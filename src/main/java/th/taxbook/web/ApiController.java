package th.taxbook.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import th.taxbook.data.TaxDataService;
import th.taxbook.job.Mailer;
import th.taxbook.reminder.MailTemplates;
import th.taxbook.reminder.ReminderLogic;
import th.taxbook.repo.Repos;
import th.taxbook.repo.SettingsRow;
import th.taxbook.settings.SettingsValidator;

@RestController
@RequestMapping("/api")
public class ApiController {
    private static final Pattern UUID_RE = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /** เปลี่ยนค่านี้เมื่อแก้ข้อกำหนดหรือนโยบายสาระสำคัญ ผู้ใช้ทุกคนจะถูกขอให้ยอมรับใหม่ */
    static final String TOS_VERSION = "2026-10-24";

    private final Repos repos;
    private final CurrentUser current;
    private final ObjectMapper mapper;
    private final Mailer mailer;
    private final TaxDataService data;
    private final String appUrl;

    public ApiController(Repos repos, CurrentUser current, ObjectMapper mapper, Mailer mailer, TaxDataService data,
                         @Value("${app.base-url}") String appUrl) {
        this.repos = repos;
        this.current = current;
        this.mapper = mapper;
        this.mailer = mailer;
        this.data = data;
        this.appUrl = appUrl.replaceAll("/+$", "");
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser p, HttpSession s) {
        UUID id = current.id(p, s); // สร้างแถวผู้ใช้ครั้งแรก
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("email", p.getEmail() == null ? "" : p.getEmail());
        out.put("tos_current", TOS_VERSION);
        out.put("tos_version", repos.tosVersion(id).orElse(null)); // null = ยังไม่เคยยอมรับ
        return out;
    }

    /** ผู้ใช้ติ๊ก "ฉันทราบนโยบายและยอมรับข้อกำหนด" ต้องส่งเวอร์ชันที่ตรงกับฉบับปัจจุบันของเซิร์ฟเวอร์ */
    @PostMapping("/consent/tos")
    public ResponseEntity<Void> acceptTos(@AuthenticationPrincipal OidcUser p, HttpSession s,
                                          @RequestBody Map<String, Object> body) {
        UUID id = current.id(p, s);
        if (!TOS_VERSION.equals(body.get("version"))) return ResponseEntity.status(HttpStatus.CONFLICT).build();
        repos.acceptTos(id, TOS_VERSION);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings(@AuthenticationPrincipal OidcUser p, HttpSession s) {
        UUID id = current.id(p, s);
        return repos.getSettings(id).map(r -> ResponseEntity.ok(r.toApi())).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/settings")
    public ResponseEntity<?> patchSettings(@AuthenticationPrincipal OidcUser p, HttpSession s,
                                           @RequestBody Map<String, Object> body) {
        UUID id = current.id(p, s);
        Map<String, Object> valid;
        try {
            valid = SettingsValidator.validate(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return repos.updateSettings(id, valid)
            .<ResponseEntity<?>>map(r -> ResponseEntity.ok(r.toApi()))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * ส่งอีเมลตัวอย่างถึงอีเมลของผู้ใช้เอง เพื่อตรวจว่าการส่งผ่าน Gmail ใช้งานได้ ส่งได้นาทีละครั้งต่อเซสชัน
     * 204 = ส่งแล้ว, 429 = ถี่เกินไป, 503 = เซิร์ฟเวอร์ยังไม่ได้ตั้ง MAIL_FROM, 502 = ผู้ให้บริการอีเมลปฏิเสธ
     */
    @PostMapping("/notifications/test")
    public ResponseEntity<Map<String, Object>> testMail(@AuthenticationPrincipal OidcUser p, HttpSession s) {
        UUID id = current.id(p, s);
        if (!mailer.configured()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "mail_not_configured"));
        long now = System.currentTimeMillis();
        if (s.getAttribute("taxbook.testmail") instanceof Long last && now - last < 60_000)
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", "too_many"));
        SettingsRow row = repos.getSettings(id).orElse(null);
        if (row == null || row.email().isBlank()) return ResponseEntity.notFound().build();
        s.setAttribute("taxbook.testmail", now);
        try {
            int month = ReminderLogic.bangkokNow(Instant.ofEpochMilli(now)).m();
            MailTemplates.Mail m = MailTemplates.compose(new ReminderLogic.Due(ReminderLogic.Kind.MONTHLY_CUSTOM, "test", Map.of("month", month)));
            MailTemplates.Mail mail = new MailTemplates.Mail("[ทดสอบ] " + m.subject(), m.heading(), m.paragraphs(), m.cta());
            String token = row.unsubToken().toString();
            MailTemplates.Rendered r = MailTemplates.render(mail, appUrl, appUrl + "/unsubscribe.html?t=" + token);
            mailer.send(row.email(), mail.subject(), r.html(), r.text(), appUrl + "/api/unsubscribe?t=" + token);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "send_failed"));
        }
    }

    /** ดาวน์โหลดข้อมูลทั้งหมดของตัวเอง (ไม่รวมโทเคนยกเลิกรับ) */
    @GetMapping("/export")
    public Map<String, Object> export(@AuthenticationPrincipal OidcUser p, HttpSession s) {
        UUID id = current.id(p, s);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exported_at", Instant.now().toString());
        out.put("email", p.getEmail());
        out.put("data", data.workbook(id));
        out.put("notification_settings", repos.getSettings(id).map(SettingsRow::toApi).orElse(null));
        return out;
    }

    /** ลบบัญชีและข้อมูลทั้งหมดของผู้ใช้ที่ล็อกอินอยู่ */
    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal OidcUser p, HttpSession s) {
        UUID id = current.id(p, s);
        repos.deleteUser(id);
        SecurityContextHolder.clearContext();
        s.invalidate();
        return ResponseEntity.noContent().build();
    }

    /**
     * ยกเลิกรับอีเมลด้วยโทเคนในลิงก์ ไม่ต้องล็อกอิน รับ POST เท่านั้น โทเคนส่งได้ 2 ทาง
     *  - ?t=... (One-Click ของ Gmail ซึ่งส่งเนื้อหาเป็นฟอร์ม List-Unsubscribe=One-Click)
     *  - JSON {"token": "..."} (ปุ่มในหน้า unsubscribe.html)
     */
    @PostMapping("/unsubscribe")
    public ResponseEntity<Map<String, Object>> unsubscribe(HttpServletRequest req) throws IOException {
        String token = req.getParameter("t");
        if ((token == null || token.isEmpty()) && req.getContentType() != null
                && req.getContentType().toLowerCase().startsWith("application/json") && req.getContentLength() < 4096) {
            Object bt = mapper.readValue(req.getInputStream(), Map.class).get("token");
            if (bt instanceof String str) token = str;
        }
        if (token == null || !UUID_RE.matcher(token).matches()) return ResponseEntity.badRequest().body(Map.of("error", "invalid_token"));
        int n = repos.unsubscribe(UUID.fromString(token));
        if (n == 0) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
