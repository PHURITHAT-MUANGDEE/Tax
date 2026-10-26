package th.taxbook.web;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import th.taxbook.data.Dtos;
import th.taxbook.data.TaxDataService;
import th.taxbook.validation.DataValidator;

/** โปรไฟล์ผู้เสียภาษี (entity TaxProfile): ดูและแก้ไข (แทนที่ทั้งชุด ฟิลด์ที่ไม่ส่งใช้ค่าเริ่มต้น) */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final CurrentUser current;
    private final TaxDataService data;

    public ProfileController(CurrentUser current, TaxDataService data) {
        this.current = current;
        this.data = data;
    }

    @GetMapping
    public Dtos.Profile get(@AuthenticationPrincipal OidcUser p, HttpSession s) {
        return data.profile(current.id(p, s));
    }

    @PutMapping
    public Dtos.Profile put(@AuthenticationPrincipal OidcUser p, HttpSession s, @RequestBody Map<String, Object> body) {
        return data.saveProfile(current.id(p, s), DataValidator.profile(body));
    }
}
