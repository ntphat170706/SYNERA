package vn.synera.integration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.display.*;
import vn.synera.integration.CallbackSignature;

@RestController
public class AiCallbackController {
  private final CallbackSignature signature;
  private final AiFacade ai;
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final Jsons json;

  public AiCallbackController(
      CallbackSignature signature,
      AiFacade ai,
      JdbcTemplate jdbc,
      ObjectMapper mapper,
      Jsons json) {
    this.signature = signature;
    this.ai = ai;
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.json = json;
  }

  @PostMapping("/internal/display-audits/{id}/result")
  @Transactional
  public ApiResponse<?> result(
      @PathVariable Long id,
      @RequestHeader("X-Callback-Time") String time,
      @RequestHeader("X-Callback-Event") String event,
      @RequestHeader("X-Callback-Signature") String proof,
      @RequestBody String body)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    String path = "/internal/display-audits/" + id + "/result";
    signature.verify(time, event, path, body, proof);
    jdbc.queryForList("select id from integration_callback_guard where id=1 for update");
    String hash = json.hash(Map.of("path", path, "body", body));
    var previous =
        jdbc.queryForList(
            "select * from integration_callback where provider='DISPLAY_AI' and event_id=?", event);
    if (!previous.isEmpty()) {
      if (!previous.get(0).get("request_hash").equals(hash))
        throw BusinessException.conflict(
            "IDEMPOTENCY_KEY_REUSED", "Mã callback đã dùng cho nội dung khác.");
      return ApiResponse.ok(json.read((String) previous.get(0).get("response_json")));
    }
    var result = ai.callback(id, mapper.readValue(body, AiClient.Result.class));
    jdbc.update(
        "insert into integration_callback(provider,event_id,request_hash,created_at,response_json)"
            + " values('DISPLAY_AI',?,?,?,?)",
        event,
        hash,
        Timestamp.from(Instant.now()),
        json.write(result));
    return ApiResponse.ok(result);
  }
}
