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
 * รายจ่ายรายเดือน (entity ExpenseEntry)
 *   GET    /api/expenses        รายการทุกปีของผู้ใช้
 *   POST   /api/expenses        {"yearBe":2569,"month":3,"kind":"personal|income_cost","category":"c2","name":"...","amount":500,"note":""}
 *   GET    /api/expenses/{id}
 *   PUT    /api/expenses/{id}   ส่งเฉพาะฟิลด์ที่เปลี่ยน
 *   DELETE /api/expenses/{id}
 */
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {
    private final CurrentUser current;
    private final TaxDataService data;

    public ExpenseController(CurrentUser current, TaxDataService data) {
        this.current = current;
        this.data = data;
    }

    @GetMapping
    public List<Dtos.Expense> list(@AuthenticationPrincipal OidcUser p, HttpSession s) {
        return data.expenses(current.id(p, s));
    }

    @PostMapping
    public ResponseEntity<Dtos.Expense> create(@AuthenticationPrincipal OidcUser p, HttpSession s,
                                               @RequestBody Map<String, Object> body) {
        Dtos.Expense e = data.createExpense(current.id(p, s), DataValidator.expense(body, true));
        return ResponseEntity.status(HttpStatus.CREATED).body(e);
    }

    @GetMapping("/{id}")
    public Dtos.Expense get(@AuthenticationPrincipal OidcUser p, HttpSession s, @PathVariable long id) {
        return data.expense(current.id(p, s), id);
    }

    @PutMapping("/{id}")
    public Dtos.Expense update(@AuthenticationPrincipal OidcUser p, HttpSession s, @PathVariable long id,
                               @RequestBody Map<String, Object> body) {
        return data.updateExpense(current.id(p, s), id, DataValidator.expense(body, false));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal OidcUser p, HttpSession s, @PathVariable long id) {
        data.deleteExpense(current.id(p, s), id);
        return ResponseEntity.noContent().build();
    }
}