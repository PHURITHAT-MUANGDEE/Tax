package th.taxbook.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import th.taxbook.validation.DataValidator.ProfileInput;

/** โปรไฟล์ผู้เสียภาษี หนึ่งแถวต่อผู้ใช้ (ความสัมพันธ์ 1:1 กับ User ใช้คีย์เดียวกัน) */
@Entity
@Table(name = "tax_profile")
public class TaxProfile {
    @Id
    @Column(name = "user_id")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(nullable = false) private String nick = "";
    @Column(nullable = false) private String marital = "single";
    @Column(nullable = false) private Integer kids = 0;
    @Column(nullable = false) private Integer kids2 = 0;
    @Column(nullable = false) private Integer parents = 0;
    @Column(name = "sp_parents", nullable = false) private Integer spParents = 0;
    @Column(nullable = false) private Integer disabled = 0;
    @Column(nullable = false) private BigDecimal donate = BigDecimal.ZERO;
    @Column(name = "rent_type", nullable = false) private BigDecimal rentType = new BigDecimal("30.00");
    @Column(name = "prof_type", nullable = false) private BigDecimal profType = new BigDecimal("30.00");
    @Column(name = "biz_rate", nullable = false) private BigDecimal bizRate = new BigDecimal("60.00");
    @Column(nullable = false) private boolean excl4 = false;
    @Column(name = "project_remaining", nullable = false) private boolean projectRemaining = true;
    @Column(name = "is_sample", nullable = false) private boolean sample = false;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    protected TaxProfile() {}

    public TaxProfile(UUID userId, User userRef) {
        this.id = userId;
        this.user = userRef;
    }

    /** แทนที่ค่าทั้งหมดด้วยค่าที่ผ่านการตรวจแล้ว */
    public void apply(ProfileInput in) {
        nick = in.nick(); marital = in.marital();
        kids = in.kids(); kids2 = in.kids2(); parents = in.parents(); spParents = in.spParents(); disabled = in.disabled();
        donate = in.donate();
        rentType = in.rentType(); profType = in.profType(); bizRate = in.bizRate();
        excl4 = in.excl4(); projectRemaining = in.project(); sample = in.sample();
    }

    @PrePersist @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getNick() { return nick; }
    public String getMarital() { return marital; }
    public int getKids() { return kids; }
    public int getKids2() { return kids2; }
    public int getParents() { return parents; }
    public int getSpParents() { return spParents; }
    public int getDisabled() { return disabled; }
    public BigDecimal getDonate() { return donate; }
    public BigDecimal getRentType() { return rentType; }
    public BigDecimal getProfType() { return profType; }
    public BigDecimal getBizRate() { return bizRate; }
    public boolean isExcl4() { return excl4; }
    public boolean isProjectRemaining() { return projectRemaining; }
    public boolean isSample() { return sample; }
    public Instant getUpdatedAt() { return updatedAt; }
}