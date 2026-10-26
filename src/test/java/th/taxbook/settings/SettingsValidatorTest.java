package th.taxbook.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsValidatorTest {
    static Map<String, Object> p(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    static boolean rejects(Map<String, Object> patch) {
        try { SettingsValidator.validate(patch); return false; } catch (IllegalArgumentException e) { return true; }
    }

    @Test
    void acceptsAllEditableFields() {
        Map<String, Object> out = SettingsValidator.validate(p(
            "enabled", true, "monthly", false, "deadline", true, "yearend", false,
            "send_minute", 1170, "remind_day", 28,
            "monthly_when", List.of("start", "end", "start"), "deadline_days", List.of(30, 7, 0, 7)));
        assertEquals(List.of("start", "end"), out.get("monthly_when")); // ตัดค่าซ้ำ
        assertEquals(List.of(30, 7, 0), out.get("deadline_days"));
        assertEquals(1170, out.get("send_minute"));
    }

    @Test
    void acceptsEmptyListsAndBoundaries() {
        SettingsValidator.validate(p("monthly_when", List.of(), "deadline_days", List.of(), "send_minute", 0, "remind_day", 1));
        SettingsValidator.validate(p("send_minute", 1439));
    }

    @Test
    void rejectsOutOfRangeAndWrongTypes() {
        assertTrue(rejects(p("send_minute", 2000)));
        assertTrue(rejects(p("send_minute", -1)));
        assertTrue(rejects(p("send_minute", 12.5)));
        assertTrue(rejects(p("send_minute", "540")));
        assertTrue(rejects(p("remind_day", 31)));
        assertTrue(rejects(p("remind_day", 0)));
        assertTrue(rejects(p("enabled", "true")));
        assertTrue(rejects(p("monthly_when", List.of("foo"))));
        assertTrue(rejects(p("monthly_when", "start")));
        assertTrue(rejects(p("deadline_days", List.of(5))));
        assertTrue(rejects(p("deadline_days", List.of(60))));
        assertTrue(rejects(p("deadline_days", List.of("7"))));
    }

    @Test
    void rejectsFieldsUsersMustNotEdit() {
        assertTrue(rejects(p("email", "x@y.z")));
        assertTrue(rejects(p("unsub_token", "22222222-2222-2222-2222-222222222222")));
        assertTrue(rejects(p("consent_at", "2020-01-01")));
        assertTrue(rejects(p("unsubscribed_at", null)));
        assertTrue(rejects(p("user_id", "abc")));
        assertTrue(rejects(p("enabled", true, "email", "x@y.z"))); // แอบแทรกมาพร้อมค่าที่ถูกต้อง
        assertTrue(rejects(Map.of()));
        assertTrue(rejects(null));
    }

    @Test
    void rejectsHugeLists() {
        assertTrue(rejects(p("deadline_days", java.util.Collections.nCopies(21, 7))));
    }
}
