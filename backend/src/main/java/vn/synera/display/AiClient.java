package vn.synera.display;

import java.util.Map;

public interface AiClient {
  record Result(
      double score, double confidence, String modelVersion, Map<String, Object> findings) {}

  /** Null means accepted by an asynchronous provider; a signed callback must follow. */
  Result score(Long auditId, String contentType, byte[] image, Object criteria);

  String provider();
}
