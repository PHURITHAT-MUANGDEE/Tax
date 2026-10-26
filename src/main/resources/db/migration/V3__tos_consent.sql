-- บันทึกว่าผู้ใช้ยอมรับข้อกำหนดการใช้บริการและรับทราบนโยบายความเป็นส่วนตัวฉบับใด เมื่อใด
alter table users add column if not exists tos_accepted_at timestamptz;
alter table users add column if not exists tos_version     text;
