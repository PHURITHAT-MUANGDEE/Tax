package th.taxbook.data;

/** ทำรายการไม่ได้เพราะขัดกับข้อมูลที่มีอยู่ หรือเกินขีดจำกัด (ตอบ 409) */
public class Conflict extends RuntimeException {
    public Conflict(String message) { super(message); }
}
