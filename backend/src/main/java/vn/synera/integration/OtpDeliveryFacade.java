package vn.synera.integration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import vn.synera.common.BusinessException;

@Service
public class OtpDeliveryFacade {
  private final ObjectProvider<JavaMailSender> mail;
  private final String mode, host, from;

  public OtpDeliveryFacade(
      ObjectProvider<JavaMailSender> mail,
      @Value("${synera.integration.otp-mode}") String mode,
      @Value("${spring.mail.host:}") String host,
      @Value("${synera.integration.otp-from:}") String from,
      org.springframework.core.env.Environment environment) {
    if (mode.equals("local")
        && !environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local", "test")))
      throw new IllegalStateException("Local OTP delivery requires local/test profile");
    this.mail = mail;
    this.mode = mode;
    this.host = host;
    this.from = from;
  }

  public boolean local() {
    return mode.equals("local");
  }

  public void assertAvailable() {
    if (!local()
        && (!mode.equals("smtp")
            || host.isBlank()
            || from.isBlank()
            || mail.getIfAvailable() == null))
      throw new BusinessException(
          "INTEGRATION_NOT_CONFIGURED",
          "Chưa cấu hình nhà cung cấp OTP/email. Không có email nào được gửi.",
          503);
  }

  public void otp(String to, String code) {
    send(
        to,
        "SYNERA - Mã xác minh đăng nhập",
        "Mã xác minh: " + code + ". Hết hạn sau 5 phút. Không chia sẻ mã này.");
  }

  public void reset(String to, String token) {
    send(
        to,
        "SYNERA - Khôi phục mật khẩu",
        "Mã khôi phục một lần: "
            + token
            + ". Hết hạn sau 15 phút. Chỉ dùng trên màn hình khôi phục SYNERA.");
  }

  private void send(String to, String subject, String body) {
    assertAvailable();
    if (local()) return;
    try {
      var message = new SimpleMailMessage();
      message.setFrom(from);
      message.setTo(to);
      message.setSubject(subject);
      message.setText(body);
      mail.getObject().send(message);
    } catch (Exception e) {
      throw new BusinessException(
          "DELIVERY_UNAVAILABLE",
          "Nhà cung cấp email chưa nhận yêu cầu. Vui lòng thử lại sau.",
          503);
    }
  }
}
