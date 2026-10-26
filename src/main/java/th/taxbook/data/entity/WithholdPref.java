package th.taxbook.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** วิธีหักภาษีที่ผู้ใช้เลือกล่าสุดของแต่ละประเภทเงินได้ (ใช้เป็นค่าตั้งต้นของรายการใหม่) */
@Entity
@Table(name = "withhold_pref")
public class WithholdPref {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_code", nullable = false, updatable = false)
    private IncomeCategory category;

    @Column(nullable = false)
    private String method;

    protected WithholdPref() {}

    public WithholdPref(User user, IncomeCategory category, String method) {
        this.user = user;
        this.category = category;
        this.method = method;
    }

    public void setMethod(String method) { this.method = method; }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public IncomeCategory getCategory() { return category; }
    public String getMethod() { return method; }
}
