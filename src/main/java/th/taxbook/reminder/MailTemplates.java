package th.taxbook.reminder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** ข้อความอีเมลแจ้งเตือน (ภาษาไทย) ไม่พึ่ง Spring */
public final class MailTemplates {
    private MailTemplates() {}

    private static final String[] MONTHS = {
        "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน",
        "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม"};

    public record Mail(String subject, String heading, List<String> paragraphs, String cta) {}
    public record Rendered(String html, String text) {}

    private static int get(Map<String, Integer> c, String k, int dflt) {
        Integer v = c.get(k);
        return v == null ? dflt : v;
    }

    public static Mail compose(ReminderLogic.Due due) {
        Map<String, Integer> c = due.ctx();
        switch (due.kind()) {
            case MONTHLY_START: {
                String mon = MONTHS[get(c, "month", 1) - 1];
                return new Mail("ต้นเดือน อย่าลืมบันทึกรายได้เดือน" + mon, "บันทึกรายได้เดือน" + mon,
                    List.of("เดือน" + mon + "ที่ผ่านมายังไม่มีรายได้ที่บันทึกในสมุดภาษี",
                        "บันทึกตอนนี้ใช้เวลาไม่ถึงนาที ภาษีที่ถูกหักและภาษีทั้งปีจะคำนวณให้ทันที ถ้าเดือนนั้นไม่มีรายได้ ไม่ต้องทำอะไร"),
                    "เปิดสมุดภาษี");
            }
            case MONTHLY_END: {
                String mon = MONTHS[get(c, "month", 1) - 1];
                return new Mail("สิ้นเดือน" + mon + " บันทึกรายได้เดือนนี้หรือยัง", "สิ้นเดือน" + mon + " บันทึกรายได้",
                    List.of("วันนี้เป็นวันสุดท้ายของเดือน" + mon + "และยังไม่มีรายได้เดือนนี้ที่บันทึกในสมุดภาษี",
                        "ใส่รายได้และภาษีที่ถูกหักของเดือนนี้ไว้ตอนนี้ จะได้ไม่ต้องไล่ย้อนหลังตอนยื่นภาษี ถ้าเดือนนี้ไม่มีรายได้ ไม่ต้องทำอะไร"),
                    "เปิดสมุดภาษี");
            }
            case MONTHLY_CUSTOM: {
                String mon = MONTHS[get(c, "month", 1) - 1];
                return new Mail("ยังไม่ได้บันทึกรายได้เดือน" + mon, "บันทึกรายได้เดือน" + mon,
                    List.of("เดือน" + mon + "นี้ยังไม่มีรายได้ที่บันทึกในสมุดภาษี",
                        "บันทึกตอนนี้ใช้เวลาไม่ถึงนาที ภาษีที่ถูกหักและภาษีทั้งปีจะคำนวณให้ทันที ถ้ายังไม่มีรายได้เดือนนี้ ไม่ต้องทำอะไร"),
                    "เปิดสมุดภาษี");
            }
            case DEADLINE: {
                int n = get(c, "daysLeft", 0), ty = get(c, "taxYearBE", 0);
                String date = get(c, "dd", 31) + " " + MONTHS[get(c, "dm", 3) - 1];
                return new Mail(
                    n == 0 ? "วันนี้ครบกำหนดยื่นภาษีปี " + ty : "ใกล้ครบกำหนดยื่นภาษีปี " + ty + " อีก " + n + " วัน",
                    n == 0 ? "วันนี้ครบกำหนดยื่นภาษีปี " + ty : "อีก " + n + " วันครบกำหนดยื่นภาษีปี " + ty,
                    List.of("กำหนดยื่นแบบและชำระภาษีคือวันที่ " + date + " การยื่นออนไลน์ผ่าน e-Filing ของกรมสรรพากรมักขยายเวลาออกไปอีกเล็กน้อย โปรดตรวจประกาศล่าสุดที่ rd.go.th",
                        "ก่อนยื่น เตรียมหนังสือรับรองการหักภาษี ณ ที่จ่าย (50 ทวิ) และหนังสือรับรองเบี้ยประกัน กองทุน เงินบริจาค แล้วตรวจยอดสรุปในสมุดภาษีให้ตรงกัน ถ้าภาษีที่ต้องจ่ายตั้งแต่ 3,000 บาทขึ้นไป แบ่งชำระได้ 3 งวด"),
                    "ดูสรุปภาษีของฉัน");
            }
            default: {
                int ty = get(c, "taxYearBE", 0), left = get(c, "daysLeft", 0);
                return new Mail("สิ้นปีภาษี " + ty + " อีก " + left + " วัน วางแผนลดหย่อนได้ตอนนี้",
                    "วางแผนลดหย่อนก่อนสิ้นปี " + ty,
                    List.of("อีก " + left + " วันจะสิ้นปีภาษี รายการที่ต้องจ่ายหรือซื้อภายในปีจึงจะใช้ลดหย่อนได้ เช่น กองทุนเพื่อการเกษียณ เบี้ยประกัน และเงินบริจาค",
                        "ในสมุดภาษีมีตัวเลขว่าคุณอยู่ขั้นภาษีใด และลดหย่อนเพิ่ม 10,000 บาทประหยัดภาษีได้เท่าไร"),
                    "ดูขั้นภาษีของฉัน");
            }
        }
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public static Rendered render(Mail mail, String appUrl, String unsubUrl) {
        String paras = mail.paragraphs().stream()
            .map(p -> "<p style=\"margin:0 0 12px;font-size:15px;line-height:1.7;\">" + esc(p) + "</p>")
            .collect(Collectors.joining());
        String html = "<!doctype html><html lang=\"th\"><body style=\"margin:0;background:#edf0f5;padding:24px 12px;font-family:'Segoe UI',Tahoma,sans-serif;color:#0f1b33;\">\n"
            + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\"><tr><td align=\"center\">\n"
            + "<table role=\"presentation\" width=\"100%\" style=\"max-width:560px;background:#ffffff;border-radius:6px;border:1px solid #d3dae6;\">\n"
            + "<tr><td style=\"padding:28px 28px 8px;\">\n"
            + "<div style=\"font-size:13px;color:#55627a;letter-spacing:.04em;\">สมุดภาษี</div>\n"
            + "<h1 style=\"margin:6px 0 14px;font-size:22px;line-height:1.35;\">" + esc(mail.heading()) + "</h1>\n"
            + paras + "\n"
            + "<p style=\"margin:20px 0 8px;\"><a href=\"" + esc(appUrl) + "\" style=\"display:inline-block;background:#1f3fbf;color:#ffffff;text-decoration:none;padding:11px 20px;border-radius:4px;font-size:15px;\">" + esc(mail.cta()) + "</a></p>\n"
            + "</td></tr>\n"
            + "<tr><td style=\"padding:16px 28px 24px;border-top:1px solid #d3dae6;font-size:12px;line-height:1.6;color:#55627a;\">\n"
            + "ตัวเลขในสมุดภาษีเป็นการประมาณเพื่อวางแผน ไม่ใช่คำแนะนำทางภาษี<br>\n"
            + "คุณได้รับอีเมลนี้เพราะเปิดการแจ้งเตือนไว้ <a href=\"" + esc(unsubUrl) + "\" style=\"color:#55627a;\">ยกเลิกรับอีเมล</a>\n"
            + "</td></tr></table></td></tr></table></body></html>";
        StringBuilder text = new StringBuilder(mail.heading()).append("\n\n");
        mail.paragraphs().forEach(p -> text.append(p).append('\n'));
        text.append('\n').append(mail.cta()).append(": ").append(appUrl).append("\n\n")
            .append("ตัวเลขในสมุดภาษีเป็นการประมาณเพื่อวางแผน ไม่ใช่คำแนะนำทางภาษี\n")
            .append("ยกเลิกรับอีเมล: ").append(unsubUrl);
        return new Rendered(html, text.toString());
    }
}
