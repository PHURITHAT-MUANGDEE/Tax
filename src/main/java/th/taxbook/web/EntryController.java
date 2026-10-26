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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import th.taxbook.data.Dtos;
import th.taxbook.data.TaxDataService;
import th.taxbook.validation.DataValidator;
import th.taxbook.validation.DataValidator.Invalid;

/**
 * รายการรายได้ (entity IncomeEntry)
 *   GET    /api/entries?year=&month=&category=&q=   ดู/ค้นหา (ไม่ส่งเงื่อนไข = ทุกรายการของผู้ใช้)
 *   POST   /api/entries                             สร้าง {yearBe, month, category, amount, withheld, method, name}
 *   GET    /api/entries/{id}                        ดูรายการเดียว
 *   PUT    /api/entries/{id}                        แก้ (ส่งเฉพาะฟิลด์ที่เปลี่ยน)
 *   DELETE /api/entries/{id}                        ลบ
 */
@RestController
@RequestMapping("/api/entries")
public class EntryController {
    private final CurrentUser current;
    private final TaxDataService data;

    public EntryController(CurrentUser current, TaxDataService data) {
        this.current = current;
        this.data = data;
    }

    static String query(String q) {
        if (q != null && q.length() > 100) throw new Invalid("invalid_q");
        return q;
    }

    @GetMapping
    public List<Dtos.Entry> search(@AuthenticationPrincipal OidcUser p, HttpSession s,
                                   @RequestParam(required = false) Integer year,
                                   @RequestParam(required = false) Integer month,
                                   @RequestParam(required = false) String category,
                                   @RequestParam(required = false) String q) {
        return data.search(current.id(p, s),
            year == null ? null : DataValidator.yearBe(year),
            month == null ? null : DataValidator.month(month),
            category == null ? null : DataValidator.category(category), query(q));
    }

    @PostMapping
    public ResponseEntity<Dtos.Entry> create(@AuthenticationPrincipal OidcUser p, HttpSession s,
                                             @RequestBody Map<String, Object> body) {
        Dtos.Entry e = data.createEntry(current.id(p, s), DataValidator.entry(body, true));
        return ResponseEntity.status(HttpStatus.CREATED).body(e);
    }

    @GetMapping("/{id}")
    public Dtos.Entry get(@AuthenticationPrincipal OidcUser p, HttpSession s, @PathVariable long id) {
        return data.entry(current.id(p, s), id);
    }

    @PutMapping("/{id}")
    public Dtos.Entry update(@AuthenticationPrincipal OidcUser p, HttpSession s, @PathVariable long id,
                             @RequestBody Map<String, Object> body) {
        return data.updateEntry(current.id(p, s), id, DataValidator.entry(body, false));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal OidcUser p, HttpSession s, @PathVariable long id) {
        data.deleteEntry(current.id(p, s), id);
        return ResponseEntity.noContent().build();
    }
}
