package vn.synera.policy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;
import vn.synera.integration.StorageFacade;

@Service
@Transactional
public class PolicyDocumentFacade {
  private final JdbcTemplate jdbc;
  private final SqlRecords sql;
  private final ActorFacade actors;
  private final CatalogFacade catalog;
  private final StorageFacade storage;
  private final AuditService audit;

  public PolicyDocumentFacade(
      JdbcTemplate jdbc,
      SqlRecords sql,
      ActorFacade actors,
      CatalogFacade catalog,
      StorageFacade storage,
      AuditService audit) {
    this.jdbc = jdbc;
    this.sql = sql;
    this.actors = actors;
    this.catalog = catalog;
    this.storage = storage;
    this.audit = audit;
  }

  private Map<String, Object> scoped(Long id, boolean lock) {
    var doc = sql.row("policy_document", id, lock);
    if (doc.get("npp_id") != null) catalog.npp(SqlRecords.id(doc, "npp_id"));
    if (!actors.has("POLICY_WRITE") && !doc.get("status").equals("PUBLISHED"))
      throw BusinessException.missing();
    return doc;
  }

  public List<Map<String, Object>> list() {
    var result =
        new ArrayList<Map<String, Object>>(
            jdbc.queryForList("select * from policy_document where npp_id is null"));
    for (Long npp : catalog.visibleNppIds())
      result.addAll(jdbc.queryForList("select * from policy_document where npp_id=?", npp));
    return result.stream()
        .filter(d -> actors.has("POLICY_WRITE") || d.get("status").equals("PUBLISHED"))
        .peek(
            d ->
                d.put(
                    "acknowledged",
                    jdbc.queryForObject(
                            "select count(*) from policy_ack where document_id=? and user_id=?",
                            Integer.class,
                            d.get("id"),
                            actors.current().id())
                        > 0))
        .toList();
  }

  public Object create(Long npp, String title, String body, Long attachment) {
    actors.require("POLICY_WRITE");
    if (npp != null) catalog.npp(npp);
    else
      BusinessException.require(
          actors.current().nsx() && actors.current().region() == null,
          "Chính sách toàn hệ thống cần phạm vi toàn quốc.");
    BusinessException.require(
        title != null
            && !title.isBlank()
            && title.length() <= 255
            && body != null
            && !body.isBlank()
            && body.length() <= 100000,
        "Tiêu đề hoặc nội dung không hợp lệ.");
    if (attachment != null) {
      BusinessException.require(npp != null, "Tài liệu đính kèm cần phạm vi NPP.");
      storage.assertReady(attachment, npp);
    }
    long id =
        sql.insert(
            "policy_document",
            SqlRecords.versioned(
                "npp_id",
                npp,
                "title",
                title,
                "body",
                body,
                "attachment_id",
                attachment,
                "status",
                "DRAFT"));
    audit.add(actors.current().id(), "PolicyDocument", id, "CREATE", Map.of("title", title));
    return scoped(id, false);
  }

  public Object publish(Long id, String reason) {
    actors.require("POLICY_WRITE");
    var doc = scoped(id, true);
    BusinessException.require(
        doc.get("status").equals("DRAFT"), "Tài liệu đã phát hành hoặc thiếu lý do.");
    if (doc.get("npp_id") == null)
      BusinessException.require(
          actors.current().nsx() && actors.current().region() == null,
          "Phạm vi phát hành không hợp lệ.");
    var now = Timestamp.from(Instant.now());
    jdbc.update(
        "update policy_document set"
            + " status='PUBLISHED',published_at=?,version=version+1,updated_at=? where id=?",
        now,
        now,
        id);
    audit.add(actors.current().id(), "PolicyDocument", id, "PUBLISH", Map.of("reason", reason));
    return scoped(id, false);
  }

  public Object acknowledge(Long id) {
    var doc = scoped(id, true);
    BusinessException.require(doc.get("status").equals("PUBLISHED"), "Tài liệu chưa phát hành.");
    if (jdbc.queryForObject(
            "select count(*) from policy_ack where document_id=? and user_id=?",
            Integer.class,
            id,
            actors.current().id())
        == 0)
      jdbc.update(
          "insert into policy_ack(document_id,user_id,acknowledged_at) values(?,?,?)",
          id,
          actors.current().id(),
          Timestamp.from(Instant.now()));
    return Map.of("documentId", id, "acknowledged", true);
  }
}
