-- V5: ค่าลดหย่อนกลุ่ม "ประกันและดอกเบี้ย (รายปี)" เปลี่ยนจากช่องตัวเลขเดี่ยวใน tax_profile เป็นรายการหลายแถวต่อปีภาษี
--   tax_year 1─* deduction_entry
-- แต่ละแถวมีประเภท (kind) ชื่อรายการ ยอดเงิน และ % ภาษีที่ถูกหัก (ผู้ใช้กรอกเอง นำไปรวมเป็นเครดิตภาษี)
-- ค่าเดิมใน tax_profile (ss, life, health, phealth, retire, home) ถูกย้ายมาเป็นแถวของทุกปีที่ผู้ใช้มี
-- คอลัมน์เดิมยังอยู่ (ไม่ถูกลบ) เพื่อย้อนกลับได้ แอปเลิกอ่านแล้ว

create table deduction_entry (
  id           bigint generated always as identity primary key,
  tax_year_id  bigint not null references tax_year (id) on delete cascade,
  kind         text not null check (kind in ('ss', 'life', 'health', 'phealth', 'home', 'fund')),
  name         text not null check (char_length(btrim(name)) between 1 and 200),
  amount       numeric(14, 2) not null default 0 check (amount >= 0),
  withheld_pct numeric(5, 2) not null default 0 check (withheld_pct between 0 and 100),
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);
create index deduction_entry_year_idx on deduction_entry (tax_year_id);

-- ผู้ใช้ที่มีค่าเดิมแต่ยังไม่มีปีภาษีเลย: สร้างปีปัจจุบัน (พ.ศ.) ให้ เพื่อไม่ให้ค่าหาย
insert into tax_year (user_id, year_be)
select p.user_id, (extract(year from now())::int + 543)
from tax_profile p
where (p.ss + p.life + p.health + p.phealth + p.retire + p.home) > 0
  and not exists (select 1 from tax_year y where y.user_id = p.user_id);

insert into deduction_entry (tax_year_id, kind, name, amount)
select y.id, v.kind, v.name, v.amount
from tax_year y
join tax_profile p on p.user_id = y.user_id
cross join lateral (values
  ('ss'::text,      'ประกันสังคม'::text,                          p.ss),
  ('life'::text,    'ประกันชีวิต'::text,                          p.life),
  ('health'::text,  'ประกันสุขภาพ'::text,                         p.health),
  ('phealth'::text, 'ประกันสุขภาพบิดามารดา'::text,                p.phealth),
  ('home'::text,    'ดอกเบี้ยเงินกู้ซื้อบ้าน'::text,               p.home),
  ('fund'::text,    'หุ้นและกองทุน (เดิม: กองทุนเพื่อการเกษียณ)'::text, p.retire)
) as v(kind, name, amount)
where v.amount > 0;

comment on column tax_profile.ss      is 'เลิกใช้ตั้งแต่ V5: ย้ายไป deduction_entry (kind = ss)';
comment on column tax_profile.life    is 'เลิกใช้ตั้งแต่ V5: ย้ายไป deduction_entry (kind = life)';
comment on column tax_profile.health  is 'เลิกใช้ตั้งแต่ V5: ย้ายไป deduction_entry (kind = health)';
comment on column tax_profile.phealth is 'เลิกใช้ตั้งแต่ V5: ย้ายไป deduction_entry (kind = phealth)';
comment on column tax_profile.retire  is 'เลิกใช้ตั้งแต่ V5: ย้ายไป deduction_entry (kind = fund)';
comment on column tax_profile.home    is 'เลิกใช้ตั้งแต่ V5: ย้ายไป deduction_entry (kind = home)';