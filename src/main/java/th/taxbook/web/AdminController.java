package th.taxbook.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import th.taxbook.job.ReminderService;
import th.taxbook.reminder.ReminderLogic;
import th.taxbook.reminder.ReminderLogic.Now;

/**
 * ปลายทางสำหรับผู้ดูแลระบบ ใช้ทดสอบการส่งอีเมลด้วยมือ ปิดอยู่ถ้าไม่ได้ตั้ง ADMIN_TOKEN
 *
 *   curl -X POST "$APP_URL/admin/reminders/run" -H "X-Admin-Token: $ADMIN_TOKEN" \
 *     -H "Content-Type: application/json" -d '{"dry": true, "today": "2027-03-24", "time": "09:30"}'
 *
 * ตัวเลือก: dry (ค่าเริ่มต้น true, ไม่ส่งจริง) / today "YYYY-MM-DD" และ time "HH:MM" จำลองเวลา (ใช้กับ dry เท่านั้น)
 *           / only_email จำกัดเฉพาะผู้ใช้คนนี้  ถ้าจะส่งจริงต้องใส่ "dry": false
 */
@RestController
public class AdminController {
    private final ReminderService service;
    private final byte[] token;

    public AdminController(ReminderService service, @Value("${app.admin-token:}") String adminToken) {
        this.service = service;
        this.token = adminToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/admin/reminders/run")
    public ResponseEntity<Map<String, Object>> run(@RequestHeader(value = "X-Admin-Token", required = false) String given,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        if (token.length == 0) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        if (given == null || !MessageDigest.isEqual(token, given.getBytes(StandardCharsets.UTF_8)))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Map<String, Object> b = body == null ? Map.of() : body;
        boolean dry = !Boolean.FALSE.equals(b.get("dry"));
        String only = b.get("only_email") instanceof String s && !s.isBlank() ? s : null;

        Now now = ReminderLogic.bangkokNow(Instant.now());
        if (dry && b.get("today") instanceof String t && t.matches("\\d{4}-\\d{2}-\\d{2}")) {
            String[] p = t.split("-");
            try {
                java.time.LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                now = new Now(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), now.minutes());
            } catch (java.time.DateTimeException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "today ไม่ใช่วันที่จริง"));
            }
        }
        if (dry && b.get("time") instanceof String t && t.matches("\\d{2}:\\d{2}")) {
            int h = Integer.parseInt(t.substring(0, 2)), m = Integer.parseInt(t.substring(3));
            if (h > 23 || m > 59) return ResponseEntity.badRequest().body(Map.of("error", "time ไม่ถูกต้อง"));
            now = new Now(now.y(), now.m(), now.d(), h * 60 + m);
        }
        return ResponseEntity.ok(service.run(now, dry, only));
    }
}
