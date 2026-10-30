package th.taxbook.web;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import th.taxbook.data.Dtos;
import th.taxbook.data.TaxDataService;
import th.taxbook.validation.DataValidator;

/**
 * ค่าลดหย่อนกลุ่มประกันและดอกเบี้ย (รายปี) (entity DeductionEntry)
 *   GET    /api/deductions        รายการทุกปีของผู้ใช้
 *   POST   /api/deductions        เพิ่ม {"yearBe":2569,"kind":"life","name":"...","amount":1000,"pct":0}
 *   GET    /api/deductions/{id}   ดูรายการเดียว
 *   PUT    /api/deductions/{id}   แก้ (ส่งเฉพาะฟิลด์ที่เปลี่ยน)
 *   DELETE /api/deductions/{id}   ลบ
 */
@RestController
@RequestMapping("/api/deductions")
public class DeductionController {
    private final CurrentUser current;
    private final TaxDataService data;

    public DeductionController(CurrentUser current, TaxDataService data) {
        this.current = current;
        this.data = data;
    }

    @GetMapping
    public List<Dtos.Deduction> list(@AuthenticationPrincipal OidcUser p, HttpSession s) {
        return data.deductions(current.id(p, s));
    }

    @PostMapping
    public ResponseEntity<Dtos.Deduction> create(@AuthenticationPrincipal OidcUser p, HttpSession s,
                                                 @RequestBody Map<String, Object> body) {
        Dtos.Deduction d = data.createDeduction(current.id(p, s), DataValidator.deduction(body, true));
        return ResponseEntity.status(HttpStatus.CREATED).body(d);
    }

    @GetMapping("/{id}")
    public Dtos.Deduction get(@AuthenticationPrincipal OidcUser p, HttpSession s, @PathVariable long id) {
        return data.deduction(current.id(p, s), id);
    }

    @PutMapping("/{id}")
    public Dtos.Deduction update(@AuthenticationPrincipal OidcUser p, HttpSession s, @PathVariable long id,
                                 @RequestBody Map<String, Object> body) {
        return data.updateDeduction(current.id(p, s), id, DataValidator.deduction(body, false));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal OidcUser p, HttpSession s, @PathVariable long id) {
        data.deleteDeduction(current.id(p, s), id);
        return ResponseEntity.noContent().build();
    }
}