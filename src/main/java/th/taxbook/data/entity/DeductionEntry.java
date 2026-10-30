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
 * รายการค่าลดหย่อนกลุ่ม "ประกันและดอกเบี้ย (รายปี)" ของปีภาษีหนึ่งปี ผู้ใช้เพิ่มได้หลายรายการ
 * kind: ss ประกันสังคม, life ประกันชีวิต, health ประกันสุขภาพ, phealth ประกันสุขภาพบิดามารดา,
 *       home ดอกเบี้ยเงินกู้ซื้อบ้าน, fund หุ้นและกองทุน
 * withheldPct = % ภาษีที่ถูกหักที่ผู้ใช้กรอกเอง (ยอด x % นำไปรวมเป็นเครดิตภาษี)
 */
@Entity
@Table(name = "deduction_entry")
public class DeductionEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_year_id", nullable = false)
    private TaxYear taxYear;

    @Column(nullable = false) private String kind;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private BigDecimal amount = BigDecimal.ZERO;
    @Column(name = "withheld_pct", nullable = false) private BigDecimal withheldPct = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    protected DeductionEntry() {}

    public DeductionEntry(TaxYear taxYear, String kind, String name, BigDecimal amount, BigDecimal withheldPct) {
        this.taxYear = taxYear;
        this.kind = kind;
        this.name = name;
        this.amount = amount;
        this.withheldPct = withheldPct;
    }

    @PrePersist @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public void setTaxYear(TaxYear taxYear) { this.taxYear = taxYear; }
    public void setKind(String kind) { this.kind = kind; }
    public void setName(String name) { this.name = name; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setWithheldPct(BigDecimal withheldPct) { this.withheldPct = withheldPct; }

    public Long getId() { return id; }
    public TaxYear getTaxYear() { return taxYear; }
    public String getKind() { return kind; }
    public String getName() { return name; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getWithheldPct() { return withheldPct; }
}