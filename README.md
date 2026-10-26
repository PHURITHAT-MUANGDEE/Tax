
## โครงสร้าง

```
pom.xml                          Spring Boot 3.3 / Java 21
src/main/java/th/taxbook/
  config/SecurityConfig.java     ล็อกอิน Google (OIDC), เซสชัน, CSRF, 401 สำหรับ /api
  web/ApiController.java        /api/me, state, settings, export, account, unsubscribe
  web/AdminController.java      /admin/reminders/run (ทดสอบส่งอีเมลด้วยมือ ปิดไว้ถ้าไม่ตั้ง ADMIN_TOKEN)
  repo/Repos.java               SQL ทั้งหมด (JdbcTemplate)
  settings/SettingsValidator    ตรวจค่าตั้งค่าที่ผู้ใช้ส่งมา (ตรงกับ check constraint ในฐานข้อมูล)
  reminder/ReminderLogic        ตรรกะ "ใครควรได้อีเมลอะไรตอนนี้" (ไม่พึ่ง Spring)
  reminder/MailTemplates        ข้อความอีเมลภาษาไทย
  job/ReminderJob, ReminderService, SmtpMailer   ตั้งเวลาทุก 30 นาที จองสิทธิ์กันส่งซ้ำ ส่งผ่าน SMTP
src/main/resources/
  db/migration/V1__init.sql     ตาราง (Flyway รันให้เองตอนเริ่มแอป)
  static/                       index.html, unsubscribe.html, privacy.html
tests/                           db_test.sh, e2e.py, run_pure_tests.sh
```

| ชื่อ | ความหมาย |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | การเชื่อมต่อ PostgreSQL (ค่าเริ่มต้น `jdbc:postgresql://localhost:5432/taxbook`, ผู้ใช้ `taxbook`) |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | จาก Google Cloud Console |
| `APP_URL` | ที่อยู่เว็บจริง ไม่มี `/` ท้าย ใช้เป็นลิงก์ในอีเมล |
| `COOKIE_SECURE` | `true` (ค่าเริ่มต้น) ต้องใช้ HTTPS ทดลองบน `http://localhost` ให้ตั้ง `false` |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD` | ค่าเริ่มต้นเป็น Gmail (`smtp.gmail.com:587`) |
| `MAIL_FROM`, `MAIL_FROM_NAME` | ผู้ส่ง ถ้า `MAIL_FROM` ว่างจะไม่ส่งอีเมลจริง |
| `ADMIN_TOKEN` | เปิดปลายทางทดสอบ `/admin/reminders/run` (ว่าง = ปิด) |
| `REMINDERS_ENABLED`, `REMINDERS_MAX_PER_RUN`, `REMINDERS_PAUSE_MS` | เปิด/ปิดตัวตั้งเวลา จำนวนสูงสุดต่อรอบ (150) และหน่วงระหว่างฉบับ (300 มิลลิวินาที) |

## ชุดทดสอบ

```bash
bash tests/run_pure_tests.sh   # ตรรกะเตือน เทมเพลต ตัวตรวจค่า ไม่ต้องใช้ Maven (JDK อย่างเดียว)
bash tests/db_test.sh          # ฐานข้อมูลและ SQL ของ Repos ต้องมี Postgres ในเครื่อง
python3 tests/e2e.py           # หน้าเว็บกับหลังบ้านจำลอง ต้องมี playwright (Python)
mvn test                       # เทสต์ JUnit ชุดเดียวกับ run_pure_tests.sh (ยังไม่เคยรันผ่าน Maven)
```

## ข้อควรรู้