package vn.synera.display;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
@ConditionalOnProperty(name = "synera.ai.mode", havingValue = "mock")
public class MockAiClient implements AiClient {
  public Result score(Long id, String type, byte[] bytes, Object criteria) {
    return new Result(
        75,
        0,
        "local-fixture-v1",
        Map.of(
            "simulation",
            true,
            "note",
            "Kết quả mô phỏng để kiểm thử kết nối; bắt buộc người dùng duyệt."));
  }

  public String provider() {
    return "MOCK_LOCAL_NOT_AI";
  }
}
