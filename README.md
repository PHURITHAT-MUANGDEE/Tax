PROLECT NAME: Revenue management system and understanding of taxes

DESCRIPTION: This system stems from a lack of understanding of taxation among Thais and the complexity of Thailand's personal income tax system, particularly the classification of assessable income under Section 40 of the Revenue Code, as well as the diverse and constantly changing conditions and limitations on deductions and tax benefits.

## GROUP MEMBER:
- 6831503050 Thanakrit Phochot 
- 6831503056 Pradisaru Jaroenthua
- 6831503058 Pannawat Kanha
- 6831503062 Pitchayapa Boonsiri
- 6831503065 Phurithat Muangdee

## LINK WEB:
https://taxbook-qlkhrj2c4a-as.a.run.app/

# คู่มือรันสมุดภาษีด้วย `mvn spring-boot:run`
เวลาที่ใช้ประมาณ 20–30 นาที (ส่วนใหญ่หมดไปกับการสร้าง Google OAuth ครั้งแรกและการโหลด dependency ของ Maven)

## ภาพรวมสิ่งที่ต้องมี
 
| สิ่งที่ต้องมี | ใช้ทำอะไร | ตรวจว่ามีแล้วด้วยคำสั่ง |
|---|---|---|
| Java 21 (JDK) | คอมไพล์และรันแอป | `java -version` ต้องขึ้น 21 |
| Maven 3.9+ | สั่ง build / run | `mvn -v` |
| Docker | เปิดฐานข้อมูล PostgreSQL ง่าย ๆ | `docker --version` |
| Google OAuth Client | แอปล็อกอินด้วย Google เท่านั้น | (สร้างในขั้นที่ 3) |
| อินเทอร์เน็ต | Maven โหลดไลบรารีครั้งแรก | |
 
ไม่มี Docker ก็ได้ ถ้าติดตั้ง PostgreSQL 13+ เองในเครื่อง (ดูข้ามไปที่ "ไม่ใช้ Docker")
 
---
 
## ขั้นที่ 1: เตรียม Java 21 และ Maven
 
### ใน GitHub Codespaces
Java และ Docker มักมีให้แล้ว ลองตรวจก่อน:
 
```bash
java -version
mvn -v
docker --version
```
 
ถ้า `java` ไม่ใช่เวอร์ชัน 21 หรือไม่มี `mvn` ให้ติดตั้งผ่าน SDKMAN:
 
```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21-tem
sdk install maven
```

### บนเครื่องตัวเอง
- **Windows:** ติดตั้ง [Temurin JDK 21](https://adoptium.net), [Maven](https://maven.apache.org/download.cgi) (แตก zip แล้วเพิ่มโฟลเดอร์ `bin` ใน PATH) และ [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- **macOS:** `brew install openjdk@21 maven` และติดตั้ง Docker Desktop
- **Ubuntu/Debian:** `sudo apt install openjdk-21-jdk maven` และติดตั้ง Docker
เปิด Terminal ใหม่ แล้วตรวจอีกครั้งว่า `java -version` ขึ้น 21 และ `mvn -v` ทำงานได้
 
---
 
## ขั้นที่ 2: ดึงโค้ดมา
 
```bash
git clone https://github.com/PHURITHAT-MUANGDEE/Tax (ชื่อ)
cd ชื่อ
```

(ถ้าอยู่ใน Codespace ของโปรเจกต์นี้แล้ว ใช้ `cd /workspaces/Tax` และ `git pull`)
 
---

## ขั้นที่ 3: สร้าง Google OAuth Client (ทำครั้งเดียว)
 
แอปล็อกอินด้วย Google เท่านั้น จึงต้องมี Client ID และ Client Secret
 
1. เข้า [Google Cloud Console](https://console.cloud.google.com) แล้วเลือกหรือสร้างโปรเจกต์
2. เมนู **APIs & Services > OAuth consent screen**
   - เลือก User type: **External** กด Create
   - กรอกชื่อแอป อีเมลติดต่อ แล้วกดต่อจนจบ
   - ในหน้า **Test users** กด Add users ใส่อีเมล Google ของทุกคนที่จะล็อกอินทดสอบ (แอปอยู่โหมดทดสอบ คนที่ไม่อยู่ในรายชื่อจะล็อกอินไม่ได้)
3. เมนู **APIs & Services > Credentials > Create credentials > OAuth client ID**
   - Application type: **Web application**
   - ตรงช่อง **Authorized redirect URIs** กด Add URI แล้วใส่ที่อยู่ตามที่รัน (เลือกข้อเดียวที่ตรงกับของคุณ):
4. กด Create จะได้หน้าต่างแสดง **(copy ไว้เอาไปใส่ในขั้นที่ 4)**
Client ID หน้าตาประมาณ 123456789-abc....apps.googleusercontent.com
Client secret ขึ้นต้นด้วย GOCSPX-???

| รันที่ไหน | Redirect URI |
|---|---|
| เครื่องตัวเอง | `http://localhost:8080/login/oauth2/code/google` |
| GitHub Codespaces | `https://<ชื่อ-codespace>-8080.app.github.dev/login/oauth2/code/google` |
 
   ชื่อ Codespace ดูได้ด้วย `echo $CODESPACE_NAME` (ในสคริปต์ `run-codespace.sh` จะพิมพ์ที่อยู่นี้ให้ตอนเริ่มรัน)
4. กด Create จด **Client ID** และ **Client Secret** ไว้
 
---
 
## ขั้นที่ 4: ตั้งค่าไฟล์ `.env`
 
```bash
cp .env.example .env
```
 
เปิด `.env` แล้วแก้ 3 บรรทัดนี้ (ที่เหลือปล่อยไว้ได้):
 
```
DB_PASSWORD=taxbook
GOOGLE_CLIENT_ID=ค่าที่ได้จากขั้นที่ 3
GOOGLE_CLIENT_SECRET=ค่าที่ได้จากขั้นที่ 3
```
 
- `DB_PASSWORD` ตั้งเป็นอะไรก็ได้ (ใช้ทั้งตอนสร้างฐานข้อมูลและตอนแอปเชื่อมต่อ) ตัวอย่างใช้ `taxbook`
- ห้าม commit ไฟล์ `.env` ขึ้น GitHub (ถูกกันไว้ใน `.gitignore` แล้ว)
- ไม่ต้องตั้งค่า SMTP ถ้าไม่ต้องการทดสอบการส่งอีเมล (ไม่ตั้ง = ไม่ส่งอีเมล แอปยังทำงานปกติ)
---
 
## ขั้นที่ 5: รัน
 
### วิธีง่ายที่สุด: ใช้สคริปต์ (Mac / Linux / Codespaces)
 
```bash
chmod +x run-codespace.sh
./run-codespace.sh
```
 
สคริปต์ทำให้ครบ: เปิดฐานข้อมูล ตั้งตัวแปร แล้วรัน `mvn spring-boot:run` ครั้งแรกจะช้า เพราะ Maven โหลดไลบรารี
 
### วิธีทีละคำสั่ง (ใช้ได้ทุกระบบ รวมถึง Windows)
 
**5.1 เปิดฐานข้อมูล**
```bash
docker compose -f docker-compose.dev.yml up -d db
```
รอสัก 10 วินาที แล้วเช็กว่าพร้อม: `docker compose -f docker-compose.dev.yml ps` ต้องขึ้น `healthy`
 
**5.2 ตั้งตัวแปรสภาพแวดล้อม**
 
Mac / Linux / Codespaces:
```bash
export DB_URL=jdbc:postgresql://localhost:5432/taxbook
export DB_USER=taxbook
export DB_PASSWORD=taxbook
export GOOGLE_CLIENT_ID=ค่าของคุณ
export GOOGLE_CLIENT_SECRET=ค่าของคุณ
export COOKIE_SECURE=false        # รันบน http://localhost
                                  # SECURE=true ถ้ารันบน codespace
```
ถ้าอยากให้ส่งเมลด้วย
```bash
export SMTP_USER=เมลของคุณ
export SMTP_PASSWORD=รหัสผ่านแอป16ตัวติดกัน
export MAIL_FROM=เมลของคุณ
```
 
Windows (PowerShell):
```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/taxbook"
$env:DB_USER="taxbook"
$env:DB_PASSWORD="taxbook"
$env:GOOGLE_CLIENT_ID="ค่าของคุณ"
$env:GOOGLE_CLIENT_SECRET="ค่าของคุณ"
$env:COOKIE_SECURE="false"
```
ถ้าอยากให้ส่งเมลด้วย
```bash
$env:SMTP_USER=เมลของคุณ
$env:SMTP_PASSWORD=รหัสผ่านแอป16ตัวติดกัน
$env:MAIL_FROM=เมลของคุณ
```

 
> ใน Codespaces ให้ตั้ง `APP_URL` เป็นที่อยู่ของ Codespace และ `COOKIE_SECURE=true` (เพราะเข้าผ่าน https) ซึ่ง `run-codespace.sh` ทำให้อัตโนมัติ
 
**5.3 รันแอป**
```bash
mvn spring-boot:run
```
 
---
 
## ขั้นที่ 6: เปิดเว็บและตรวจว่าทำงาน
 
เมื่อ log ขึ้นข้อความประมาณ `Started TaxbookApplication in ... seconds` แปลว่าแอปพร้อมแล้ว
 
- เครื่องตัวเอง: เปิด `http://localhost:8080`
- Codespaces: แท็บ **Ports** > พอร์ต 8080 > กดไอคอนเปิดในเบราว์เซอร์
ทดสอบ: กด "เข้าสู่ระบบด้วย Google" > ติ๊กยอมรับ > ล็อกอิน แล้วลองเพิ่มรายได้ 1 รายการ
 
ครั้งแรกที่รัน Flyway จะสร้างตารางทั้งหมดให้เอง (ดูใน log บรรทัด `Successfully applied 8 migrations`)
 
เมื่อจะหยุดแอป กด `Ctrl + C` ที่ Terminal ส่วนฐานข้อมูลหยุดด้วย `docker compose -f docker-compose.dev.yml down` (ข้อมูลยังอยู่ ถ้าเพิ่ม `-v` ข้อมูลจะถูกลบทั้งหมด)
 
---
 
## ไม่ใช้ Docker (ติดตั้ง PostgreSQL เอง)
 
1. ติดตั้ง PostgreSQL 13 ขึ้นไป
2. สร้างผู้ใช้และฐานข้อมูล:
```bash
psql -U postgres -c "CREATE USER taxbook WITH PASSWORD 'taxbook';"
psql -U postgres -c "CREATE DATABASE taxbook OWNER taxbook;"
```
3. ข้ามขั้น 5.1 แล้วทำ 5.2–5.3 ต่อ
---

## ถ้าติดปัญหา
 
| อาการ | สาเหตุ / วิธีแก้ |
|---|---|
| `mvn: command not found` | ยังไม่ได้ติดตั้ง Maven หรือยังไม่ได้เปิด Terminal ใหม่ |
| `release version 21 not supported` | ใช้ JDK เก่ากว่า 21 ตรวจ `java -version` และ `mvn -v` (ต้องขึ้น Java 21) |
| ดาวน์โหลด dependency ไม่ได้ / timeout | เครือข่ายเข้า Maven Central ไม่ได้ ลองเครือข่ายอื่น (ไม่ใช่ proxy ที่บล็อก) แล้วรันซ้ำ |
| `Connection refused` ที่ localhost:5432 | ฐานข้อมูลยังไม่รัน ทำขั้น 5.1 และรอให้ `healthy` |
| `password authentication failed` | `DB_PASSWORD` ที่ตั้งไม่ตรงกับตอนสร้างฐานข้อมูล ถ้าเปลี่ยนรหัสต้องลบ volume เดิม: `docker compose -f docker-compose.dev.yml down -v` แล้วเริ่มใหม่ |
| `Port 8080 was already in use` | มีโปรแกรมอื่นใช้พอร์ตนี้ รันด้วยพอร์ตอื่น: `PORT=8081 mvn spring-boot:run` และเปลี่ยน redirect URI ให้ตรง |
| กดล็อกอินแล้ว `redirect_uri_mismatch` | Redirect URI ใน Google Console ไม่ตรงกับที่อยู่ที่เปิดเว็บ ต้องตรงทุกตัวอักษร รวมพอร์ตและ `http`/`https` |
| `Error 403: access_denied` | อีเมลที่ล็อกอินยังไม่อยู่ในรายชื่อ Test users ของ OAuth consent screen |
| ล็อกอินแล้ววนกลับหน้าเข้าสู่ระบบ (บน localhost) | ลืมตั้ง `COOKIE_SECURE=false` |
| ล็อกอินไม่ได้ใน Codespaces | `APP_URL` ต้องเป็นที่อยู่ https ของ Codespace และ `COOKIE_SECURE=true` (ใช้ `run-codespace.sh` จะตั้งให้) |
 
## คำสั่งที่ใช้บ่อย
 
```bash
mvn -DskipTests compile          # เช็กว่าโค้ดคอมไพล์ผ่าน (ไม่ต้องมีฐานข้อมูล)
mvn test                         # เทสต์ JUnit (ไม่ต้องมีฐานข้อมูล)
mvn package                      # สร้างไฟล์ target/taxbook-1.0.0.jar
java -jar target/taxbook-1.0.0.jar   # รันจากไฟล์ jar (ต้องตั้งตัวแปรสภาพแวดล้อมเหมือนเดิม)
```
