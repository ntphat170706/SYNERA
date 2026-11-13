package vn.synera.catalog.api;

import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;

/**
 * Each row has its own transaction/idempotency record. No parent transaction holds the actor lock.
 */
@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {
  private static final Map<String, List<String>> HEADERS =
      Map.of(
          "STORE",
          List.of("nppId", "repId", "name", "owner", "phone", "address", "latitude", "longitude"),
          "SKU",
          List.of("code", "name", "category", "baseUnit", "minOrder", "caseUnit", "caseFactor"),
          "STOCK",
          List.of("warehouseId", "skuId", "lotNo", "expiryDate", "qtyBase", "reason"));
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final IdempotencyService idem;
  private final Jsons json;
  private final Validator validator;
  private final vn.synera.inventory.InventoryFacade inventory;

  public record StockRow(
      @NotNull Long warehouseId,
      @NotNull Long skuId,
      @NotBlank String lotNo,
      @NotNull java.time.LocalDate expiryDate,
      @NotNull @DecimalMin("0.001") BigDecimal qtyBase,
      String reason) {
    public StockRow {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  public ImportController(
      CatalogFacade catalog,
      ActorFacade actors,
      IdempotencyService idem,
      Jsons json,
      Validator validator,
      vn.synera.inventory.InventoryFacade inventory) {
    this.catalog = catalog;
    this.actors = actors;
    this.idem = idem;
    this.json = json;
    this.validator = validator;
    this.inventory = inventory;
  }

  private List<String> headers(String kind) {
    var headers = HEADERS.get(kind);
    BusinessException.require(headers != null, "Chỉ hỗ trợ mẫu STORE, SKU hoặc STOCK.");
    actors.require(
        kind.equals("STORE")
            ? "CRM_WRITE"
            : kind.equals("STOCK") ? "WAREHOUSE_WRITE" : "CATALOG_WRITE");
    return headers;
  }

  @GetMapping("/template.xlsx")
  public ResponseEntity<byte[]> template(@RequestParam String kind) throws IOException {
    var headers = headers(kind);
    try (var book = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      var sheet = book.createSheet(kind);
      var row = sheet.createRow(0);
      var textStyle = book.createCellStyle();
      textStyle.setDataFormat(book.createDataFormat().getFormat("@"));
      for (int i = 0; i < headers.size(); i++) {
        row.createCell(i).setCellValue(headers.get(i));
        sheet.setColumnWidth(i, 6000);
        sheet.setDefaultColumnStyle(i, textStyle);
      }
      book.write(output);
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=synera-" + kind + ".xlsx")
          .contentType(
              MediaType.parseMediaType(
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .body(output.toByteArray());
    }
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<?> upload(
      @RequestParam String kind,
      @RequestParam(defaultValue = "true") boolean preview,
      @RequestParam MultipartFile file,
      @RequestHeader("Idempotency-Key") String key)
      throws IOException {
    var headers = headers(kind);
    BusinessException.require(
        key.matches("[A-Za-z0-9_.-]{8,70}")
            && file.getSize() > 0
            && file.getSize() <= 5 * 1024 * 1024,
        "Tệp tối đa 5 MB và cần khóa gửi hợp lệ.");
    byte[] bytes = file.getBytes();
    String hash = json.hash(bytes);
    var results = new ArrayList<Map<String, Object>>();
    try (var book = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
      BusinessException.require(
          book instanceof XSSFWorkbook && book.getNumberOfSheets() == 1,
          "Dùng một sheet theo mẫu XLSX.");
      var sheet = book.getSheetAt(0);
      BusinessException.require(sheet.getLastRowNum() <= 1000, "Mỗi lần tối đa 1000 dòng.");
      for (int c = 0; c < headers.size(); c++)
        BusinessException.require(
            headers.get(c).equals(value(sheet.getRow(0), c)),
            "Header không khớp mẫu tải từ hệ thống.");
      for (int index = 1; index <= sheet.getLastRowNum(); index++) {
        var row = sheet.getRow(index);
        if (row == null) continue;
        int number = index + 1;
        try {
          var values = new LinkedHashMap<String, String>();
          for (int c = 0; c < headers.size(); c++) values.put(headers.get(c), value(row, c));
          if (values.values().stream().allMatch(String::isBlank)) continue;
          Object request;
          if (kind.equals("STORE")) {
            request =
                new CatalogController.NewStore(
                    Long.valueOf(values.get("nppId")),
                    Long.valueOf(values.get("repId")),
                    values.get("name"),
                    values.get("owner"),
                    values.get("phone"),
                    values.get("address"),
                    optional(values.get("latitude")),
                    optional(values.get("longitude")));
            var s = (CatalogController.NewStore) request;
            catalog.npp(s.nppId());
            actors.assertOwnRep(s.repId());
            actors.assertRep(s.repId(), s.nppId());
            CatalogFacade.normalizePhone(s.phone());
          } else if (kind.equals("STOCK")) {
            var stock =
                new StockRow(
                    Long.valueOf(values.get("warehouseId")),
                    Long.valueOf(values.get("skuId")),
                    values.get("lotNo"),
                    java.time.LocalDate.parse(values.get("expiryDate")),
                    new BigDecimal(values.get("qtyBase")),
                    values.get("reason"));
            catalog.warehouse(stock.warehouseId());
            catalog.product(stock.skuId());
            request = stock;
          } else {
            var factors = new LinkedHashMap<String, BigDecimal>();
            factors.put(values.get("baseUnit"), BigDecimal.ONE);
            if (!values.get("caseUnit").isBlank())
              factors.put(values.get("caseUnit"), new BigDecimal(values.get("caseFactor")));
            request =
                new CatalogController.NewProduct(
                    values.get("code"),
                    values.get("name"),
                    values.get("category"),
                    values.get("baseUnit"),
                    new BigDecimal(values.get("minOrder")),
                    factors);
          }
          var violations = validator.validate(request);
          BusinessException.require(
              violations.isEmpty(),
              violations.stream()
                  .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                  .sorted()
                  .collect(java.util.stream.Collectors.joining("; ")));
          Object result =
              preview
                  ? Map.of()
                  : idem.execute(
                      key + "-" + number,
                      "import." + kind,
                      Map.of("fileHash", hash, "row", request),
                      () -> create(request));
          results.add(
              Map.of("row", number, "status", preview ? "VALID" : "SAVED", "result", result));
        } catch (BusinessException e) {
          results.add(Map.of("row", number, "status", "REJECTED", "error", e.getMessage()));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
          results.add(
              Map.of(
                  "row",
                  number,
                  "status",
                  "REJECTED",
                  "error",
                  "Dữ liệu trùng hoặc không đáp ứng ràng buộc. Kiểm tra mã, số điện thoại và độ"
                      + " dài."));
        } catch (java.time.DateTimeException e) {
          results.add(
              Map.of(
                  "row",
                  number,
                  "status",
                  "REJECTED",
                  "error",
                  "Ngày phải dùng định dạng yyyy-mm-dd."));
        } catch (IllegalArgumentException e) {
          results.add(
              Map.of(
                  "row",
                  number,
                  "status",
                  "REJECTED",
                  "error",
                  "Sai định dạng số, tọa độ hoặc ô công thức. Dùng giá trị tĩnh theo mẫu."));
        }
      }
    } catch (org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException e) {
      throw BusinessException.invalid("Tệp không phải XLSX hợp lệ.");
    } catch (java.io.IOException e) {
      throw BusinessException.invalid(
          "Không đọc được tệp XLSX. Tải lại mẫu và kiểm tra nội dung tệp.");
    }
    return ApiResponse.ok(
        Map.of(
            "preview",
            preview,
            "rows",
            results,
            "saved",
            results.stream().filter(r -> r.get("status").equals("SAVED")).count(),
            "rejected",
            results.stream().filter(r -> r.get("status").equals("REJECTED")).count()));
  }

  private Object create(Object request) {
    if (request instanceof StockRow s)
      return inventory.receipt(
          s.warehouseId(), s.skuId(), s.lotNo(), s.qtyBase(), s.expiryDate(), s.reason());
    if (request instanceof CatalogController.NewStore s)
      return catalog.createStore(
          s.nppId(),
          s.repId(),
          s.name(),
          s.owner(),
          s.phone(),
          s.address(),
          s.latitude(),
          s.longitude());
    var p = (CatalogController.NewProduct) request;
    return catalog.createProduct(
        p.code(), p.name(), p.category(), p.baseUnit(), p.minOrder(), p.factors());
  }

  private static Double optional(String value) {
    return value.isBlank() ? null : Double.valueOf(value);
  }

  private static String value(Row row, int column) {
    if (row == null) return "";
    var cell = row.getCell(column);
    if (cell == null) return "";
    if (cell.getCellType() == CellType.FORMULA)
      throw new IllegalArgumentException("Formula not allowed");
    String value = new DataFormatter(Locale.ROOT).formatCellValue(cell).trim();
    if (value.length() > 1000) throw new IllegalArgumentException("Cell too long");
    return value;
  }
}
