#!/usr/bin/env bash
# ทดสอบสคริปต์ฐานข้อมูล (V1–V4) และคำสั่ง SQL ที่ Repos.java ใช้ กับ Postgres ในเครื่อง
# ต้องมี Postgres 13+ (initdb, pg_ctl, psql) ถ้ารันเป็น root สคริปต์จะสลับเป็นผู้ใช้ postgres ให้เอง
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
PGBIN="$(ls -d /usr/lib/postgresql/*/bin 2>/dev/null | tail -1)"
export PATH="$PGBIN:$PATH"
if [ "$(id -u)" = "0" ]; then exec runuser -u postgres -- env PATH="$PATH" bash "$0" "$@"; fi
D=/tmp/taxbook_pg2; PORT=5545; rm -rf "$D"
initdb -D "$D" -A trust >/dev/null
pg_ctl -D "$D" -o "-p $PORT -k /tmp -c listen_addresses=''" -l "$D/log" -w start >/dev/null
trap 'pg_ctl -D "$D" -m immediate stop >/dev/null' EXIT
P="psql -h /tmp -p $PORT -U postgres -d postgres -v ON_ERROR_STOP=1 -q -X"
q() { $P -At -c "$1"; }

pass=0; fail=0
ok()   { echo "PASS $1"; pass=$((pass+1)); }
bad()  { echo "FAIL $1 :: ${2:-}"; fail=$((fail+1)); }
eq()   { [ "$2" = "$3" ] && ok "$1" || bad "$1" "expected [$3] got [$2]"; }
rejects() { if $P -c "$2" >/dev/null 2>&1; then bad "$1" "ควรถูกปฏิเสธแต่ผ่าน"; else ok "$1"; fi; }

$P -f "$HERE/src/main/resources/db/migration/V1__init.sql"
$P -f "$HERE/src/main/resources/db/migration/V2__spring_session.sql"
$P -f "$HERE/src/main/resources/db/migration/V3__tos_consent.sql"

# --- V4: ย้ายข้อมูลเดิมจาก taxbook_state (JSON) เข้าตารางใหม่ ทั้งข้อมูลปกติและข้อมูลผิดรูปแบบ ---
LEGACY=$(python3 - <<'PY'
import json
print(json.dumps({
  "project": False, "sample": True,
  "profile": {"nick": "ต้น", "marital": "married_noinc", "kids": 2, "parents": 9, "ss": "9000", "retire": "abc", "rentType": 15, "excl4": True},
  "wm": {"c1": "auto", "c4": "10", "c9": "3", "c2": "weird"},
  "years": {
    "2569": {"months": {
      "1": {"c1": [{"i": 50000, "w": 0, "t": "auto", "n": "เงินเดือน"}, {"i": 1200, "w": 0, "t": "15", "n": "ดอกเบี้ย"}], "c9": [{"i": 5}]},
      "6": {"c2": [{"i": 15000, "t": "3", "n": "งานออกแบบ"}, "junk", [1, 2]]},
      "13": {"c1": [{"i": 1}]}, "x": {"c1": [{"i": 2}]}}},
    "2568": {"months": {"12": {"c4": [{"i": 300.5, "w": -5, "t": "zzz"}]}}},
    "1999": {"months": {"1": {"c1": [{"i": 1}]}}}, "abc": {}}}, ensure_ascii=False))
PY
)
L1=$(q "insert into users (google_sub, email) values ('legacy-1', 'l1@example.com') returning id" | head -1)
L2=$(q "insert into users (google_sub, email) values ('legacy-2', 'l2@example.com') returning id" | head -1)
L3=$(q "insert into users (google_sub, email) values ('legacy-3', 'l3@example.com') returning id" | head -1)
$P -c "insert into taxbook_state (user_id, state) values ('$L1', \$J\$$LEGACY\$J\$::jsonb), ('$L2', '{}'::jsonb)"
$P -f "$HERE/src/main/resources/db/migration/V4__normalize_entities.sql"
eq "V4 ย้ายโปรไฟล์: ชื่อ/สถานภาพ/บุตร" "$(q "select nick||'|'||marital||'|'||kids from tax_profile where user_id='$L1'")" "ต้น|married_noinc|2"
eq "V4 ค่าเกินช่วงถูกบีบ (parents 9 → 2) และข้อความไม่ใช่ตัวเลขเป็น 0" "$(q "select parents||'|'||ss||'|'||retire from tax_profile where user_id='$L1'")" "2|9000.00|0.00"
eq "V4 ค่าที่ไม่มีใช้ค่าเริ่มต้น (prof 30, biz 60) และ rent=15" "$(q "select rent_type||'|'||prof_type||'|'||biz_rate from tax_profile where user_id='$L1'")" "15.00|30.00|60.00"
eq "V4 excl4/project/sample" "$(q "select excl4||'|'||project_remaining||'|'||is_sample from tax_profile where user_id='$L1'")" "true|false|true"
eq "V4 ผู้ใช้ state ว่างและผู้ใช้ไม่มีแถว state ได้โปรไฟล์เริ่มต้น" "$(q "select count(*) from tax_profile where user_id in ('$L2','$L3') and marital='single' and biz_rate=60 and project_remaining")" 2
eq "V4 ปีภาษี: เหลือ 2568 และ 2569 (ตัด 1999 และ abc)" "$(q "select string_agg(year_be::text, ',' order by year_be) from tax_year where user_id='$L1'")" "2568,2569"
eq "V4 รายการรายได้ 4 รายการ (ตัดหมวด c9 เดือน 13 เดือน x ข้อมูลที่ไม่ใช่ object)" "$(q "select count(*) from income_entry e join tax_year y on y.id=e.tax_year_id where y.user_id='$L1'")" 4
eq "V4 รายการ ม.ค. 2569 เรียงตามเดิม" "$(q "select string_agg(e.amount::text||'/'||e.method||'/'||e.name, ';' order by e.id) from income_entry e join tax_year y on y.id=e.tax_year_id where y.user_id='$L1' and y.year_be=2569 and e.month=1")" "50000.00/auto/เงินเดือน;1200.00/15/ดอกเบี้ย"
eq "V4 ค่าลบเป็น 0 ทศนิยมคงอยู่ method ผิดเป็นว่าง" "$(q "select e.amount||'/'||e.withheld||'/['||e.method||']' from income_entry e join tax_year y on y.id=e.tax_year_id where y.user_id='$L1' and y.year_be=2568")" "300.50/0.00/[]"
eq "V4 วิธีหักที่เลือก: เก็บเฉพาะค่าถูกต้อง" "$(q "select string_agg(category_code||'='||method, ',' order by category_code) from withhold_pref where user_id='$L1'")" "c1=auto,c4=10"
eq "V4 ตารางประเภทเงินได้มี 8 แถว" "$(q "select count(*) from income_category")" 8
eq "V4 รันแล้ว taxbook_state ยังอยู่ครบ" "$(q "select count(*) from taxbook_state")" 2

# --- Repos.upsertUser ---
UP="insert into users (google_sub, email) values ('sub-1', 'a@example.com') on conflict (google_sub) do update set email = excluded.email returning id"
ID1=$(q "$UP" | head -1)
$P -c "insert into notification_settings (user_id, email) values ('$ID1', 'a@example.com') on conflict (user_id) do update set email = excluded.email" 
ID1b=$(q "insert into users (google_sub, email) values ('sub-1', 'new@example.com') on conflict (google_sub) do update set email = excluded.email returning id" | head -1)
eq "upsert คืน id เดิมเมื่อ sub ซ้ำ" "$ID1b" "$ID1"
eq "อีเมลอัปเดตตามบัญชี Google" "$(q "select email from users where id='$ID1'")" "new@example.com"
$P -c "insert into notification_settings (user_id, email) values ('$ID1', 'new@example.com') on conflict (user_id) do update set email = excluded.email"
eq "แถว settings มีแถวเดียว" "$(q "select count(*) from notification_settings where user_id='$ID1'")" 1
eq "ค่าเริ่มต้นแจ้งเตือนปิด" "$(q "select enabled from notification_settings where user_id='$ID1'")" f
eq "ค่าเริ่มต้นช่วงเตือน" "$(q "select monthly_when::text||deadline_days::text||send_minute::text from notification_settings where user_id='$ID1'")" "{custom}{14,7,1}540"
ID2=$(q "insert into users (google_sub, email) values ('sub-2', 'b@example.com') on conflict (google_sub) do update set email = excluded.email returning id" | head -1)
$P -c "insert into notification_settings (user_id, email) values ('$ID2', 'b@example.com');"
[ "$ID1" != "$ID2" ] && ok "ผู้ใช้คนละ sub ได้ id ต่างกัน" || bad "id ซ้ำ"

# --- ตาราง entity ใหม่ (คำสั่งเทียบเท่าที่ JPA จะสร้าง) ---
$P -c "insert into tax_profile (user_id) values ('$ID1')"
eq "โปรไฟล์ใหม่ได้ค่าเริ่มต้น" "$(q "select marital||kids||biz_rate||project_remaining from tax_profile where user_id='$ID1'")" "single060.00true"
rejects "tax_profile: marital ต้องเป็นค่าที่กำหนด" "update tax_profile set marital='x' where user_id='$ID1'"
rejects "tax_profile: ห้ามจำนวนเงินติดลบ" "update tax_profile set ss=-1 where user_id='$ID1'"
rejects "tax_profile: parents ไม่เกิน 2" "update tax_profile set parents=3 where user_id='$ID1'"
rejects "tax_profile: 1 แถวต่อผู้ใช้" "insert into tax_profile (user_id) values ('$ID1')"
Y1=$(q "insert into tax_year (user_id, year_be) values ('$ID1', 2569) returning id" | head -1)
rejects "tax_year: ปีซ้ำของผู้ใช้เดียวกันไม่ได้" "insert into tax_year (user_id, year_be) values ('$ID1', 2569)"
rejects "tax_year: ปีต้องอยู่ช่วง 2500-2700" "insert into tax_year (user_id, year_be) values ('$ID1', 1999)"
$P -c "insert into tax_year (user_id, year_be) values ('$ID2', 2569)"
ok "ผู้ใช้คนละคนมีปี 2569 เหมือนกันได้"
$P -c "insert into income_entry (tax_year_id, month, category_code, amount, withheld, method, name) values ($Y1, 10, 'c1', 50000, 0, 'auto', 'เงินเดือน'), ($Y1, 10, 'c4', 1200, 180, '15', ''), ($Y1, 3, 'c2', 15000, 450.5, 'manual', 'ฟรีแลนซ์')"
eq "เพิ่มรายการรายได้ได้ 3 รายการ" "$(q "select count(*) from income_entry where tax_year_id=$Y1")" 3
rejects "income_entry: เดือนต้อง 1-12" "insert into income_entry (tax_year_id, month, category_code) values ($Y1, 13, 'c1')"
rejects "income_entry: ประเภทต้องมีในตาราง income_category" "insert into income_entry (tax_year_id, month, category_code) values ($Y1, 1, 'c9')"
rejects "income_entry: ห้ามจำนวนเงินติดลบ" "insert into income_entry (tax_year_id, month, category_code, amount) values ($Y1, 1, 'c1', -5)"
rejects "income_entry: method ต้องเป็นรูปแบบที่กำหนด" "insert into income_entry (tax_year_id, month, category_code, method) values ($Y1, 1, 'c1', 'drop table')"
rejects "income_entry: ต้องอ้างปีภาษีที่มีอยู่" "insert into income_entry (tax_year_id, month, category_code) values (999999, 1, 'c1')"
eq "ค้นหา: กรองตามเดือนและประเภท" "$(q "select count(*) from income_entry e join tax_year y on y.id=e.tax_year_id where y.user_id='$ID1' and y.year_be=2569 and e.month=10 and e.category_code='c1'")" 1
eq "ค้นหา: ชื่อมีคำว่า ฟรี (ไม่สนตัวพิมพ์)" "$(q "select count(*) from income_entry e join tax_year y on y.id=e.tax_year_id where y.user_id='$ID1' and lower(e.name) like '%ฟรี%'")" 1
eq "สรุปต่อเดือน/ประเภท (query ของ summary)" "$(q "select e.month||':'||e.category_code||':'||sum(e.amount)||':'||sum(e.withheld) from income_entry e where e.tax_year_id=$Y1 group by e.month, e.category_code order by e.month, e.category_code limit 1")" "3:c2:15000.00:450.50"
eq "ตรวจเดือนที่บันทึกแล้ว (query ของตัวเตือน) ใช้ any(uuid[])" "$(q "select count(distinct y.user_id) from income_entry e join tax_year y on y.id=e.tax_year_id where y.user_id = any (array['$ID1'::uuid, '$ID2'::uuid]) and (e.amount > 0 or e.withheld > 0)")" 1
$P -c "update income_entry set amount=777, updated_at=now() where tax_year_id=$Y1 and category_code='c1'"
eq "แก้รายการรายได้ได้" "$(q "select amount from income_entry where tax_year_id=$Y1 and category_code='c1'")" "777.00"
$P -c "delete from income_entry where tax_year_id=$Y1 and month=3"
eq "ลบรายการรายได้ตามเดือนได้" "$(q "select count(*) from income_entry where tax_year_id=$Y1")" 2
$P -c "insert into withhold_pref (user_id, category_code, method) values ('$ID1', 'c4', '10') on conflict (user_id, category_code) do update set method = excluded.method"
$P -c "insert into withhold_pref (user_id, category_code, method) values ('$ID1', 'c4', '15') on conflict (user_id, category_code) do update set method = excluded.method"
eq "withhold_pref upsert ได้แถวเดียวและค่าล่าสุด" "$(q "select count(*)||':'||max(method) from withhold_pref where user_id='$ID1'")" "1:15"
$P -c "delete from tax_year where id=$Y1"
eq "ลบปีภาษี รายการรายได้ลบตาม (cascade)" "$(q "select count(*) from income_entry where tax_year_id=$Y1")" 0
Y1=$(q "insert into tax_year (user_id, year_be) values ('$ID1', 2569) returning id" | head -1)
$P -c "insert into income_entry (tax_year_id, month, category_code, amount) values ($Y1, 1, 'c1', 1)"

# --- Repos.updateSettings: ยินยอม/ยกเลิกรับ ---
ENABLE="update notification_settings set consent_at = case when true and not enabled then now() else consent_at end, unsubscribed_at = case when true and not enabled then null else unsubscribed_at end, enabled = true where user_id = '$ID1'"
$P -c "$ENABLE"
C1=$(q "select consent_at from notification_settings where user_id='$ID1'")
[ -n "$C1" ] && ok "เปิดแจ้งเตือนครั้งแรกบันทึกเวลายินยอม" || bad "consent_at ว่าง"
sleep 1; $P -c "$ENABLE"
eq "เปิดซ้ำตอนเปิดอยู่ ไม่ทับเวลายินยอม" "$(q "select consent_at from notification_settings where user_id='$ID1'")" "$C1"
$P -c "update notification_settings set monthly_when = '{start,end}', deadline_days = '{30,7,0}', send_minute = 1170, remind_day = 20 where user_id='$ID1'"
eq "ตั้งช่วงเตือนและเวลาได้" "$(q "select monthly_when::text||deadline_days::text||send_minute::text||remind_day::text from notification_settings where user_id='$ID1'")" "{start,end}{30,7,0}117020"
rejects "ห้าม send_minute=2000" "update notification_settings set send_minute=2000 where user_id='$ID1'"
rejects "ห้าม remind_day=31" "update notification_settings set remind_day=31 where user_id='$ID1'"
rejects "ห้าม monthly_when ค่าแปลก" "update notification_settings set monthly_when='{foo}' where user_id='$ID1'"
rejects "ห้าม deadline_days=5" "update notification_settings set deadline_days='{5}' where user_id='$ID1'"

# --- Repos.unsubscribe แล้วเปิดใหม่ ---
TOKEN=$(q "select unsub_token from notification_settings where user_id='$ID1'")
eq "unsubscribe พบ 1 แถว" "$(q "with u as (update notification_settings set enabled = false, unsubscribed_at = now() where unsub_token = '$TOKEN' returning 1) select count(*) from u")" 1
eq "unsubscribe โทเคนมั่ว ไม่พบแถว" "$(q "with u as (update notification_settings set enabled = false, unsubscribed_at = now() where unsub_token = gen_random_uuid() returning 1) select count(*) from u")" 0
eq "หลังยกเลิกรับ enabled=false" "$(q "select enabled from notification_settings where user_id='$ID1'")" f
$P -c "$ENABLE"
eq "เปิดใหม่ล้างสถานะยกเลิกรับ" "$(q "select unsubscribed_at is null from notification_settings where user_id='$ID1'")" t
eq "listEnabled เห็นเฉพาะคนที่เปิดและไม่ยกเลิก" "$(q "select count(*) from notification_settings where enabled and unsubscribed_at is null and email <> ''")" 1

# --- notification_log: จองสิทธิ์กันส่งซ้ำ ---
RES="insert into notification_log (user_id, kind, period) values ('$ID1', 'monthly_end', '2026-10') on conflict (user_id, kind, period) do nothing returning 1"
eq "จองสิทธิ์ครั้งแรกได้" "$(q "$RES" | head -1)" 1
eq "จองซ้ำไม่ได้" "$(q "$RES" | head -1)" ""
$P -c "delete from notification_log where user_id = '$ID1' and kind = 'monthly_end' and period = '2026-10'"
eq "ปล่อยสิทธิ์แล้วจองใหม่ได้" "$(q "$RES" | head -1)" 1

# --- ลบบัญชี cascade ---
$P -c "delete from users where id = '$ID1'"
eq "ลบผู้ใช้แล้วข้อมูลทุกตารางหายตาม (cascade)" "$(q "select (select count(*) from tax_profile where user_id='$ID1') + (select count(*) from tax_year where user_id='$ID1') + (select count(*) from income_entry where tax_year_id=$Y1) + (select count(*) from withhold_pref where user_id='$ID1') + (select count(*) from notification_settings where user_id='$ID1') + (select count(*) from notification_log where user_id='$ID1')")" 0
eq "ผู้ใช้อื่นไม่กระทบ" "$(q "select count(*) from tax_year where user_id='$ID2'")" 1

echo "ผ่าน $pass ไม่ผ่าน $fail"; [ $fail = 0 ]
