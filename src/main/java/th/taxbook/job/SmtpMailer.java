package th.taxbook.job;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * ส่งผ่าน SMTP (Gmail: smtp.gmail.com:587 ต้องเปิดการยืนยัน 2 ขั้นตอนแล้วสร้างรหัสผ่านแอป)
 * Gmail จำกัดประมาณ 500 ฉบับต่อวันสำหรับบัญชีทั่วไป
 */
@Component
public class SmtpMailer implements Mailer {
    private final JavaMailSender sender;
    private final String from;
    private final String fromName;

    public SmtpMailer(JavaMailSender sender,
                      @Value("${app.mail-from:}") String from,
                      @Value("${app.mail-from-name:สมุดภาษี}") String fromName) {
        this.sender = sender;
        this.from = from;
        this.fromName = fromName;
    }

    @Override
    public boolean configured() { return from != null && !from.isBlank(); }

    @Override
    public void send(String to, String subject, String html, String text, String unsubPostUrl) throws Exception {
        MimeMessage msg = sender.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
        h.setFrom(from, fromName);
        h.setTo(to);
        h.setSubject(subject);
        h.setText(text, html); // multipart/alternative: ข้อความล้วนก่อน แล้วตามด้วย HTML
        msg.addHeader("List-Unsubscribe", "<" + unsubPostUrl + ">");
        msg.addHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        sender.send(msg);
    }
}
