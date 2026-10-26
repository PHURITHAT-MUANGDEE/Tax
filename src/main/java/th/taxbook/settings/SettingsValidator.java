package th.taxbook.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ตรวจค่าตั้งค่าแจ้งเตือนที่ผู้ใช้ส่งมา (PATCH /api/settings) ไม่พึ่ง Spring
 * กฎเดียวกับ check constraint ในฐานข้อมูล ผู้ใช้แก้ได้เฉพาะคีย์ในรายการนี้ ห้ามแก้อีเมล โทเคน หรือเวลายินยอม
 */
public final class SettingsValidator {
    private SettingsValidator() {}

    public static final Set<String> BOOLEANS = Set.of("enabled", "monthly", "deadline", "yearend");
    public static final Set<String> MONTHLY_WHEN = Set.of("start", "end", "custom");
    public static final Set<Integer> DEADLINE_DAYS = Set.of(0, 1, 3, 7, 14, 30);

    /** คืนค่าที่ตรวจและทำความสะอาดแล้ว (ลำดับตามที่ส่งมา ไม่มีค่าซ้ำ) หรือโยน IllegalArgumentException */
    public static Map<String, Object> validate(Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) throw new IllegalArgumentException("ไม่มีค่าที่จะบันทึก");
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : patch.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (BOOLEANS.contains(k)) {
                if (!(v instanceof Boolean)) throw new IllegalArgumentException(k + " ต้องเป็น true/false");
                out.put(k, v);
            } else if (k.equals("send_minute")) {
                out.put(k, intInRange(k, v, 0, 1439));
            } else if (k.equals("remind_day")) {
                out.put(k, intInRange(k, v, 1, 28));
            } else if (k.equals("monthly_when")) {
                List<String> l = new ArrayList<>();
                for (Object o : list(k, v)) {
                    if (!(o instanceof String s) || !MONTHLY_WHEN.contains(s)) throw new IllegalArgumentException(k + " มีค่าที่ไม่รองรับ");
                    l.add(s);
                }
                out.put(k, new ArrayList<>(new LinkedHashSet<>(l)));
            } else if (k.equals("deadline_days")) {
                List<Integer> l = new ArrayList<>();
                for (Object o : list(k, v)) {
                    int n = intInRange(k, o, 0, 30);
                    if (!DEADLINE_DAYS.contains(n)) throw new IllegalArgumentException(k + " มีค่าที่ไม่รองรับ");
                    l.add(n);
                }
                out.put(k, new ArrayList<>(new LinkedHashSet<>(l)));
            } else {
                throw new IllegalArgumentException("ไม่อนุญาตให้แก้ " + k);
            }
        }
        return out;
    }

    private static List<?> list(String k, Object v) {
        if (!(v instanceof List<?> l)) throw new IllegalArgumentException(k + " ต้องเป็นรายการ");
        if (l.size() > 20) throw new IllegalArgumentException(k + " ยาวเกินไป");
        return l;
    }

    private static int intInRange(String k, Object v, int lo, int hi) {
        if (!(v instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue()))
            throw new IllegalArgumentException(k + " ต้องเป็นจำนวนเต็ม");
        double d = n.doubleValue();
        if (d < lo || d > hi) throw new IllegalArgumentException(k + " ต้องอยู่ระหว่าง " + lo + " ถึง " + hi);
        return (int) d;
    }
}
