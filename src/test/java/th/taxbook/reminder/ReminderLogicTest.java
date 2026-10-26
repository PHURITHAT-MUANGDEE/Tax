package th.taxbook.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import th.taxbook.reminder.ReminderLogic.Due;
import th.taxbook.reminder.ReminderLogic.Kind;
import th.taxbook.reminder.ReminderLogic.Now;
import th.taxbook.reminder.ReminderLogic.Settings;

class ReminderLogicTest {
    // ค่าเริ่มต้น: เตือนตามวันเอง 25, deadline 14/7/1, ปลายปีเปิด, ส่ง 09:00
    record S(boolean monthly, List<String> when, int day, boolean deadline, List<Integer> days, boolean yearend, int send) {
        S() { this(true, List.of("custom"), 25, true, List.of(14, 7, 1), true, 540); }
        S monthly(boolean v) { return new S(v, when, day, deadline, days, yearend, send); }
        S wh(String... w) { return new S(monthly, List.of(w), day, deadline, days, yearend, send); }
        S day(int v) { return new S(monthly, when, v, deadline, days, yearend, send); }
        S deadline(boolean v) { return new S(monthly, when, day, v, days, yearend, send); }
        S dy(Integer... v) { return new S(monthly, when, day, deadline, List.of(v), yearend, send); }
        S yearend(boolean v) { return new S(monthly, when, day, deadline, days, v, send); }
        S send(int v) { return new S(monthly, when, day, deadline, days, yearend, v); }
        Settings st() { return new Settings(monthly, when, day, deadline, days, yearend, send); }
    }

    static int hm(int h, int m) { return h * 60 + m; }
    static int hm(int h) { return hm(h, 0); }

    static List<String> run(S s, Object state, int y, int m, int d, int minutes) {
        List<String> out = new ArrayList<>();
        for (Due x : ReminderLogic.dueFor(s.st(), state, new Now(y, m, d, minutes)))
            out.add(x.kind().name().toLowerCase() + ":" + x.period());
        return out;
    }
    static List<String> run(S s, Object state, int y, int m, int d) { return run(s, state, y, m, d, hm(12)); }

    static Map<String, Object> months(String month, String cat, Object list) {
        return Map.of("months", Map.of(month, Map.of(cat, list)));
    }

    @Test
    void bangkokTimeCrossesUtcDay() {
        assertEquals(new Now(2026, 10, 18, 60), ReminderLogic.bangkokNow(Instant.parse("2026-10-17T18:00:00Z")));
        assertEquals(new Now(2026, 10, 17, 23 * 60 + 59), ReminderLogic.bangkokNow(Instant.parse("2026-10-17T16:59:00Z")));
        assertEquals(29, ReminderLogic.lastDayOfMonth(2028, 2));
        assertEquals(28, ReminderLogic.lastDayOfMonth(2027, 2));
        assertEquals(31, ReminderLogic.lastDayOfMonth(2026, 12));
    }

    @Test
    void hasIncomeInMonthHandlesOldAndNewShapes() {
        assertFalse(ReminderLogic.hasIncomeInMonth(Map.of(), 10, null));
        assertFalse(ReminderLogic.hasIncomeInMonth(null, 10, null));
        // รูปแบบเก่า (ไม่แยกปี)
        assertFalse(ReminderLogic.hasIncomeInMonth(months("10", "c1", List.of(Map.of("i", 0))), 10, null));
        assertTrue(ReminderLogic.hasIncomeInMonth(months("10", "c1", List.of(Map.of("i", 50000))), 10, null));
        assertTrue(ReminderLogic.hasIncomeInMonth(months("10", "c4", List.of(Map.of("i", 0), Map.of("i", 1200))), 10, null));
        assertTrue(ReminderLogic.hasIncomeInMonth(months("10", "c1", List.of(50000, 1800)), 10, null));
        assertFalse(ReminderLogic.hasIncomeInMonth(months("9", "c1", List.of(Map.of("i", 50000))), 10, null));
        // ข้อมูลเก่าถือเป็นปี 2569 เท่านั้น
        assertTrue(ReminderLogic.hasIncomeInMonth(months("10", "c1", List.of(Map.of("i", 1))), 10, 2569));
        assertFalse(ReminderLogic.hasIncomeInMonth(months("10", "c1", List.of(Map.of("i", 1))), 10, 2570));
        // รูปแบบใหม่แยกปี
        Object years = Map.of("years", Map.of("2569", Map.of("months", Map.of("10", Map.of("c1", List.of(Map.of("i", 5)))))));
        assertTrue(ReminderLogic.hasIncomeInMonth(years, 10, 2569));
        assertFalse(ReminderLogic.hasIncomeInMonth(years, 10, 2568));
        assertFalse(ReminderLogic.hasIncomeInMonth(years, 10, null));
    }

    // ใช้ state รูปแบบใหม่ในการทดสอบที่เหลือ: ปี พ.ศ. = ค.ศ. + 543
    static Object incomeIn(int yearCE, int month) {
        return Map.of("years", Map.of(String.valueOf(yearCE + 543),
            Map.of("months", Map.of(String.valueOf(month), Map.of("c1", List.of(Map.of("i", 1)))))));
    }

    @Test
    void userSendTimeIsRespected() {
        S s = new S().wh("end").send(hm(20, 30));
        assertEquals(List.of(), run(s, Map.of(), 2026, 10, 31, hm(20, 29)));
        assertEquals(List.of("monthly_end:2026-10"), run(s, Map.of(), 2026, 10, 31, hm(20, 30)));
        assertEquals(List.of("monthly_end:2026-10"), run(s, Map.of(), 2026, 10, 31, hm(23, 30)));
    }

    @Test
    void endOfMonth() {
        S s = new S().wh("end");
        assertEquals(List.of(), run(s, Map.of(), 2026, 10, 30));
        assertEquals(List.of("monthly_end:2026-10"), run(s, Map.of(), 2026, 10, 31));
        assertEquals(List.of("monthly_end:2027-02"), run(s, Map.of(), 2027, 2, 28));
        assertEquals(List.of(), run(s, Map.of(), 2028, 2, 28));
        assertEquals(List.of("monthly_end:2028-02"), run(s, Map.of(), 2028, 2, 29));
        assertEquals(List.of(), run(s, incomeIn(2026, 10), 2026, 10, 31));
    }

    @Test
    void startOfMonth() {
        S s = new S().wh("start");
        assertEquals(List.of(), run(s, Map.of(), 2026, 10, 1, hm(8)));
        assertEquals(List.of("monthly_start:2026-10"), run(s, Map.of(), 2026, 10, 1, hm(9)));
        assertEquals(List.of("monthly_start:2026-10"), run(s, Map.of(), 2026, 10, 5));
        assertEquals(List.of(), run(s, Map.of(), 2026, 10, 8));
        assertEquals(List.of(), run(s, incomeIn(2026, 9), 2026, 10, 1));
        assertEquals(List.of(), run(s, Map.of(), 2027, 1, 1));
        Due d = ReminderLogic.dueFor(s.st(), Map.of(), new Now(2026, 10, 1, hm(12))).get(0);
        assertEquals(9, d.ctx().get("month"));
    }

    @Test
    void customDay() {
        assertEquals(List.of(), run(new S().day(25), Map.of(), 2026, 10, 24));
        assertEquals(List.of("monthly_custom:2026-10"), run(new S().day(25), Map.of(), 2026, 10, 25));
        assertEquals(List.of("monthly_custom:2026-10"), run(new S().day(25), Map.of(), 2026, 10, 31));
        assertEquals(List.of("monthly_custom:2026-10"), run(new S().day(10), Map.of(), 2026, 10, 10));
        assertEquals(List.of(), run(new S().day(25), incomeIn(2026, 10), 2026, 10, 28));
    }

    @Test
    void multiSelectAndMasterSwitch() {
        S s = new S().wh("start", "end", "custom").day(28);
        assertEquals(List.of("monthly_end:2026-10", "monthly_custom:2026-10"), run(s, Map.of(), 2026, 10, 31));
        assertEquals(List.of("monthly_start:2026-10"), run(s, Map.of(), 2026, 10, 2));
        assertEquals(List.of(), run(new S().monthly(false).wh("start", "end", "custom"), Map.of(), 2026, 10, 31));
        assertEquals(List.of(), run(new S().wh(), Map.of(), 2026, 10, 31));
    }

    @Test
    void deadlineCountdown() {
        S s = new S().monthly(false).dy(14, 7, 1, 0);
        assertEquals(List.of(), run(s, Map.of(), 2027, 3, 16));
        assertEquals(List.of("deadline:2027-n14"), run(s, Map.of(), 2027, 3, 17));
        assertEquals(List.of("deadline:2027-n14"), run(s, Map.of(), 2027, 3, 18));
        assertEquals(List.of(), run(s, Map.of(), 2027, 3, 19));
        assertEquals(List.of("deadline:2027-n7"), run(s, Map.of(), 2027, 3, 24));
        assertEquals(List.of("deadline:2027-n1"), run(s, Map.of(), 2027, 3, 30));
        assertEquals(List.of("deadline:2027-n1", "deadline:2027-n0"), run(s, Map.of(), 2027, 3, 31));
        assertEquals(List.of(), run(s, Map.of(), 2027, 4, 1));
        Due d = ReminderLogic.dueFor(s.st(), Map.of(), new Now(2027, 3, 24, hm(12))).get(0);
        assertEquals(2569, d.ctx().get("taxYearBE"));
        assertEquals(7, d.ctx().get("daysLeft"));
        assertEquals(0, run(s.deadline(false), Map.of(), 2027, 3, 24).size());
        assertEquals(List.of(), run(s.dy(), Map.of(), 2027, 3, 24));
        assertEquals(List.of("deadline:2027-n7"), run(new S().monthly(false).dy(7, 7), Map.of(), 2027, 3, 24));
        assertEquals(List.of(), run(new S().monthly(false).dy(99, -1), Map.of(), 2027, 3, 24));
    }

    @Test
    void deadlineRespectsSendTimeOnExactDay() {
        S s = new S().monthly(false).dy(7).send(hm(18));
        assertEquals(List.of(), run(s, Map.of(), 2027, 3, 24, hm(17, 30)));
        assertEquals(List.of("deadline:2027-n7"), run(s, Map.of(), 2027, 3, 24, hm(18)));
    }

    @Test
    void yearEnd() {
        S s = new S().monthly(false).deadline(false);
        assertEquals(List.of("yearend:2026-11-01"), run(s, Map.of(), 2026, 11, 1));
        assertEquals(List.of(), run(s, Map.of(), 2026, 11, 4));
        Due d = ReminderLogic.dueFor(s.st(), Map.of(), new Now(2026, 12, 1, hm(12))).get(0);
        assertEquals(30, d.ctx().get("daysLeft"));
        assertEquals(2569, d.ctx().get("taxYearBE"));
        assertEquals(List.of(), run(s.yearend(false), Map.of(), 2026, 11, 1));
    }

    @Test
    void brokenSettingsDoNotThrow() {
        Settings broken = new Settings(true, null, 0, true, null, false, 540);
        assertEquals(List.of(), ReminderLogic.dueFor(broken, Map.of(), new Now(2026, 10, 31, hm(12))));
    }

    @Test
    void everyMailHasUnsubscribeLinkAndThai() {
        List<Due> dues = List.of(
            new Due(Kind.MONTHLY_START, "2026-10", Map.of("month", 9)),
            new Due(Kind.MONTHLY_END, "2026-10", Map.of("month", 10)),
            new Due(Kind.MONTHLY_CUSTOM, "2026-10", Map.of("month", 10)),
            new Due(Kind.DEADLINE, "2027-n7", Map.of("taxYearBE", 2569, "daysLeft", 7, "dm", 3, "dd", 31)),
            new Due(Kind.DEADLINE, "2027-n0", Map.of("taxYearBE", 2569, "daysLeft", 0, "dm", 3, "dd", 31)),
            new Due(Kind.YEAREND, "2026-12-01", Map.of("taxYearBE", 2569, "daysLeft", 30)));
        for (Due due : dues) {
            MailTemplates.Mail mail = MailTemplates.compose(due);
            MailTemplates.Rendered r = MailTemplates.render(mail, "https://app.example.com", "https://app.example.com/unsubscribe.html?t=abc");
            assertTrue(mail.subject().length() > 5);
            assertTrue(r.html().contains("unsubscribe.html?t=abc"));
            assertTrue(r.text().contains("ยกเลิกรับอีเมล: https://app.example.com/unsubscribe.html?t=abc"));
            for (String s : List.of(r.html(), r.text(), mail.subject()))
                assertTrue(!s.contains("null") && !s.contains("NaN"), s.substring(0, Math.min(40, s.length())));
        }
        assertTrue(MailTemplates.compose(dues.get(0)).subject().contains("กันยายน"));
        assertTrue(MailTemplates.compose(dues.get(3)).heading().contains("อีก 7 วัน")
            && MailTemplates.compose(dues.get(3)).paragraphs().get(0).contains("31 มีนาคม"));
        assertTrue(MailTemplates.compose(dues.get(4)).subject().contains("วันนี้"));
    }
}
