package vn.synera.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.synera.common.BusinessException;

@Component
public class CallbackSignature {
  private final String secret;

  public CallbackSignature(@Value("${synera.integration.callback-secret:}") String secret) {
    this.secret = secret;
  }

  public void verify(String time, String event, String path, String body, String signature) {
    if (secret.length() < 32)
      throw new BusinessException(
          "INTEGRATION_NOT_CONFIGURED", "Chưa cấu hình xác thực callback.", 503);
    try {
      long seconds = Long.parseLong(time);
      if (Math.abs(Instant.now().getEpochSecond() - seconds) > 300
          || !event.matches("[A-Za-z0-9_-]{1,100}")
          || body.length() > 1048576) throw new IllegalArgumentException();
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] expected =
          mac.doFinal(
              (time + "." + event + "." + path + "." + body).getBytes(StandardCharsets.UTF_8));
      if (!MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature)))
        throw new IllegalArgumentException();
    } catch (Exception e) {
      throw new BusinessException(
          "INVALID_CALLBACK_SIGNATURE", "Callback không hợp lệ hoặc quá hạn.", 401);
    }
  }
}
