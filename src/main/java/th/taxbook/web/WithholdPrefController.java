package th.taxbook.web;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import th.taxbook.data.TaxDataService;
import th.taxbook.validation.DataValidator;

/** วิธีหักภาษีที่เลือกล่าสุดของแต่ละประเภท (entity WithholdPref) GET ทั้งชุด / PUT และ DELETE ต่อประเภท */
@RestController
@RequestMapping("/api/withhold-prefs")
public class WithholdPrefController {
    private final CurrentUser current;
    private final TaxDataService data;

    public WithholdPrefController(CurrentUser current, TaxDataService data) {
        this.current = current;
        this.data = data;
    }

    @GetMapping
    public Map<String, String> list(@AuthenticationPrincipal OidcUser p, HttpSession s) {
        return data.prefs(current.id(p, s));
    }

    @PutMapping("/{category}")
    public Map<String, String> put(@AuthenticationPrincipal OidcUser p, HttpSession s, @PathVariable String category,
                                   @RequestBody Map<String, Object> body) {
        return data.savePref(current.id(p, s), DataValidator.category(category), DataValidator.method(body.get("method")));
    }

    @DeleteMapping("/{category}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal OidcUser p, HttpSession s, @PathVariable String category) {
        data.deletePref(current.id(p, s), DataValidator.category(category));
        return ResponseEntity.noContent().build();
    }
}
