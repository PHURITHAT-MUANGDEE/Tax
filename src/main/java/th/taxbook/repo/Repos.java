package th.taxbook.repo;

import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** ทุกคำสั่ง SQL ของแอปอยู่ที่นี่ ทุกคำสั่งที่เกี่ยวกับผู้ใช้ผูกกับ user_id ที่มาจากเซสชัน ไม่รับจากหน้าเว็บ */
@Repository
public class Repos {
    private final JdbcTemplate jdbc;

    public Repos(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // ---------- users ----------

    /** สร้างหรืออัปเดตผู้ใช้จากบัญชี Google พร้อมแถวเริ่มต้นของการตั้งค่าและข้อมูล */
    @Transactional
    public UUID upsertUser(String googleSub, String email) {
        UUID id = jdbc.queryForObject(
            "insert into users (google_sub, email) values (?, ?) "
                + "on conflict (google_sub) do update set email = excluded.email returning id",
            UUID.class, googleSub, email);
        jdbc.update("insert into notification_settings (user_id, email) values (?, ?) "
            + "on conflict (user_id) do update set email = excluded.email", id, email);
        return id;
    }

    /** บันทึกการยอมรับข้อกำหนดการใช้บริการและรับทราบนโยบายความเป็นส่วนตัว (เวอร์ชัน + เวลา) */
    public void acceptTos(UUID id, String version) {
        jdbc.update("update users set tos_accepted_at = now(), tos_version = ? where id = ?", version, id);
    }

    /** เวอร์ชันข้อกำหนดที่ผู้ใช้เคยยอมรับ ว่างถ้ายังไม่เคย */
    public Optional<String> tosVersion(UUID id) {
        List<String> r = jdbc.queryForList("select tos_version from users where id = ?", String.class, id);
        return r.isEmpty() ? Optional.empty() : Optional.ofNullable(r.get(0));
    }

    /** ลบผู้ใช้และข้อมูลทุกตาราง (on delete cascade) */
    public int deleteUser(UUID id) { return jdbc.update("delete from users where id = ?", id); }

    // ---------- income (ใช้โดยตัวส่งอีเมลเตือน) ----------

    /**
     * รายการรายได้ของผู้ใช้ที่ระบุ ในรูปโครงสร้างที่ ReminderLogic ใช้:
     * years → "2569" → months → "10" → "c1" → [{i: จำนวนเงิน}] อ่านจากตาราง income_entry
     */
    public Map<UUID, Object> incomeFor(Collection<UUID> userIds) {
        Map<UUID, Object> out = new HashMap<>();
        if (userIds.isEmpty()) return out;
        jdbc.query(con -> {
            var ps = con.prepareStatement("select y.user_id, y.year_be, e.month, e.category_code, e.amount "
                + "from income_entry e join tax_year y on y.id = e.tax_year_id where y.user_id = any (?)");
            ps.setArray(1, con.createArrayOf("uuid", userIds.toArray()));
            return ps;
        }, (ResultSet rs) -> {
            UUID uid = rs.getObject("user_id", UUID.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) out.computeIfAbsent(uid, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("years", new HashMap<String, Object>());
                return m;
            });
            @SuppressWarnings("unchecked")
            Map<String, Object> years = (Map<String, Object>) root.get("years");
            @SuppressWarnings("unchecked")
            Map<String, Object> y = (Map<String, Object>) years.computeIfAbsent(String.valueOf(rs.getInt("year_be")), k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("months", new HashMap<String, Object>());
                return m;
            });
            @SuppressWarnings("unchecked")
            Map<String, Object> months = (Map<String, Object>) y.get("months");
            @SuppressWarnings("unchecked")
            Map<String, Object> cats = (Map<String, Object>) months.computeIfAbsent(String.valueOf(rs.getInt("month")), k -> new HashMap<String, Object>());
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) cats.computeIfAbsent(rs.getString("category_code"), k -> new ArrayList<Object>());
            list.add(Map.of("i", rs.getBigDecimal("amount")));
        });
        return out;
    }

    // ---------- notification_settings ----------

    private static <T> List<T> arr(ResultSet rs, String col, java.util.function.Function<Object, T> f) throws SQLException {
        Array a = rs.getArray(col);
        if (a == null) return List.of();
        Object[] raw = (Object[]) a.getArray();
        List<T> out = new ArrayList<>();
        for (Object o : raw) out.add(f.apply(o));
        return out;
    }

    private static SettingsRow map(ResultSet rs, int n) throws SQLException {
        return new SettingsRow(rs.getObject("user_id", UUID.class), rs.getString("email"), rs.getBoolean("enabled"),
            rs.getBoolean("monthly"), arr(rs, "monthly_when", o -> (String) o), rs.getInt("remind_day"),
            rs.getBoolean("deadline"), arr(rs, "deadline_days", o -> ((Number) o).intValue()), rs.getBoolean("yearend"),
            rs.getInt("send_minute"), rs.getObject("consent_at", java.time.OffsetDateTime.class),
            rs.getObject("unsubscribed_at", java.time.OffsetDateTime.class), rs.getObject("unsub_token", UUID.class));
    }

    public Optional<SettingsRow> getSettings(UUID userId) {
        return jdbc.query("select * from notification_settings where user_id = ?", Repos::map, userId).stream().findFirst();
    }

    /** ผู้ใช้ที่เปิดแจ้งเตือนอยู่และยังไม่ยกเลิกรับ (ให้ตัวตั้งเวลาใช้) */
    public List<SettingsRow> listEnabled(String onlyEmail) {
        String sql = "select * from notification_settings where enabled and unsubscribed_at is null and email <> ''"
            + (onlyEmail != null ? " and email = ?" : "") + " order by created_at limit 5000";
        return onlyEmail != null ? jdbc.query(sql, Repos::map, onlyEmail) : jdbc.query(sql, Repos::map);
    }

    /**
     * อัปเดตเฉพาะคอลัมน์ที่ผ่าน SettingsValidator แล้ว (ชื่อคอลัมน์มาจากรายการตายตัวด้านล่าง ไม่ใช่ข้อความจากผู้ใช้)
     * เมื่อเปิดแจ้งเตือนจากสถานะปิด จะบันทึกเวลายินยอมและล้างสถานะยกเลิกรับ
     */
    public Optional<SettingsRow> updateSettings(UUID userId, Map<String, Object> valid) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> e : valid.entrySet()) {
            String k = e.getKey();
            switch (k) {
                case "enabled" -> {
                    sets.add("consent_at = case when ? and not enabled then now() else consent_at end");
                    sets.add("unsubscribed_at = case when ? and not enabled then null else unsubscribed_at end");
                    sets.add("enabled = ?");
                    args.add(e.getValue()); args.add(e.getValue()); args.add(e.getValue());
                }
                case "monthly", "deadline", "yearend", "send_minute", "remind_day" -> { sets.add(k + " = ?"); args.add(e.getValue()); }
                case "monthly_when" -> { sets.add("monthly_when = ?"); args.add(new SqlArray("text", ((List<?>) e.getValue()).toArray())); }
                case "deadline_days" -> { sets.add("deadline_days = ?"); args.add(new SqlArray("integer", ((List<?>) e.getValue()).toArray())); }
                default -> throw new IllegalArgumentException("คอลัมน์ไม่รองรับ: " + k);
            }
        }
        args.add(userId);
        jdbc.update(con -> {
            var ps = con.prepareStatement("update notification_settings set " + String.join(", ", sets) + " where user_id = ?");
            for (int i = 0; i < args.size(); i++) {
                Object a = args.get(i);
                if (a instanceof SqlArray sa) ps.setArray(i + 1, sa.create(con)); else ps.setObject(i + 1, a);
            }
            return ps;
        });
        return getSettings(userId);
    }

    private record SqlArray(String type, Object[] values) {
        Array create(Connection con) throws SQLException { return con.createArrayOf(type, values); }
    }

    /** ยกเลิกรับอีเมลด้วยโทเคนในลิงก์ คืนจำนวนแถวที่พบ */
    public int unsubscribe(UUID token) {
        return jdbc.update("update notification_settings set enabled = false, unsubscribed_at = now() where unsub_token = ?", token);
    }

    // ---------- notification_log ----------

    /** จองสิทธิ์ส่ง true = จองได้ (ยังไม่เคยส่งรอบนี้) false = เคยส่งแล้ว */
    public boolean reserve(UUID userId, String kind, String period) {
        return jdbc.update("insert into notification_log (user_id, kind, period) values (?, ?, ?) "
            + "on conflict (user_id, kind, period) do nothing", userId, kind, period) == 1;
    }

    public void release(UUID userId, String kind, String period) {
        jdbc.update("delete from notification_log where user_id = ? and kind = ? and period = ?", userId, kind, period);
    }

}
