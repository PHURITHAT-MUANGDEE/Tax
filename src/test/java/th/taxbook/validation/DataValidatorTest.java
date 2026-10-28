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
