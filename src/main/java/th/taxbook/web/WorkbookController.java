package th.taxbook.web;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import th.taxbook.data.TaxDataService;

/** มุมมองรวมอ่านอย่างเดียว: โปรไฟล์ + วิธีหักที่เลือก + ปี/เดือน/รายการทั้งหมด (หน้าเว็บใช้โหลดครั้งเดียวตอนเข้าแอป) */
@RestController
@RequestMapping("/api/workbook")
public class WorkbookController {
    private final CurrentUser current;
    private final TaxDataService data;

    public WorkbookController(CurrentUser current, TaxDataService data) {
        this.current = current;
        this.data = data;
    }

    @GetMapping
    public Map<String, Object> get(@AuthenticationPrincipal OidcUser p, HttpSession s) {
        return data.workbook(current.id(p, s));
    }
}
