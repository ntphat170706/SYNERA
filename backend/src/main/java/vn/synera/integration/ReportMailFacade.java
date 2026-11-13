package vn.synera.integration;

import java.nio.file.Path;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Service;

@Service
public class ReportMailFacade {
  private final ObjectProvider<JavaMailSender> senders;
  private final String from;

  public ReportMailFacade(
      ObjectProvider<JavaMailSender> senders,
      @Value("${synera.integration.otp-from:}") String from) {
    this.senders = senders;
    this.from = from;
  }

  public void send(String to, Path file, String name) {
    var sender = senders.getIfAvailable();
    if (sender == null || from.isBlank()) throw new IllegalStateException("SMTP_NOT_CONFIGURED");
    try {
      var message = sender.createMimeMessage();
      var helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(from);
      helper.setTo(to);
      helper.setSubject("SYNERA - Báo cáo định kỳ");
      helper.setText(
          "Báo cáo được tạo từ phạm vi quyền hiện tại của tài khoản. File đính kèm có thời điểm tạo"
              + " và lớp dữ liệu; không dùng số tạm tính để tính thưởng.");
      helper.addAttachment(name, file.toFile());
      sender.send(message);
    } catch (jakarta.mail.MessagingException e) {
      throw new IllegalStateException("REPORT_MAIL_FAILED", e);
    }
  }
}
