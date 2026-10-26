-- สมุดภาษี: โครงสร้างฐานข้อมูล (PostgreSQL 13 ขึ้นไป) รันอัตโนมัติด้วย Flyway ตอนเริ่มแอป

-- ผู้ใช้ (ล็อกอินด้วย Google ใช้ค่า sub ของ Google เป็นตัวระบุ ไม่ใช่อีเมล เพราะอีเมลเปลี่ยนได้)
create table users (
  id          uuid primary key default gen_random_uuid(),
  google_sub  text not null unique,
  email       text not null,
  created_at  timestamptz not null default now()
);

-- ข้อมูลของผู้ใช้ หนึ่งแถวต่อคน เก็บเป็น JSON ก้อนเดียวตามรูปแบบที่หน้าเว็บใช้
create table taxbook_state (
  user_id    uuid primary key references users (id) on delete cascade,
  state      jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

-- การตั้งค่าแจ้งเตือนทางอีเมล ค่าเริ่มต้นปิด ผู้ใช้ต้องกดยินยอมเอง
create table notification_settings (
  user_id         uuid primary key references users (id) on delete cascade,
  email           text not null,
  enabled         boolean not null default false,
  monthly         boolean not null default true,
  monthly_when    text[]  not null default array['custom']::text[],
  remind_day      smallint not null default 25,
  deadline        boolean not null default true,
  deadline_days   integer[] not null default array[14, 7, 1],
  yearend         boolean not null default true,
  send_minute     smallint not null default 540,
  consent_at      timestamptz,
  unsubscribed_at timestamptz,
  unsub_token     uuid not null default gen_random_uuid() unique,
  created_at      timestamptz not null default now(),
  constraint notification_remind_day_chk    check (remind_day between 1 and 28),
  constraint notification_send_minute_chk   check (send_minute between 0 and 1439),
  constraint notification_monthly_when_chk  check (monthly_when <@ array['start', 'end', 'custom']::text[]),
  constraint notification_deadline_days_chk check (deadline_days <@ array[0, 1, 3, 7, 14, 30])
);

-- บันทึกการส่ง ใช้กันส่งซ้ำ (หนึ่งแถวต่อชนิดต่อรอบต่อคน)
create table notification_log (
  id       bigint generated always as identity primary key,
  user_id  uuid not null references users (id) on delete cascade,
  kind     text not null,
  period   text not null,
  sent_at  timestamptz not null default now(),
  unique (user_id, kind, period)
);
