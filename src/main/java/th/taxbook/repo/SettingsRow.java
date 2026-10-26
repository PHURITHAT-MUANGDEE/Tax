package th.taxbook.repo;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import th.taxbook.reminder.ReminderLogic;

public record SettingsRow(UUID userId, String email, boolean enabled, boolean monthly, List<String> monthlyWhen,
                          int remindDay, boolean deadline, List<Integer> deadlineDays, boolean yearend,
                          int sendMinute, OffsetDateTime consentAt, OffsetDateTime unsubscribedAt, UUID unsubToken) {

    /** รูปแบบที่ส่งให้หน้าเว็บ (ชื่อคีย์เหมือนคอลัมน์) ไม่รวม user_id และ unsub_token */
    public Map<String, Object> toApi() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", email);
        m.put("enabled", enabled);
        m.put("send_minute", sendMinute);
        m.put("monthly", monthly);
        m.put("monthly_when", monthlyWhen);
        m.put("remind_day", remindDay);
        m.put("deadline", deadline);
        m.put("deadline_days", deadlineDays);
        m.put("yearend", yearend);
        m.put("consent_at", consentAt == null ? null : consentAt.toInstant().toString());
        m.put("unsubscribed_at", unsubscribedAt == null ? null : unsubscribedAt.toInstant().toString());
        return m;
    }

    public ReminderLogic.Settings toLogic() {
        return new ReminderLogic.Settings(monthly, monthlyWhen, remindDay, deadline, deadlineDays, yearend, sendMinute);
    }
}
