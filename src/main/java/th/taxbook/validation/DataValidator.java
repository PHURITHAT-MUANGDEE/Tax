package th.taxbook.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * ตรวจและแปลงค่าที่ผู้ใช้ส่งมา (โปรไฟล์ รายการรายได้ ปีภาษี) ก่อนถึงฐานข้อมูล ไม่พึ่ง Spring จึงทดสอบแยกได้
 * ค่าไม่ถูกต้องโยน {@link Invalid} (ตอบ 400) กฎต้องตรงกับ check constraint ใน V4__normalize_entities.sql
 */
public final class DataValidator {
    private DataValidator() {}

    public static final class Invalid extends RuntimeException {
        public Invalid(String message) { super(message); }
    }

    public static final Pattern METHOD = Pattern.compile("^(auto|manual|none|[0-9]{1,2})$");
    public static final Pattern CATEGORY = Pattern.compile("^c[1-8]$");
    public static final int MIN_YEAR = 2500, MAX_YEAR = 2700, MAX_NAME = 200;
    static final BigDecimal MAX_MONEY = new BigDecimal("999999999999.99");

    /** ฟิลด์ที่เป็น null แปลว่าไม่ได้ส่งมา (ตอนแก้ไข = คงค่าเดิม) */
    public record EntryInput(Integer yearBe, Integer month, String category, BigDecimal amount, BigDecimal withheld,
                             String method, String name) {}

    public record ProfileInput(String nick, String marital, int kids, int kids2, int parents, int spParents, int disabled,
                               BigDecimal ss, BigDecimal life, BigDecimal health, BigDecimal phealth, BigDecimal retire,
                               BigDecimal home, BigDecimal donate, BigDecimal rentType, BigDecimal profType,
                               BigDecimal bizRate, boolean excl4, boolean project, boolean sample) {}

    private static final Set<String> ENTRY_KEYS = Set.of("yearBe", "month", "category", "amount", "withheld", "method", "name");
    private static final Set<String> PROFILE_KEYS = Set.of("nick", "marital", "kids", "kids2", "parents", "spParents", "disabled",
        "ss", "life", "health", "phealth", "retire", "home", "donate", "rentType", "profType", "bizRate", "excl4", "project", "sample");
    private static final Set<String> MARITAL = Set.of("single", "married_noinc", "married_inc");

    // ---------- ค่าเดี่ยว ----------

    public static int integer(Object v, String field, int lo, int hi) {
        long n;
        if (v instanceof Number num) {
            double d = num.doubleValue();
            if (!Double.isFinite(d) || d != Math.rint(d)) throw new Invalid("invalid_" + field);
            n = num.longValue();
        } else if (v instanceof String s && s.trim().matches("-?[0-9]{1,9}")) {
            n = Long.parseLong(s.trim());
        } else {
            throw new Invalid("invalid_" + field);
        }
        if (n < lo || n > hi) throw new Invalid("invalid_" + field);
        return (int) n;
    }

    public static BigDecimal money(Object v, String field, BigDecimal max) {
        BigDecimal b;
        try {
            if (v instanceof Number num) {
                if (num instanceof Double d && !Double.isFinite(d)) throw new Invalid("invalid_" + field);
                b = new BigDecimal(num.toString());
            } else if (v instanceof String s && !s.isBlank()) {
                b = new BigDecimal(s.trim());
            } else {
                throw new Invalid("invalid_" + field);
            }
        } catch (NumberFormatException e) {
            throw new Invalid("invalid_" + field);
        }
        if (b.signum() < 0 || b.compareTo(max) > 0) throw new Invalid("invalid_" + field);
        return b.setScale(2, RoundingMode.HALF_UP);
    }

    public static int yearBe(Object v) { return integer(v, "yearBe", MIN_YEAR, MAX_YEAR); }

    public static int month(Object v) { return integer(v, "month", 1, 12); }

    public static String category(Object v) {
        if (!(v instanceof String s) || !CATEGORY.matcher(s).matches()) throw new Invalid("invalid_category");
        return s;
    }

    /** วิธีหักภาษีสำหรับ withhold_pref (ห้ามว่าง) */
    public static String method(Object v) {
        if (!(v instanceof String s) || !METHOD.matcher(s).matches()) throw new Invalid("invalid_method");
        return s;
    }

    // ---------- รายการรายได้ ----------

    /** create = true: ต้องมี yearBe, month, category และ name (ชื่อรายการ ห้ามว่าง) ส่วน amount/withheld/method ไม่ส่ง = ค่าว่าง */
    public static EntryInput entry(Map<String, Object> body, boolean create) {
        if (body == null) throw new Invalid("invalid_body");
        for (String k : body.keySet()) if (!ENTRY_KEYS.contains(k)) throw new Invalid("unknown_field_" + k);
        if (create) for (String k : new String[]{"yearBe", "month", "category"})
            if (body.get(k) == null) throw new Invalid("missing_" + k);

        Integer year = body.get("yearBe") == null ? null : yearBe(body.get("yearBe"));
        Integer month = body.get("month") == null ? null : month(body.get("month"));
        String cat = body.get("category") == null ? null : category(body.get("category"));
        BigDecimal amount = body.get("amount") == null ? (create ? BigDecimal.ZERO.setScale(2) : null) : money(body.get("amount"), "amount", MAX_MONEY);
        BigDecimal withheld = body.get("withheld") == null ? (create ? BigDecimal.ZERO.setScale(2) : null) : money(body.get("withheld"), "withheld", MAX_MONEY);
        String method = null;
        if (body.get("method") != null) {
            if (!(body.get("method") instanceof String m) || !(m.isEmpty() || METHOD.matcher(m).matches())) throw new Invalid("invalid_method");
            method = m;
        } else if (create) method = "";
        String name = null;   // ชื่อรายการบังคับ: สร้างใหม่ต้องมี และถ้าส่งมาแก้ก็ห้ามว่าง
        if (body.get("name") != null) {
            if (!(body.get("name") instanceof String n) || n.length() > MAX_NAME) throw new Invalid("invalid_name");
            if (n.isBlank()) throw new Invalid("name_required");
            name = n.strip();
        } else if (create) throw new Invalid("name_required");
        return new EntryInput(year, month, cat, amount, withheld, method, name);
    }

    // ---------- โปรไฟล์ ----------

    /** แทนที่ทั้งชุด: ฟิลด์ที่ไม่ส่งมาใช้ค่าเริ่มต้น */
    public static ProfileInput profile(Map<String, Object> body) {
        if (body == null) throw new Invalid("invalid_body");
        for (String k : body.keySet()) if (!PROFILE_KEYS.contains(k)) throw new Invalid("unknown_field_" + k);
        String nick = "";
        if (body.get("nick") != null) {
            if (!(body.get("nick") instanceof String n) || n.length() > 80) throw new Invalid("invalid_nick");
            nick = n;
        }
        String marital = "single";
        if (body.get("marital") != null) {
            if (!(body.get("marital") instanceof String m) || !MARITAL.contains(m)) throw new Invalid("invalid_marital");
            marital = m;
        }
        BigDecimal hundred = new BigDecimal("100");
        return new ProfileInput(nick, marital,
            count(body, "kids", 50), count(body, "kids2", 50), count(body, "parents", 2), count(body, "spParents", 2), count(body, "disabled", 50),
            amount(body, "ss"), amount(body, "life"), amount(body, "health"), amount(body, "phealth"), amount(body, "retire"),
            amount(body, "home"), amount(body, "donate"),
            percent(body, "rentType", 30, hundred), percent(body, "profType", 30, hundred), percent(body, "bizRate", 60, hundred),
            flag(body, "excl4", false), flag(body, "project", true), flag(body, "sample", false));
    }

    private static int count(Map<String, Object> b, String k, int hi) { return b.get(k) == null ? 0 : integer(b.get(k), k, 0, hi); }

    private static BigDecimal amount(Map<String, Object> b, String k) {
        return b.get(k) == null ? BigDecimal.ZERO.setScale(2) : money(b.get(k), k, MAX_MONEY);
    }

    private static BigDecimal percent(Map<String, Object> b, String k, int dflt, BigDecimal max) {
        return b.get(k) == null ? new BigDecimal(dflt).setScale(2) : money(b.get(k), k, max);
    }

    private static boolean flag(Map<String, Object> b, String k, boolean dflt) {
        if (b.get(k) == null) return dflt;
        if (!(b.get(k) instanceof Boolean v)) throw new Invalid("invalid_" + k);
        return v;
    }
}
