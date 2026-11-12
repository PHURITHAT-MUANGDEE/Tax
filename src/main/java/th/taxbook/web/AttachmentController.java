package th.taxbook.web;

import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import th.taxbook.data.AttachmentService;
import th.taxbook.validation.DataValidator.Invalid;

/**
 * ไฟล์หลักฐานแนบรายการ (PDF / JPG / PNG ไม่เกิน 5 MB)
 *   GET    /api/attachments?parent=income|expense|deduction&id=N   รายการไฟล์ของรายการนั้น
 *   GET    /api/attachments/counts                                 จำนวนไฟล์ของทุกรายการ
 *   POST   /api/attachments  (multipart: parent, parentId, docType, file)
 *   GET    /api/attachments/{id}/file                              ดาวน์โหลด
 *   DELETE /api/attachments/{id}
 */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {
    private final CurrentUser current;
    private final AttachmentService files;

    public AttachmentController(CurrentUser current, AttachmentService files) {
        this.current = current;
        this.files = files;
    }

    @GetMapping
    public List<AttachmentService.Attachment> list(@AuthenticationPrincipal OidcUser p, HttpSession s,
                                                   @RequestParam String parent, @RequestParam long id) {
        return files.list(current.id(p, s), parent, id);
    }

    @GetMapping("/counts")
    public List<Map<String, Object>> counts(@AuthenticationPrincipal OidcUser p, HttpSession s) {
        return files.counts(current.id(p, s));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentService.Attachment> upload(@AuthenticationPrincipal OidcUser p, HttpSession s,
                                                               @RequestParam String parent, @RequestParam long parentId,
                                                               @RequestParam(defaultValue = "other") String docType,
                                                               @RequestParam("file") MultipartFile file) {
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException e) { throw new Invalid("file_unreadable"); }
        AttachmentService.Attachment a = files.add(current.id(p, s), parent, parentId, docType, file.getOriginalFilename(), bytes);
        return ResponseEntity.status(HttpStatus.CREATED).body(a);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal OidcUser p, HttpSession s, @PathVariable long id) {
        AttachmentService.Download d = files.download(current.id(p, s), id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(d.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.fileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .body(d.content());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal OidcUser p, HttpSession s, @PathVariable long id) {
        files.delete(current.id(p, s), id);
        return ResponseEntity.noContent().build();
    }
}