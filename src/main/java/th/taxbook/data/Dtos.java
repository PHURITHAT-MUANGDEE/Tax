package th.taxbook.data;

import java.math.BigDecimal;
import th.taxbook.data.entity.IncomeCategory;
import th.taxbook.data.entity.IncomeEntry;
import th.taxbook.data.entity.TaxProfile;

/** รูปแบบข้อมูลที่ส่งออกทาง API (ไม่ส่งตัว entity ออกไปโดยตรง) */
public final class Dtos {
    private Dtos() {}

    public record Category(String code, String section, String name, String defaultMethod) {
        static Category of(IncomeCategory c) { return new Category(c.getCode(), c.getSection(), c.getName(), c.getDefaultMethod()); }
    }

    public record Profile(String nick, String marital, int kids, int kids2, int parents, int spParents, int disabled,
                          BigDecimal ss, BigDecimal life, BigDecimal health, BigDecimal phealth, BigDecimal retire,
                          BigDecimal home, BigDecimal donate, BigDecimal rentType, BigDecimal profType, BigDecimal bizRate,
                          boolean excl4, boolean project, boolean sample) {
        static Profile of(TaxProfile p) {
            return new Profile(p.getNick(), p.getMarital(), p.getKids(), p.getKids2(), p.getParents(), p.getSpParents(), p.getDisabled(),
                p.getSs(), p.getLife(), p.getHealth(), p.getPhealth(), p.getRetire(), p.getHome(), p.getDonate(),
                p.getRentType(), p.getProfType(), p.getBizRate(), p.isExcl4(), p.isProjectRemaining(), p.isSample());
        }

        /** ผู้ใช้ที่ยังไม่เคยบันทึกโปรไฟล์ */
        static Profile defaults() {
            BigDecimal z = new BigDecimal("0.00");
            return new Profile("", "single", 0, 0, 0, 0, 0, z, z, z, z, z, z, z,
                new BigDecimal("30.00"), new BigDecimal("30.00"), new BigDecimal("60.00"), false, true, false);
        }
    }

    public record Entry(Long id, int yearBe, int month, String category, BigDecimal amount, BigDecimal withheld,
                        String method, String name) {
        static Entry of(IncomeEntry e) {
            return new Entry(e.getId(), e.getTaxYear().getYearBe(), e.getMonth(), e.getCategory().getCode(),
                e.getAmount(), e.getWithheld(), e.getMethod(), e.getName());
        }
    }

    public record Year(int yearBe, long entries, BigDecimal totalAmount, BigDecimal totalWithheld) {}

    public record MonthSummary(int month, String category, BigDecimal amount, BigDecimal withheld, long entries) {}

    public record YearSummary(int yearBe, long entries, BigDecimal totalAmount, BigDecimal totalWithheld,
                              java.util.List<MonthSummary> byMonthAndCategory) {}
}
