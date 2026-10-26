package th.taxbook.job;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import th.taxbook.reminder.ReminderLogic;

/** ทุก 30 นาที (นาทีที่ 0 และ 30) ตรวจว่าผู้ใช้คนไหนถึงเวลาที่ตั้งไว้ แล้วส่งอีเมล */
@Component
@ConditionalOnProperty(name = "app.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderJob {
    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);
    private final ReminderService service;
    private final Mailer mailer;

    public ReminderJob(ReminderService service, Mailer mailer) {
        this.service = service;
        this.mailer = mailer;
    }

    @Scheduled(cron = "0 0/30 * * * *", zone = "Asia/Bangkok")
    public void tick() {
        if (!mailer.configured()) return; // ยังไม่ได้ตั้ง MAIL_FROM
        try {
            var r = service.run(ReminderLogic.bangkokNow(Instant.now()), false, null);
            if (!Integer.valueOf(0).equals(r.get("planned"))) log.info("reminders: {}", r);
        } catch (Exception e) {
            log.error("reminders failed", e);
        }
    }
}
