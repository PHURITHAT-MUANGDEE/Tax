package th.taxbook.data;

/** ไม่พบข้อมูล หรือข้อมูลนั้นไม่ใช่ของผู้ใช้คนนี้ (ตอบ 404 เหมือนกัน เพื่อไม่เปิดเผยว่ามีข้อมูลของคนอื่นอยู่) */
public class NotFound extends RuntimeException {
    public NotFound(String message) { super(message); }
}
