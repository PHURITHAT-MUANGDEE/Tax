package th.taxbook.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * รายจ่ายรายเดือน kind: personal = ค่าใช้จ่ายส่วนตัว, income_cost = ค่าใช้จ่ายในการหารายได้
 * (ผูกกับประเภทเงินได้ได้ผ่าน categoryCode) รายจ่ายไม่ถูกนำไปคำนวณภาษี ใช้เปรียบเทียบกับค่าใช้จ่ายเหมาเท่านั้น
 */
@Entity
@Table(name = "expense_entry")
public class ExpenseEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_year_id", nullable = false)
    private TaxYear taxYear;

    @Column(nullable = false) private short month;
    @Column(nullable = false) private String kind;
    @Column(name = "category_code") private String categoryCode;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private BigDecimal amount = BigDecimal.ZERO;
    @Column(nullable = false) private String note = "";

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    protected ExpenseEntry() {}

    public ExpenseEntry(TaxYear taxYear, int month, String kind, String categoryCode, String name, BigDecimal amount, String note) {
        this.taxYear = taxYear;
        this.month = (short) month;
        this.kind = kind;
        this.categoryCode = categoryCode;
        this.name = name;
        this.amount = amount;
        this.note = note;
    }

    @PrePersist @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public void setTaxYear(TaxYear taxYear) { this.taxYear = taxYear; }
    public void setMonth(int month) { this.month = (short) month; }
    public void setKind(String kind) { this.kind = kind; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public void setName(String name) { this.name = name; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setNote(String note) { this.note = note; }

    public Long getId() { return id; }
    public TaxYear getTaxYear() { return taxYear; }
    public int getMonth() { return month; }
    public String getKind() { return kind; }
    public String getCategoryCode() { return categoryCode; }
    public String getName() { return name; }
    public BigDecimal getAmount() { return amount; }
    public String getNote() { return note; }
}