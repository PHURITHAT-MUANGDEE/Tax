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

/** รายการรายได้หนึ่งรายการ: ปีภาษี + เดือน + ประเภท 40(x) ผู้ใช้เพิ่มได้หลายรายการต่อเดือนต่อประเภท */
@Entity
@Table(name = "income_entry")
public class IncomeEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_year_id", nullable = false)
    private TaxYear taxYear;

    @Column(nullable = false)
    private Integer month;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_code", nullable = false)
    private IncomeCategory category;

    @Column(nullable = false) private BigDecimal amount = BigDecimal.ZERO;
    @Column(nullable = false) private BigDecimal withheld = BigDecimal.ZERO;   // ภาษีที่ถูกหักแล้ว (กรณีระบุเอง)
    @Column(nullable = false) private String method = "";                       // auto | manual | none | อัตรา %
    @Column(nullable = false) private String name = "";

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    protected IncomeEntry() {}

    public IncomeEntry(TaxYear taxYear, int month, IncomeCategory category, BigDecimal amount, BigDecimal withheld,
                       String method, String name) {
        this.taxYear = taxYear;
        this.month = month;
        this.category = category;
        this.amount = amount;
        this.withheld = withheld;
        this.method = method;
        this.name = name;
    }

    @PrePersist @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public void setTaxYear(TaxYear taxYear) { this.taxYear = taxYear; }
    public void setMonth(int month) { this.month = month; }
    public void setCategory(IncomeCategory category) { this.category = category; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setWithheld(BigDecimal withheld) { this.withheld = withheld; }
    public void setMethod(String method) { this.method = method; }
    public void setName(String name) { this.name = name; }

    public Long getId() { return id; }
    public TaxYear getTaxYear() { return taxYear; }
    public int getMonth() { return month; }
    public IncomeCategory getCategory() { return category; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getWithheld() { return withheld; }
    public String getMethod() { return method; }
    public String getName() { return name; }
}
