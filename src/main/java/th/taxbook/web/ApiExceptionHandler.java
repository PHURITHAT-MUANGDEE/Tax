package th.taxbook.web;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import th.taxbook.data.Conflict;
import th.taxbook.data.NotFound;
import th.taxbook.validation.DataValidator.Invalid;

/** แปลงข้อผิดพลาดของชั้นข้อมูลเป็นรหัส HTTP ที่เหมาะสม ตัวข้อความเป็นรหัสสั้นๆ ไม่เปิดเผยรายละเอียดภายใน */
@RestControllerAdvice(basePackages = "th.taxbook.web")
public class ApiExceptionHandler {
    @ExceptionHandler(Invalid.class)
    ResponseEntity<Map<String, String>> invalid(Invalid e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(NotFound.class)
    ResponseEntity<Map<String, String>> notFound(NotFound e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
    }

    @ExceptionHandler(Conflict.class)
    ResponseEntity<Map<String, String>> conflict(Conflict e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", "file_too_large"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> integrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "conflict"));
    }
}