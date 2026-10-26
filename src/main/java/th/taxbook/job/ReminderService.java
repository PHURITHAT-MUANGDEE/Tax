package th.taxbook.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import th.taxbook.reminder.MailTemplates;
import th.taxbook.reminder.ReminderLogic;
import th.taxbook.reminder.ReminderLogic.Due;
import th.taxbook.reminder.ReminderLogic.Now;
import th.taxbook.repo.Repos;
import th.taxbook.repo.SettingsRow;

/** หาว่าใครถึงเวลาได้รับอีเมลอะไร จองสิทธิ์ใน notification_log กันส่งซ้ำ แล้วส่ง */
@Service
public class ReminderService {
    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final Repos repos;
    private final Mailer mailer;
    private final ObjectMapper mapper;
    private final String appUrl;
    private final int maxPerRun;
    private final long pauseMs;

    public ReminderService(Repos repos, Mailer mailer, ObjectMapper mapper,
                           @Value("${app.base-url}") String appUrl,
                           @Value("${app.reminders.max-per-run:150}") int maxPerRun,
                           @Value("${app.reminders.pause-ms:300}") long pauseMs) {
        this.repos = repos;
        this.mailer = mailer;
        this.mapper = mapper;
        this.appUrl = appUrl.replaceAll("/+$", "");
        this.maxPerRun = maxPerRun;
        this.pauseMs = pauseMs;
    }

    private record Planned(SettingsRow row, Due due) {}

    /** dry = true แสดงว่าจะส่งอะไรให้ใคร โดยไม่ส่งจริงและไม่บันทึก log */
    public Map<String, Object> run(Now now, boolean dry, String onlyEmail) {
        if (!dry && !mailer.configured()) throw new IllegalStateException("ยังไม่ได้ตั้ง MAIL_FROM จึงส่งอีเมลจริงไม่ได้");
        List<SettingsRow> users = repos.listEnabled(onlyEmail);

        // โหลดข้อมูลรายได้เป็นชุด เฉพาะคนที่เปิดเตือนบันทึกรายได้ (ใช้ตรวจว่าเดือนนั้นบันทึกแล้วหรือยัง)
        List<UUID> need = users.stream().filter(SettingsRow::monthly).map(SettingsRow::userId).toList();
        Map<UUID, Object> states = new java.util.HashMap<>();
        for (int i = 0; i < need.size(); i += 200) states.putAll(repos.incomeFor(need.subList(i, Math.min(need.size(), i + 200))));

        List<Planned> planned = new ArrayList<>();
        for (SettingsRow row : users)
            for (Due d : ReminderLogic.dueFor(row.toLogic(), states.get(row.userId()), now)) planned.add(new Planned(row, d));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("now", Map.of("y", now.y(), "m", now.m(), "d", now.d(), "minutes", now.minutes()));
        result.put("users", users.size());

        if (dry) {
            List<Map<String, String>> would = new ArrayList<>();
            for (Planned p : planned) would.add(Map.of("email", p.row().email(), "kind", kind(p.due()), "period", p.due().period()));
            result.put("dry", true);
            result.put("wouldSend", would);
            return result;
        }

        int sent = 0, skipped = 0;
        List<String> failed = new ArrayList<>();
        for (Planned p : planned.subList(0, Math.min(planned.size(), maxPerRun))) {
            UUID uid = p.row().userId();
            String kind = kind(p.due());
            if (!repos.reserve(uid, kind, p.due().period())) { skipped++; continue; } // เคยส่งรอบนี้แล้ว
            try {
                MailTemplates.Mail mail = MailTemplates.compose(p.due());
                String token = p.row().unsubToken().toString();
                MailTemplates.Rendered r = MailTemplates.render(mail, appUrl, appUrl + "/unsubscribe.html?t=" + token);
                mailer.send(p.row().email(), mail.subject(), r.html(), r.text(), appUrl + "/api/unsubscribe?t=" + token);
                sent++;
            } catch (Exception e) {
                repos.release(uid, kind, p.due().period()); // ส่งไม่สำเร็จ ลบ log เพื่อให้รอบถัดไปลองใหม่
                failed.add(uid + ": " + e.getMessage());
                log.warn("ส่งอีเมลไม่สำเร็จ user={} kind={}: {}", uid, kind, e.toString());
            }
            if (pauseMs > 0) {
                try { Thread.sleep(pauseMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        result.put("planned", planned.size());
        result.put("sent", sent);
        result.put("skipped", skipped);
        result.put("failed", failed);
        return result;
    }

    static String kind(Due d) { return d.kind().name().toLowerCase(); }
}
