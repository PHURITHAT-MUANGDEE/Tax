package th.taxbook.job;

/** ส่งอีเมลหนึ่งฉบับ แยกเป็น interface เพื่อให้ทดสอบได้โดยไม่ต้องส่งจริง */
public interface Mailer {
    boolean configured();

    /** unsubPostUrl ใช้ใส่หัวข้อ List-Unsubscribe (One-Click) โยน Exception ถ้าส่งไม่สำเร็จ */
    void send(String to, String subject, String html, String text, String unsubPostUrl) throws Exception;
}
