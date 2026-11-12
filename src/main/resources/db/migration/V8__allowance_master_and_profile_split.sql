-- V8: จัดโครงสร้างค่าลดหย่อนให้เป็นระบบ
--   allowance_type 1─* tax_allowance_entry *─1 tax_year   (ประเภทและวงเงินเป็น master data ไม่ฝังใน CHECK)
--   1) deduction_entry เปลี่ยนชื่อเป็น tax_allowance_entry (ข้อมูลเดิมอยู่ครบ)
--   2) allowance_type = ประเภทและหมวดค่าลดหย่อนที่ระบบรองรับ พร้อมวงเงินสูงสุดต่อปี
--   3) tax_allowance_entry.kind อ้างอิง allowance_type.code ด้วย FK แทน CHECK
--   4) ลบคอลัมน์ค่าลดหย่อนรายปีที่เลิกใช้ออกจาก tax_profile (ss, life, health, phealth, retire, home)
--      ค่าเหล่านี้ถูกย้ายไปเป็นแถวของ tax_allowance_entry ตั้งแต่ V5 แล้ว tax_profile เหลือเฉพาะข้อมูลประจำตัวและตัวเลือกของผู้ใช้

create table allowance_type (
  code         text primary key,
  allowance_group text not null check (allowance_group in ('insurance', 'interest_fund')),
  name         text not null,
  annual_limit numeric(14, 2) check (annual_limit is null or annual_limit > 0),
  limit_note   text not null default '',
  sort_order   smallint not null
);

insert into allowance_type (code, allowance_group, name, annual_limit, limit_note, sort_order) values
  ('ss33',    'insurance',     'ประกันสังคม มาตรา 33 (ลูกจ้าง)',          9000,   'หักตามจริง สูงสุด 9,000 บาท', 1),
  ('ss39',    'insurance',     'ประกันสังคม มาตรา 39 (ลาออกแล้วส่งต่อ)',   5184,   'สูงสุด 5,184 บาท (432 บาท × 12 เดือน)', 2),
  ('ss40',    'insurance',     'ประกันสังคม มาตรา 40 (อาชีพอิสระ)',        3600,   'สูงสุด 3,600 บาท (ทางเลือก 3 = 300 บาท × 12 เดือน)', 3),
  ('life',    'insurance',     'ประกันชีวิต',                              100000, 'สูงสุด 100,000 บาท (รวมกับประกันสุขภาพตัวเองแล้วไม่เกิน 100,000)', 4),
  ('health',  'insurance',     'ประกันสุขภาพตัวเอง',                       25000,  'สูงสุด 25,000 บาท (รวมกับประกันชีวิตแล้วไม่เกิน 100,000)', 5),
  ('phealth', 'insurance',     'ประกันสุขภาพบิดามารดา',                    15000,  'สูงสุด 15,000 บาท', 6),
  ('slife',   'insurance',     'ประกันชีวิตคู่สมรส',                       10000,  'สูงสุด 10,000 บาท ใช้ได้เมื่อคู่สมรสไม่มีเงินได้เท่านั้น', 7),
  ('home',    'interest_fund', 'ดอกเบี้ยเงินกู้ซื้อบ้าน',                   100000, 'หักตามจริง สูงสุด 100,000 บาท', 8),
  ('fund',    'interest_fund', 'หุ้นและกองทุน',                            500000, 'รวมกันไม่เกิน 500,000 บาท และไม่เกิน 30% ของเงินได้', 9);

alter table deduction_entry rename to tax_allowance_entry;
alter index deduction_entry_year_idx rename to tax_allowance_entry_year_idx;
alter table tax_allowance_entry rename constraint deduction_entry_kind_check to tax_allowance_entry_kind_check;
alter table tax_allowance_entry drop constraint tax_allowance_entry_kind_check;
alter table tax_allowance_entry rename column kind to allowance_type_code;
alter table tax_allowance_entry
  add constraint tax_allowance_entry_type_fk foreign key (allowance_type_code) references allowance_type (code);

alter table document_attachment rename column deduction_entry_id to tax_allowance_entry_id;
alter index document_attachment_deduction_idx rename to document_attachment_allowance_idx;

alter table tax_profile
  drop column ss, drop column life, drop column health, drop column phealth, drop column retire, drop column home;