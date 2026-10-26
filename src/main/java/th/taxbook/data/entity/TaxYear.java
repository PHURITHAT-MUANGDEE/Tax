package th.taxbook.data.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** ปีภาษี (พ.ศ.) ของผู้ใช้ ผู้ใช้หนึ่งคนมีได้หลายปี (ปีซ้ำไม่ได้) และแต่ละปีมีรายการรายได้หลายรายการ */
@Entity
@Table(name = "tax_year")
public class TaxYear {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "year_be", nullable = false, updatable = false)
    private Integer yearBe;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "taxYear", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IncomeEntry> entries = new ArrayList<>();

    protected TaxYear() {}

    public TaxYear(User user, int yearBe) {
        this.user = user;
        this.yearBe = yearBe;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public int getYearBe() { return yearBe; }
    public Instant getCreatedAt() { return createdAt; }
    public List<IncomeEntry> getEntries() { return entries; }
}
