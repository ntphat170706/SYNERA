package vn.synera.display;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.synera.common.*;

@Component
@ConditionalOnProperty(name = "synera.ai.mode", havingValue = "http")
public class HttpAiClient implements AiClient {
  private final URI url;
  private final String token;
  private final Jsons json;

  public HttpAiClient(
      @Value("${AI_URL}") String url, @Value("${AI_API_TOKEN}") String token, Jsons json) {
    this.url = URI.create(url);
    this.token = token;
    this.json = json;
    if (!this.url.getScheme().equals("https") || token.length() < 16)
      throw new IllegalStateException("AI HTTP adapter requires HTTPS and an API token");
  }

  public Result score(Long id, String type, byte[] bytes, Object criteria) {
    try (var client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()) {
      var request =
          HttpRequest.newBuilder(url)
              .timeout(Duration.ofSeconds(20))
              .header("Authorization", "Bearer " + token)
              .header("Content-Type", "application/json")
              .header("Idempotency-Key", "display-" + id)
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      json.write(
                          Map.of(
                              "auditId",
                              id,
                              "contentType",
                              type,
                              "imageBase64",
                              Base64.getEncoder().encodeToString(bytes),
                              "criteria",
                              criteria))))
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (var input = response.body()) {
        if (response.statusCode() == 202) return null;
        if (response.statusCode() != 200)
          throw new IllegalStateException("AI provider HTTP " + response.statusCode());
        byte[] data = input.readNBytes(1048577);
        if (data.length > 1048576) throw new IllegalStateException("AI result exceeds limit");
        var result =
            (Map<?, ?>) json.read(new String(data, java.nio.charset.StandardCharsets.UTF_8));
        return new Result(
            ((Number) result.get("score")).doubleValue(),
            ((Number) result.get("confidence")).doubleValue(),
            Objects.toString(result.get("modelVersion"), "unknown"),
            (Map<String, Object>) result.getOrDefault("findings", null));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("AI request interrupted", e);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("AI provider unavailable", e);
    }
  }

  public String provider() {
    return "HTTP_AI";
  }
}
