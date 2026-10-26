package th.taxbook.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** ประเภทเงินได้ตามมาตรา 40(1)–40(8) เป็นข้อมูลอ้างอิง (seed ใน V4) อ่านอย่างเดียว */
@Entity
@Table(name = "income_category")
public class IncomeCategory {
    @Id
    private String code;               // c1–c8

    @Column(nullable = false)
    private String section;            // 40(1)–40(8)

    @Column(nullable = false)
    private String name;

    @Column(name = "default_method", nullable = false)
    private String defaultMethod;

    protected IncomeCategory() {}

    public String getCode() { return code; }
    public String getSection() { return section; }
    public String getName() { return name; }
    public String getDefaultMethod() { return defaultMethod; }
}
