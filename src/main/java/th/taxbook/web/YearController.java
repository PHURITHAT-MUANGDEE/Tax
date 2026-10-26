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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import th.taxbook.data.Dtos;
import th.taxbook.data.TaxDataService;
import th.taxbook.validation.DataValidator;

/**
 * ปีภาษี (entity TaxYear) และการจัดการรายการรายได้ที่อยู่ใต้ปีนั้น (entity ที่เกี่ยวข้อง)
 *   GET    /api/years                        รายชื่อปี พร้อมจำนวนรายการและยอดรวม
 *   POST   /api/years                        สร้างปี {"yearBe": 2569}
 *   DELETE /api/years/{yearBe}               ลบปีพร้อมรายการทั้งหมดในปีนั้น
 *   GET    /api/years/{yearBe}/summary       สรุปรายเดือนแยกประเภท
 *   GET    /api/years/{yearBe}/entries       รายการของปี (กรอง month, category, q ได้)
 *   DELETE /api/years/{yearBe}/entries       ล้างรายการของปี (หรือเฉพาะ ?month=)
 */
@RestController
@RequestMapping("/api/years")
public class YearController {
    private final CurrentUser current;
    private final TaxDataService data;

    public YearController(CurrentUser current, TaxDataService data) {
        this.current = current;
        this.data = data;
    }

    @GetMapping
    public List<Dtos.Year> list(@AuthenticationPrincipal OidcUser p, HttpSession s) {
        return data.listYears(current.id(p, s));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@AuthenticationPrincipal OidcUser p, HttpSession s,
                                                       @RequestBody Map<String, Object> body) {
        int year = DataValidator.yearBe(body.get("yearBe"));
        Map<String, Object> out = data.createYear(current.id(p, s), year);
        return ResponseEntity.status(Boolean.TRUE.equals(out.get("created")) ? HttpStatus.CREATED : HttpStatus.OK).body(out);
    }

    @DeleteMapping("/{yearBe}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal OidcUser p, HttpSession s, @PathVariable int yearBe) {
        data.deleteYear(current.id(p, s), DataValidator.yearBe(yearBe));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{yearBe}/summary")
    public Dtos.YearSummary summary(@AuthenticationPrincipal OidcUser p, HttpSession s, @PathVariable int yearBe) {
        return data.yearSummary(current.id(p, s), DataValidator.yearBe(yearBe));
    }

    @GetMapping("/{yearBe}/entries")
    public List<Dtos.Entry> entries(@AuthenticationPrincipal OidcUser p, HttpSession s, @PathVariable int yearBe,
                                    @RequestParam(required = false) Integer month,
                                    @RequestParam(required = false) String category,
                                    @RequestParam(required = false) String q) {
        return data.search(current.id(p, s), DataValidator.yearBe(yearBe),
            month == null ? null : DataValidator.month(month),
            category == null ? null : DataValidator.category(category), EntryController.query(q));
    }

    @DeleteMapping("/{yearBe}/entries")
    public Map<String, Object> clear(@AuthenticationPrincipal OidcUser p, HttpSession s, @PathVariable int yearBe,
                                     @RequestParam(required = false) Integer month) {
        int n = data.clearEntries(current.id(p, s), DataValidator.yearBe(yearBe), month == null ? null : DataValidator.month(month));
        return Map.of("deleted", n);
    }
}
