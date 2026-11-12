package th.taxbook.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import th.taxbook.validation.DataValidator.Invalid;

/**
 * ไฟล์หลักฐาน (สลิป หนังสือรับรองการหักภาษี ฯลฯ) แนบกับรายได้ รายจ่าย หรือค่าลดหย่อน 1 รายการได้หลายไฟล์
 * ข้อมูลอ้างอิงอยู่ใน document_attachment ส่วนตัวไฟล์อยู่ใน document_content (bytea)
 * ทุกคำสั่งตรวจความเป็นเจ้าของผ่าน user_id ที่มาจากเซสชัน ชนิดไฟล์ตัดสินจากไบต์แรกของไฟล์ ไม่เชื่อชื่อไฟล์หรือ Content-Type ที่ส่งมา
 */
@Service
public class AttachmentService {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_PER_ENTRY = 10;
    public static final int MAX_PER_USER = 200;
    static final List<String> DOC_TYPES = List.of("slip", "withholding_cert", "receipt", "insurance_cert", "other");

    public record Attachment(long id, String parent, long parentId, String docType, String fileName, String contentType, int sizeBytes, String createdAt) {}
    public record Download(String fileName, String contentType, byte[] content) {}

    private final JdbcTemplate jdbc;

    public AttachmentService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** parent: income | expense | deduction → ชื่อคอลัมน์ (ค่าคงที่ ไม่ต่อจากข้อความผู้ใช้) */
    static String column(String parent) {
        return switch (parent == null ? "" : parent) {
            case "income" -> "income_entry_id";
            case "expense" -> "expense_entry_id";
            case "deduction" -> "tax_allowance_entry_id";
            default -> throw new Invalid("invalid_parent");
        };
    }

    private boolean owns(UUID uid, String parent, long id) {
        String sql = switch (parent) {
            case "income" -> "select count(*) from income_entry e join tax_year y on y.id = e.tax_year_id where e.id = ? and y.user_id = ?";
            case "expense" -> "select count(*) from expense_entry e join tax_year y on y.id = e.tax_year_id where e.id = ? and y.user_id = ?";
            default -> "select count(*) from tax_allowance_entry e join tax_year y on y.id = e.tax_year_id where e.id = ? and y.user_id = ?";
        };
        Integer n = jdbc.queryForObject(sql, Integer.class, id, uid);
        return n != null && n > 0;
    }

    /** ชนิดไฟล์จากไบต์แรก (PDF / JPEG / PNG) ไม่ใช่ชนิดที่รองรับ = null */
    public static String sniff(byte[] b) {
        if (b == null || b.length < 4) return null;
        if (b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return "application/pdf";
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        return null;
    }

    static String cleanName(String raw) {
        String n = raw == null ? "" : raw.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>:*?|]", "").strip();
        if (n.length() > 200) n = n.substring(n.length() - 200);
        return n.isEmpty() ? "file" : n;
    }

    @Transactional(readOnly = true)
    public List<Attachment> list(UUID uid, String parent, long parentId) {
        String col = column(parent);
        return jdbc.query("select id, doc_type, file_name, content_type, size_bytes, created_at from document_attachment where user_id = ? and "
                + col + " = ? order by id",
            (rs, i) -> new Attachment(rs.getLong("id"), parent, parentId, rs.getString("doc_type"), rs.getString("file_name"),
                rs.getString("content_type"), rs.getInt("size_bytes"), rs.getTimestamp("created_at").toInstant().toString()),
            uid, parentId);
    }

    /** จำนวนไฟล์ของแต่ละรายการ ใช้แสดงตัวเลขบนปุ่มแนบไฟล์: [{parent, id, n}] */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> counts(UUID uid) {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbc.query("select income_entry_id i, expense_entry_id e, tax_allowance_entry_id d, count(*) n from document_attachment where user_id = ? "
                + "group by income_entry_id, expense_entry_id, tax_allowance_entry_id", rs -> {
            long i = rs.getLong("i"); boolean iNull = rs.wasNull();
            long e = rs.getLong("e"); boolean eNull = rs.wasNull();
            long d = rs.getLong("d");
            out.add(Map.of("parent", !iNull ? "income" : !eNull ? "expense" : "deduction",
                "id", !iNull ? i : !eNull ? e : d, "n", rs.getInt("n")));
        }, uid);
        return out;
    }

    @Transactional
    public Attachment add(UUID uid, String parent, long parentId, String docType, String fileName, byte[] content) {
        String col = column(parent);
        String dt = docType == null || docType.isBlank() ? "other" : docType;
        if (!DOC_TYPES.contains(dt)) throw new Invalid("invalid_doc_type");
        if (content == null || content.length == 0) throw new Invalid("file_required");
        if (content.length > MAX_BYTES) throw new Invalid("file_too_large");
        String type = sniff(content);
        if (type == null) throw new Invalid("unsupported_file_type");
        if (!owns(uid, parent, parentId)) throw new NotFound("entry");
        Integer perUser = jdbc.queryForObject("select count(*) from document_attachment where user_id = ?", Integer.class, uid);
        if (perUser != null && perUser >= MAX_PER_USER) throw new Conflict("too_many_files");
        Integer perEntry = jdbc.queryForObject("select count(*) from document_attachment where user_id = ? and " + col + " = ?", Integer.class, uid, parentId);
        if (perEntry != null && perEntry >= MAX_PER_ENTRY) throw new Conflict("too_many_files_for_entry");
        String name = cleanName(fileName);
        Long id = jdbc.queryForObject("insert into document_attachment (user_id, " + col + ", doc_type, file_name, content_type, size_bytes) "
            + "values (?, ?, ?, ?, ?, ?) returning id", Long.class, uid, parentId, dt, name, type, content.length);
        jdbc.update("insert into document_content (attachment_id, content) values (?, ?)", id, content);
        return new Attachment(id, parent, parentId, dt, name, type, content.length, java.time.Instant.now().toString());
    }

    @Transactional(readOnly = true)
    public Download download(UUID uid, long id) {
        List<Download> r = jdbc.query("select a.file_name, a.content_type, c.content from document_attachment a "
                + "join document_content c on c.attachment_id = a.id where a.id = ? and a.user_id = ?",
            (rs, i) -> new Download(rs.getString(1), rs.getString(2), rs.getBytes(3)), id, uid);
        if (r.isEmpty()) throw new NotFound("attachment");
        return r.get(0);
    }

    @Transactional
    public void delete(UUID uid, long id) {
        if (jdbc.update("delete from document_attachment where id = ? and user_id = ?", id, uid) == 0) throw new NotFound("attachment");
    }
}