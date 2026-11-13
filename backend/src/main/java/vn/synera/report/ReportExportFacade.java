package vn.synera.report;

import java.io.*;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;
import vn.synera.integration.ReportMailFacade;

@Service
public class ReportExportFacade {
  private final JdbcTemplate jdbc;
  private final SqlRecords sql;
  private final ActorFacade actors;
  private final CatalogFacade catalog;
  private final Jsons json;
  private final AuditService audit;
  private final ReportMailFacade mail;
  private final String fontPath;
  private final Path root = Path.of(".data/exports").toAbsolutePath().normalize();

  public ReportExportFacade(
      JdbcTemplate jdbc,
      SqlRecords sql,
      ActorFacade actors,
      CatalogFacade catalog,
      Jsons json,
      AuditService audit,
      ReportMailFacade mail,
      @Value("${REPORT_FONT_PATH:}") String fontPath) {
    this.jdbc = jdbc;
    this.sql = sql;
    this.actors = actors;
    this.catalog = catalog;
    this.json = json;
    this.audit = audit;
    this.mail = mail;
    this.fontPath = fontPath;
  }

  @Transactional
  public Object create(String type, String format, LocalDate from, LocalDate to, String email) {
    actors.require("REPORT_READ");
    BusinessException.require(
        Set.of("SELL_IN", "SELL_OUT").contains(type)
            && Set.of("XLSX", "PDF").contains(format)
            && from != null
            && to != null
            && !to.isBefore(from)
            && java.time.temporal.ChronoUnit.DAYS.between(from, to) <= 366,
        "Loại báo cáo, định dạng hoặc khoảng ngày không hợp lệ (tối đa 366 ngày).");
    BusinessException.require(
        !actors.current().sales() || type.equals("SELL_OUT"), "Sales chỉ xuất đơn bán cá nhân.");
    if (email != null)
      BusinessException.require(
          email.equalsIgnoreCase(actors.email()),
          "Báo cáo định kỳ chỉ gửi đến email của tài khoản.");
    long id =
        sql.insert(
            "report_export",
            SqlRecords.mutable(
                "user_id",
                actors.current().id(),
                "npp_id",
                actors.current().nppId(),
                "workspace_key",
                actors.scopeKey(),
                "report_type",
                type,
                "format",
                format,
                "status",
                "PENDING",
                "filters_json",
                json.write(Map.of("from", from.toString(), "to", to.toString())),
                "email",
                email,
                "available_at",
                Timestamp.from(Instant.now()),
                "created_at",
                Timestamp.from(Instant.now())));
    audit.add(
        actors.current().id(),
        "ReportExport",
        id,
        "REQUEST",
        Map.of("type", type, "format", format, "from", from, "to", to));
    return Map.of("id", id, "status", "PENDING");
  }

  public Object list() {
    actors.require("REPORT_READ");
    return jdbc.queryForList(
        "select"
            + " id,report_type,format,status,attempts,error_message,created_at,finished_at,expires_at"
            + " from report_export where user_id=? and workspace_key=? order by id desc limit 100",
        actors.current().id(),
        actors.scopeKey());
  }

  public record Download(Path path, String filename, String contentType) {}

  public Download download(Long id) {
    actors.require("REPORT_READ");
    var job = sql.row("report_export", id, false);
    if (SqlRecords.id(job, "user_id") != actors.current().id()
        || !Objects.equals(job.get("workspace_key"), actors.scopeKey()))
      throw BusinessException.missing();
    BusinessException.require(
        job.get("status").equals("DONE")
            && ((Timestamp) job.get("expires_at")).toInstant().isAfter(Instant.now()),
        "Báo cáo chưa sẵn sàng hoặc đã hết hạn.");
    Path path = root.resolve((String) job.get("object_key")).normalize();
    BusinessException.require(
        path.startsWith(root) && Files.isRegularFile(path), "Không tìm thấy file báo cáo.");
    audit.add(actors.current().id(), "ReportExport", id, "DOWNLOAD", Map.of());
    return new Download(
        path,
        "synera-" + id + "." + job.get("format").toString().toLowerCase(Locale.ROOT),
        job.get("format").equals("PDF")
            ? "application/pdf"
            : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
  }

  private List<Map<String, Object>> page(
      Map<String, Object> job, LocalDate from, LocalDate to, long last) {
    var ids = catalog.visibleNppIds();
    if (ids.isEmpty()) return List.of();
    boolean sellout = job.get("report_type").equals("SELL_OUT");
    String table = sellout ? "sales_order" : "sell_in_order";
    String scope = String.join(",", Collections.nCopies(ids.size(), "?"));
    var args = new ArrayList<Object>();
    args.add(last);
    args.addAll(ids);
    args.add(Timestamp.from(Dates.start(from)));
    args.add(Timestamp.from(Dates.start(to.plusDays(1))));
    boolean own = actors.current().sales();
    if (own) args.add(actors.current().id());
    return jdbc.queryForList(
        "select id,code,npp_id,status,total,created_at from "
            + table
            + " where id>? and npp_id in ("
            + scope
            + ") and created_at>=? and created_at<?"
            + (own ? " and rep_id=?" : "")
            + " order by id limit 500",
        args.toArray());
  }

  public void generate(Long id) throws IOException {
    var job = sql.row("report_export", id, false);
    actors.require("REPORT_READ");
    if (SqlRecords.id(job, "user_id") != actors.current().id()
        || !Objects.equals(job.get("workspace_key"), actors.scopeKey()))
      throw BusinessException.missing();
    var filters = (Map<?, ?>) json.read((String) job.get("filters_json"));
    LocalDate from = LocalDate.parse((String) filters.get("from")),
        to = LocalDate.parse((String) filters.get("to"));
    Files.createDirectories(root);
    String object = UUID.randomUUID() + "." + job.get("format").toString().toLowerCase(Locale.ROOT);
    Path output = root.resolve(object);
    Path partial = root.resolve(object + ".part");
    try {
      if (job.get("format").equals("XLSX")) excel(job, from, to, partial);
      else pdf(job, from, to, partial);
      Files.move(partial, output, StandardCopyOption.ATOMIC_MOVE);
      if (job.get("email") != null)
        mail.send(
            (String) job.get("email"),
            output,
            "synera-report." + job.get("format").toString().toLowerCase(Locale.ROOT));
      jdbc.update(
          "update report_export set"
              + " status='DONE',object_key=?,error_message=null,finished_at=?,expires_at=? where"
              + " id=?",
          object,
          Timestamp.from(Instant.now()),
          Timestamp.from(Instant.now().plusSeconds(86400)),
          id);
      audit.add(
          actors.current().id(),
          "ReportExport",
          id,
          "GENERATED",
          Map.of("format", job.get("format")));
    } catch (Exception e) {
      Files.deleteIfExists(partial);
      Files.deleteIfExists(output);
      if (e instanceof IOException io) throw io;
      throw new IOException("Report generation failed", e);
    }
  }

  private void excel(Map<String, Object> job, LocalDate from, LocalDate to, Path output)
      throws IOException {
    try (var book = new SXSSFWorkbook(100);
        var stream = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
      var sheet = book.createSheet("SYNERA");
      int index = 0;
      var meta = sheet.createRow(index++);
      meta.createCell(0)
          .setCellValue("PROVISIONAL - " + Instant.now() + " | " + from + " -> " + to);
      String[] columns = {"Code", "NPP", "Status", "Amount VND (exact)", "Created UTC"};
      var header = sheet.createRow(index++);
      for (int c = 0; c < columns.length; c++) header.createCell(c).setCellValue(columns[c]);
      long last = 0;
      while (true) {
        var rows = page(job, from, to, last);
        if (rows.isEmpty()) break;
        for (var item : rows) {
          if (index >= 1_000_000)
            throw new IOException("XLSX row limit exceeded; use a shorter period");
          var row = sheet.createRow(index++);
          String[] keys = {"code", "npp_id", "status", "total", "created_at"};
          for (int c = 0; c < keys.length; c++)
            row.createCell(c).setCellValue(Objects.toString(item.get(keys[c]), ""));
          last = SqlRecords.id(item, "id");
        }
      }
      book.write(stream);
      book.dispose();
    }
  }

  private void pdf(Map<String, Object> job, LocalDate from, LocalDate to, Path output)
      throws IOException {
    String configured =
        fontPath.isBlank()
            ? (System.getProperty("os.name").startsWith("Windows")
                ? "C:/Windows/Fonts/arial.ttf"
                : "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
            : fontPath;
    try (var document = new PDDocument();
        var fontInput = Files.newInputStream(Path.of(configured))) {
      var font = PDType0Font.load(document, fontInput);
      long last = 0;
      int count = 0;
      PDPageContentStream content = null;
      try {
        var lines = new ArrayList<String>();
        lines.add("SYNERA - Báo cáo tạm tính | " + Instant.now());
        lines.add(from + " → " + to + " | " + job.get("report_type"));
        while (true) {
          var rows = page(job, from, to, last);
          for (var row : rows) {
            lines.add(
                row.get("code")
                    + " | NPP "
                    + row.get("npp_id")
                    + " | "
                    + row.get("status")
                    + " | "
                    + row.get("total")
                    + " VND");
            last = SqlRecords.id(row, "id");
          }
          for (String line : lines) {
            if (count % 46 == 0) {
              if (content != null) {
                content.endText();
                content.close();
              }
              var page = new PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
              document.addPage(page);
              content = new PDPageContentStream(document, page);
              content.beginText();
              content.setFont(font, 9);
              content.setLeading(15);
              content.newLineAtOffset(35, 800);
            }
            content.showText(
                line.replaceAll("[\\p{Cntrl}]", " ").substring(0, Math.min(100, line.length())));
            content.newLine();
            count++;
          }
          lines.clear();
          if (rows.isEmpty()) break;
        }
      } finally {
        if (content != null) {
          content.endText();
          content.close();
        }
      }
      document.save(output.toFile());
    }
  }

  public Object schedules() {
    return jdbc.queryForList(
        "select * from report_schedule where user_id=? and workspace_key=? order by id desc",
        actors.current().id(),
        actors.scopeKey());
  }

  @Transactional
  public void fireSchedule(Long id) {
    var schedule = sql.row("report_schedule", id, true);
    if (!Boolean.TRUE.equals(schedule.get("active"))
        || ((Timestamp) schedule.get("next_run")).toInstant().isAfter(Instant.now())) return;
    if (SqlRecords.id(schedule, "user_id") != actors.current().id()
        || !Objects.equals(schedule.get("workspace_key"), actors.scopeKey()))
      throw BusinessException.missing();
    LocalDate end = Dates.today().minusDays(1);
    String cadence = (String) schedule.get("cadence");
    create(
        (String) schedule.get("report_type"),
        "XLSX",
        cadence.equals("DAILY")
            ? end
            : cadence.equals("WEEKLY") ? end.minusDays(6) : end.minusMonths(1).plusDays(1),
        end,
        (String) schedule.get("email"));
    var next = Dates.today().atTime(6, 0).atZone(Dates.VN);
    next =
        switch (cadence) {
          case "DAILY" -> next.plusDays(1);
          case "WEEKLY" -> next.plusWeeks(1);
          default -> next.plusMonths(1);
        };
    jdbc.update(
        "update report_schedule set last_run=?,next_run=?,last_error=null where id=?",
        Timestamp.from(Instant.now()),
        Timestamp.from(next.toInstant()),
        id);
  }

  public int cleanupExpired() throws IOException {
    int cleaned = 0;
    for (var job :
        jdbc.queryForList(
            "select id,object_key from report_export where status='DONE' and expires_at<=? order by"
                + " id limit 1000",
            Timestamp.from(Instant.now()))) {
      Path path = root.resolve((String) job.get("object_key")).normalize();
      if (!path.startsWith(root) || path.equals(root))
        throw new IOException("Invalid export storage key");
      Files.deleteIfExists(path);
      cleaned +=
          jdbc.update(
              "update report_export set status='EXPIRED',object_key=null where id=? and"
                  + " status='DONE'",
              job.get("id"));
    }
    return cleaned;
  }

  @Transactional
  public Object schedule(String type, String cadence) {
    actors.require("REPORT_READ");
    BusinessException.require(
        Set.of("SELL_IN", "SELL_OUT").contains(type)
            && Set.of("DAILY", "WEEKLY", "MONTHLY").contains(cadence)
            && (!actors.current().sales() || type.equals("SELL_OUT")),
        "Lịch báo cáo không hợp lệ.");
    long id =
        sql.insert(
            "report_schedule",
            SqlRecords.mutable(
                "user_id",
                actors.current().id(),
                "npp_id",
                actors.current().nppId(),
                "workspace_key",
                actors.scopeKey(),
                "email",
                actors.email(),
                "report_type",
                type,
                "cadence",
                cadence,
                "next_run",
                Timestamp.from(Instant.now().plusSeconds(60)),
                "created_at",
                Timestamp.from(Instant.now())));
    return Map.of("id", id);
  }

  @Transactional
  public Object cancelSchedule(Long id) {
    var s = sql.row("report_schedule", id, true);
    if (SqlRecords.id(s, "user_id") != actors.current().id()
        || !Objects.equals(s.get("workspace_key"), actors.scopeKey()))
      throw BusinessException.missing();
    jdbc.update("update report_schedule set active=false where id=?", id);
    return Map.of("id", id, "active", false);
  }
}
