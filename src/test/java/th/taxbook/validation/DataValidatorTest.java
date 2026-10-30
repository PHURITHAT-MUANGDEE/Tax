package th.taxbook.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import th.taxbook.validation.DataValidator.Invalid;

class DataValidatorTest {
    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) out.put((String) kv[i], kv[i + 1]);
        return out;
    }

    @Test
    void createEntryAcceptsValidAndDefaultsTheRest() {
        var e = DataValidator.entry(m("yearBe", 2569, "month", 10, "category", "c1", "amount", 50000, "name", " เงินเดือน "), true);
        assertEquals(2569, e.yearBe());
        assertEquals(10, e.month());
        assertEquals("c1", e.category());
        assertEquals(new BigDecimal("50000.00"), e.amount());
        assertEquals(new BigDecimal("0.00"), e.withheld());
        assertEquals("", e.method());
        assertEquals("เงินเดือน", e.name());   // ตัดช่องว่างหัวท้าย
    }

    @Test
    void entryNameIsRequired() {
        assertThrows(Invalid.class, () -> DataValidator.entry(m("yearBe", 2569, "month", 1, "category", "c1", "amount", 100), true));
        assertThrows(Invalid.class, () -> DataValidator.entry(m("yearBe", 2569, "month", 1, "category", "c1", "name", "   "), true));
        assertThrows(Invalid.class, () -> DataValidator.entry(m("name", ""), false));
        assertEquals("ฟรีแลนซ์", DataValidator.entry(m("name", "ฟรีแลนซ์"), false).name());
    }

    @Test
    void createEntryRequiresKeys() {
        assertThrows(Invalid.class, () -> DataValidator.entry(m("month", 1, "category", "c1"), true));
        assertThrows(Invalid.class, () -> DataValidator.entry(m("yearBe", 2569, "category", "c1"), true));
        assertThrows(Invalid.class, () -> DataValidator.entry(m("yearBe", 2569, "month", 1), true));
    }

    @Test
    void updateEntryIsPartial() {
        var e = DataValidator.entry(m("amount", "1200.555"), false);
        assertEquals(new BigDecimal("1200.56"), e.amount());
        assertEquals(null, e.month());
        assertEquals(null, e.name());
    }

    @Test
    void rejectsBadEntryValues() {
        assertThrows(Invalid.class, () -> DataValidator.entry(m("yearBe", 1999, "month", 1, "category", "c1"), true));
        assertThrows(Invalid.class, () -> DataValidator.entry(m("yearBe", 2569, "month", 13, "category", "c1"), true));
        assertThrows(Invalid.class, () -> DataValidator.entry(m("yearBe", 2569, "month", 1, "category", "c9"), true));
        assertThrows(Invalid.class, () -> DataValidator.entry(m("yearBe", 2569, "month", 1, "category", "c1", "amount", -1), true));
        assertThrows(Invalid.class, () -> DataValidator.entry(m("yearBe", 2569, "month", 1, "category", "c1", "amount", "abc"), true));
        assertThrows(Invalid.class, () -> DataValidator.entry(m("yearBe", 2569, "month", 1, "category", "c1", "amount", 1e15), true));
        assertThrows(Invalid.class, () -> DataValidator.entry(m("yearBe", 2569, "month", 1, "category", "c1", "method", "drop table"), true));
        assertThrows(Invalid.class, () -> DataValidator.entry(m("yearBe", 2569, "month", 1.5, "category", "c1"), true));
        assertThrows(Invalid.class, () -> DataValidator.entry(m("yearBe", 2569, "month", 1, "category", "c1", "name", "x".repeat(201)), true));
        assertThrows(Invalid.class, () -> DataValidator.entry(m("yearBe", 2569, "month", 1, "category", "c1", "userId", "x"), true));
    }

    @Test
    void deductionCreateAcceptsValidAndDefaultsTheRest() {
        var d = DataValidator.deduction(m("yearBe", 2569, "kind", "fund", "name", " หุ้น A ", "amount", 20000, "pct", 10), true);
        assertEquals(2569, d.yearBe());
        assertEquals("fund", d.kind());
        assertEquals("หุ้น A", d.name());
        assertEquals(new BigDecimal("20000.00"), d.amount());
        assertEquals(new BigDecimal("10.00"), d.pct());
        var z = DataValidator.deduction(m("yearBe", 2569, "kind", "life", "name", "ประกันชีวิต"), true);
        assertEquals(new BigDecimal("0.00"), z.amount());
        assertEquals(new BigDecimal("0.00"), z.pct());
    }

    @Test
    void deductionRequiresYearKindAndName() {
        assertThrows(Invalid.class, () -> DataValidator.deduction(m("kind", "life", "name", "x"), true));
        assertThrows(Invalid.class, () -> DataValidator.deduction(m("yearBe", 2569, "name", "x"), true));
        assertThrows(Invalid.class, () -> DataValidator.deduction(m("yearBe", 2569, "kind", "life"), true));
        assertThrows(Invalid.class, () -> DataValidator.deduction(m("yearBe", 2569, "kind", "life", "name", "   "), true));
        assertThrows(Invalid.class, () -> DataValidator.deduction(m("name", ""), false));
    }

    @Test
    void deductionRejectsBadValues() {
        assertThrows(Invalid.class, () -> DataValidator.deduction(m("yearBe", 2569, "kind", "gold", "name", "x"), true));
        assertThrows(Invalid.class, () -> DataValidator.deduction(m("yearBe", 2569, "kind", "life", "name", "x", "amount", -1), true));
        assertThrows(Invalid.class, () -> DataValidator.deduction(m("yearBe", 2569, "kind", "life", "name", "x", "pct", 101), true));
        assertThrows(Invalid.class, () -> DataValidator.deduction(m("yearBe", 2569, "kind", "life", "name", "x", "pct", -0.5), true));
        assertThrows(Invalid.class, () -> DataValidator.deduction(m("yearBe", 1999, "kind", "life", "name", "x"), true));
        assertThrows(Invalid.class, () -> DataValidator.deduction(m("yearBe", 2569, "kind", "life", "name", "x", "userId", "u"), true));
        assertThrows(Invalid.class, () -> DataValidator.deduction(m("yearBe", 2569, "kind", "life", "name", "x".repeat(201)), true));
    }

    @Test
    void deductionUpdateIsPartial() {
        var d = DataValidator.deduction(m("pct", "12.5"), false);
        assertEquals(new BigDecimal("12.50"), d.pct());
        assertEquals(null, d.kind());
        assertEquals(null, d.name());
        assertEquals(null, d.amount());
    }

    @Test
    void methodsAllowedForEntries() {
        for (String ok : new String[]{"auto", "manual", "none", "3", "15", ""})
            assertEquals(ok, DataValidator.entry(m("yearBe", 2569, "month", 1, "category", "c1", "method", ok, "name", "x"), true).method());
        assertThrows(Invalid.class, () -> DataValidator.method(""));      // withhold_pref ห้ามว่าง
        assertEquals("10", DataValidator.method("10"));
    }

    @Test
    void profileDefaultsAndRanges() {
        var p = DataValidator.profile(m());
        assertEquals("single", p.marital());
        assertEquals(new BigDecimal("60.00"), p.bizRate());
        assertEquals(new BigDecimal("30.00"), p.rentType());
        assertEquals(true, p.project());
        var q = DataValidator.profile(m("nick", "ต้น", "marital", "married_noinc", "kids", 2, "parents", 2, "ss", 9000, "excl4", true));
        assertEquals(2, q.kids());
        assertEquals(new BigDecimal("9000.00"), q.ss());
        assertEquals(true, q.excl4());
    }

    @Test
    void rejectsBadProfile() {
        assertThrows(Invalid.class, () -> DataValidator.profile(m("marital", "divorced")));
        assertThrows(Invalid.class, () -> DataValidator.profile(m("parents", 3)));
        assertThrows(Invalid.class, () -> DataValidator.profile(m("kids", -1)));
        assertThrows(Invalid.class, () -> DataValidator.profile(m("bizRate", 101)));
        assertThrows(Invalid.class, () -> DataValidator.profile(m("excl4", "yes")));
        assertThrows(Invalid.class, () -> DataValidator.profile(m("isAdmin", true)));
        assertThrows(Invalid.class, () -> DataValidator.profile(m("nick", "x".repeat(81))));
    }
}