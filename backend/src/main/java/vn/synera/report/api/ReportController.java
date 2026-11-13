package vn.synera.report.api;

import java.io.*;
import java.util.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;
import vn.synera.report.ReportFacade;
import vn.synera.sellin.SellInFacade;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAuthority('REPORT_READ')")
public class ReportController {
  private final ReportFacade reports;
  private final SellInFacade orders;
  private final AuditService audit;
  private final ActorFacade actors;

  public ReportController(
      ReportFacade reports, SellInFacade orders, AuditService audit, ActorFacade actors) {
    this.reports = reports;
    this.orders = orders;
    this.audit = audit;
    this.actors = actors;
  }

  @GetMapping("/dashboard")
  public ApiResponse<?> dashboard() {
    return ApiResponse.ok(reports.dashboard());
  }

  @GetMapping("/comparison")
  @PreAuthorize("hasAuthority('REPORT_READ') and !hasRole('SALES')")
  public ApiResponse<?> comparison(@RequestParam Long periodId) {
    return ApiResponse.ok(reports.comparison(periodId));
  }

  @GetMapping("/orders.xlsx")
  public ResponseEntity<byte[]> export() throws IOException {
    var rows = orders.list(null);
    BusinessException.require(
        !actors.current().sales(), "Sales dùng báo cáo cá nhân, không xuất đơn sell-in.");
    BusinessException.require(rows.size() <= 10000, "Trên 10.000 dòng cần job export bất đồng bộ.");
    try (var workbook = new SXSSFWorkbook(100);
        var output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("Sell-in");
      var header = sheet.createRow(0);
      String[] labels = {"Mã đơn", "NPP", "Trạng thái", "Tổng tiền"};
      for (int i = 0; i < labels.length; i++) header.createCell(i).setCellValue(labels[i]);
      int index = 1;
      for (var o : rows) {
        var row = sheet.createRow(index++);
        row.createCell(0).setCellValue(o.get("code").toString());
        row.createCell(1).setCellValue(o.get("nppId").toString());
        row.createCell(2).setCellValue(o.get("status").toString());
        row.createCell(3).setCellValue(((Number) o.get("total")).doubleValue());
      }
      workbook.write(output);
      workbook.dispose();
      audit.add(actors.current().id(), "Report", null, "EXPORT_XLSX", Map.of("rows", rows.size()));
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=synera-orders.xlsx")
          .contentType(
              MediaType.parseMediaType(
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .body(output.toByteArray());
    }
  }
}
