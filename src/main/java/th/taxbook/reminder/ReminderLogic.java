package th.taxbook.reminder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * ตรรกะล้วน ๆ ของการแจ้งเตือน ไม่พึ่ง Spring หรือฐานข้อมูล จึงทดสอบแยกได้
 * เวลาทั้งหมดเป็นเวลาไทย (UTC+7)
 */
public final class ReminderLogic {
    private ReminderLogic() {}

    public enum Kind { MONTHLY_START, MONTHLY_END, MONTHLY_CUSTOM, DEADLINE, YEAREND }

    /** monthlyWhen ผสมได้จาก "start" "end" "custom"; deadlineDays เช่น [14,7,1] (0 = วันสุดท้าย); sendMinute นับจากเที่ยงคืน 540 = 09:00 */
    public record Settings(boolean monthly, List<String> monthlyWhen, int remindDay,
                           boolean deadline, List<Integer> deadlineDays,
                           boolean yearend, int sendMinute) {}

    /** period คือคีย์กันส่งซ้ำ เช่น 2026-10 หรือ 2027-n7 */
    public record Due(Kind kind, String period, Map<String, Integer> ctx) {}

    public record Now(int y, int m, int d, int minutes) {}

    /** วันครบกำหนดยื่นและชำระภาษีของทุกปี {เดือน, วัน} ตามแบบกระดาษ */
    public static final int[] DEFAULT_DEADLINE = {3, 31};
    /** ปีที่กรมสรรพากรประกาศเลื่อนกำหนด ใส่ที่นี่ เช่น Map.of(2027, new int[]{4, 8}) แล้ว deploy ใหม่ */
    public static final Map<Integer, int[]> DEADLINE_OVERRIDES = Map.of();
    /** วันที่ส่งเตือนวางแผนลดหย่อนก่อนสิ้นปี {เดือน, วัน} */
    public static final int[][] YEAREND_DATES = {{11, 1}, {12, 1}, {12, 15}};
    public static final int YEAREND_CATCH_UP_DAYS = 2;
    public static final int START_CATCH_UP_DAYS = 6;
    public static final int DEFAULT_SEND_MINUTE = 540;

    private static String pad(int n) { return n < 10 ? "0" + n : String.valueOf(n); }

    public static Now bangkokNow(Instant instant) {
        ZonedDateTime t = instant.atZone(ZoneOffset.ofHours(7));
        return new Now(t.getYear(), t.getMonthValue(), t.getDayOfMonth(), t.getHour() * 60 + t.getMinute());
    }

    static long dayNum(int y, int m, int d) { return LocalDate.of(y, m, d).toEpochDay(); }

    public static int lastDayOfMonth(int y, int m) { return LocalDate.of(y, m, 1).lengthOfMonth(); }

    /** วันครบกำหนดวันนี้ต้องถึงเวลาที่ผู้ใช้ตั้งก่อน ถ้าเลยวันมาแล้วภายในช่วงย้อนหลังให้ส่งได้ทันที */
    static boolean isDue(Now now, long dueDay, int sendMinute, long catchUp) {
        long diff = dayNum(now.y(), now.m(), now.d()) - dueDay;
        if (diff < 0 || diff > catchUp) return false;
        return diff > 0 || now.minutes() >= sendMinute;
    }

    /**
     * state คือ JSON ที่แปลงเป็น Map/List/Number แล้ว
     * ข้อมูลรุ่นใหม่เก็บใน years[ปี พ.ศ.].months ส่วนรุ่นเก่าเก็บใน months ซึ่งเป็นของปี 2569
     * yearBE เป็น null ได้ (ไม่ระบุปี)
     */
    public static boolean hasIncomeInMonth(Object state, int month, Integer yearBE) {
        if (!(state instanceof Map<?, ?> st)) return false;
        Object months;
        if (st.get("years") instanceof Map<?, ?> years) {
            if (yearBE == null) return false;
            Object y = years.get(String.valueOf(yearBE));
            months = y instanceof Map<?, ?> ym ? ym.get("months") : null;
        } else if (yearBE == null || yearBE == 2569) {
            months = st.get("months");
        } else {
            return false;
        }
        if (!(months instanceof Map<?, ?> mm)) return false;
        if (!(mm.get(String.valueOf(month)) instanceof Map<?, ?> mo)) return false;
        for (Object list : mo.values()) {
            if (!(list instanceof List<?> l) || l.isEmpty()) continue;
            if (l.get(0) instanceof Number first) {
                if (first.doubleValue() > 0) return true;
                continue;
            }
            for (Object e : l) {
                if (e instanceof Map<?, ?> em && em.get("i") instanceof Number n
                        && Double.isFinite(n.doubleValue()) && n.doubleValue() > 0) return true;
            }
        }
        return false;
    }

    public static List<Due> dueFor(Settings s, Object state, Now now) {
        List<Due> out = new ArrayList<>();
        int send = s.sendMinute();
        List<String> when = s.monthlyWhen() == null ? List.of() : s.monthlyWhen();
        String ym = now.y() + "-" + pad(now.m());

        if (s.monthly()) {
            // ต้นเดือน: เตือนให้บันทึกรายได้ของเดือนที่แล้ว (ม.ค. ข้าม เพราะเดือนที่แล้วเป็นของปีภาษีก่อนหน้า)
            if (when.contains("start") && now.m() > 1) {
                int prev = now.m() - 1;
                if (isDue(now, dayNum(now.y(), now.m(), 1), send, START_CATCH_UP_DAYS)
                        && !hasIncomeInMonth(state, prev, now.y() + 543)) {
                    out.add(new Due(Kind.MONTHLY_START, ym, Map.of("month", prev)));
                }
            }
            // ปลายเดือน: วันสุดท้ายของเดือน เตือนให้บันทึกรายได้เดือนนี้
            if (when.contains("end")) {
                int last = lastDayOfMonth(now.y(), now.m());
                if (isDue(now, dayNum(now.y(), now.m(), last), send, 0)
                        && !hasIncomeInMonth(state, now.m(), now.y() + 543)) {
                    out.add(new Due(Kind.MONTHLY_END, ym, Map.of("month", now.m())));
                }
            }
            // กำหนดวันเอง: ตั้งแต่วันที่เลือกเป็นต้นไปในเดือนนั้น ส่งครั้งเดียวถ้ายังไม่ได้บันทึก
            if (when.contains("custom")) {
                int day = s.remindDay() == 0 ? 25 : Math.min(28, Math.max(1, s.remindDay()));
                int catchUp = lastDayOfMonth(now.y(), now.m()) - day;
                if (isDue(now, dayNum(now.y(), now.m(), day), send, catchUp)
                        && !hasIncomeInMonth(state, now.m(), now.y() + 543)) {
                    out.add(new Due(Kind.MONTHLY_CUSTOM, ym, Map.of("month", now.m())));
                }
            }
        }

        if (s.deadline()) {
            // ยื่นในปี ค.ศ. Y เป็นภาษีของปี Y-1
            int[] dl = DEADLINE_OVERRIDES.getOrDefault(now.y(), DEFAULT_DEADLINE);
            long deadlineDay = dayNum(now.y(), dl[0], dl[1]);
            List<Integer> days = s.deadlineDays() == null ? List.of() : s.deadlineDays();
            for (Integer n : new LinkedHashSet<>(days)) {
                if (n == null || n < 0 || n > 60) continue;
                if (isDue(now, deadlineDay - n, send, n == 0 ? 0 : 1)) {
                    out.add(new Due(Kind.DEADLINE, now.y() + "-n" + n,
                            Map.of("taxYearBE", now.y() - 1 + 543, "daysLeft", n, "dm", dl[0], "dd", dl[1])));
                }
            }
        }

        if (s.yearend()) {
            for (int[] md : YEAREND_DATES) {
                if (isDue(now, dayNum(now.y(), md[0], md[1]), send, YEAREND_CATCH_UP_DAYS)) {
                    long left = Math.max(0, dayNum(now.y(), 12, 31) - dayNum(now.y(), now.m(), now.d()));
                    out.add(new Due(Kind.YEAREND, now.y() + "-" + pad(md[0]) + "-" + pad(md[1]),
                            Map.of("taxYearBE", now.y() + 543, "daysLeft", (int) left)));
                }
            }
        }
        return out;
    }
}
