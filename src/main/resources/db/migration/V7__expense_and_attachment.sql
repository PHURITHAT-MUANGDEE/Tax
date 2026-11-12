-- V7: จัดการรายจ่าย (expense_entry) และแนบไฟล์หลักฐาน (document_attachment)
--   tax_year 1─* expense_entry
--   users 1─* document_attachment; document_attachment *─1 (income_entry | expense_entry | deduction_entry) เลือกได้อย่างเดียว
--   document_attachment 1─1 document_content (ตัวไฟล์ bytea แยกตารางเพื่อให้การอ่านรายการไม่ดึงไฟล์มาด้วย)

create table expense_entry (
  id            bigint generated always as identity primary key,
  tax_year_id   bigint not null references tax_year (id) on delete cascade,
  month         smallint not null check (month between 1 and 12),
  kind          text not null check (kind in ('personal', 'income_cost')),   -- ส่วนตัว / ค่าใช้จ่ายในการหารายได้
  category_code text references income_category (code),                      -- เฉพาะ income_cost: ผูกกับประเภทเงินได้ (ว่างได้)
  name          text not null check (char_length(btrim(name)) between 1 and 200),
  amount        numeric(14, 2) not null default 0 check (amount >= 0),
  note          text not null default '' check (char_length(note) <= 500),
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  check (kind = 'income_cost' or category_code is null)
);
create index expense_entry_year_month_idx on expense_entry (tax_year_id, month);

create table document_attachment (
  id               bigint generated always as identity primary key,
  user_id          uuid not null references users (id) on delete cascade,
  income_entry_id  bigint references income_entry (id) on delete cascade,
  expense_entry_id bigint references expense_entry (id) on delete cascade,
  deduction_entry_id bigint references deduction_entry (id) on delete cascade,
  doc_type         text not null default 'other' check (doc_type in ('slip', 'withholding_cert', 'receipt', 'insurance_cert', 'other')),
  file_name        text not null check (char_length(file_name) between 1 and 200),
  content_type     text not null check (content_type in ('application/pdf', 'image/jpeg', 'image/png')),
  size_bytes       integer not null check (size_bytes between 1 and 5242880),
  created_at       timestamptz not null default now(),
  check (num_nonnulls(income_entry_id, expense_entry_id, deduction_entry_id) = 1)
);
create index document_attachment_user_idx on document_attachment (user_id);
create index document_attachment_income_idx on document_attachment (income_entry_id) where income_entry_id is not null;
create index document_attachment_expense_idx on document_attachment (expense_entry_id) where expense_entry_id is not null;
create index document_attachment_deduction_idx on document_attachment (deduction_entry_id) where deduction_entry_id is not null;

create table document_content (
  attachment_id bigint primary key references document_attachment (id) on delete cascade,
  content       bytea not null
);