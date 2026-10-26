-- แยกข้อมูลที่เคยเก็บเป็น JSON ก้อนเดียว (taxbook_state.state) ออกเป็นตารางที่สัมพันธ์กัน
--   users 1─1 tax_profile
--   users 1─* tax_year 1─* income_entry *─1 income_category
--   users 1─* withhold_pref *─1 income_category
-- แล้วย้ายข้อมูลเดิมเข้าตารางใหม่ ตาราง taxbook_state ยังคงอยู่ (ไม่ถูกลบ) เพื่อให้ย้อนกลับได้ แอปเลิกใช้แล้ว

-- ---------- ประเภทเงินได้ 40(1)–40(8) (ข้อมูลอ้างอิง) ----------
create table income_category (
  code           text primary key check (code ~ '^c[1-8]$'),
  section        text not null unique,            -- เช่น 40(1)
  name           text not null,
  default_method text not null                    -- วิธีหักภาษีตั้งต้น
);
insert into income_category (code, section, name, default_method) values
  ('c1', '40(1)', 'เงินเดือน ค่าจ้าง โบนัส', 'auto'),
  ('c2', '40(2)', 'ค่านายหน้า ค่าจ้างทำงานให้', '3'),
  ('c3', '40(3)', 'ค่าลิขสิทธิ์ เงินปี ค่ากู๊ดวิลล์', '3'),
  ('c4', '40(4)', 'ดอกเบี้ย เงินปันผล', '15'),
  ('c5', '40(5)', 'ค่าเช่าทรัพย์สิน', 'none'),
  ('c6', '40(6)', 'วิชาชีพอิสระ', '3'),
  ('c7', '40(7)', 'รับเหมา (จัดหาวัสดุเอง)', '3'),
  ('c8', '40(8)', 'ธุรกิจ พาณิชย์ เกษตร อุตสาหกรรม', 'none');

-- ---------- โปรไฟล์ผู้เสียภาษี (หนึ่งแถวต่อผู้ใช้) ----------
create table tax_profile (
  user_id           uuid primary key references users (id) on delete cascade,
  nick              text not null default '' check (char_length(nick) <= 80),
  marital           text not null default 'single' check (marital in ('single', 'married_noinc', 'married_inc')),
  kids              smallint not null default 0 check (kids between 0 and 50),
  kids2             smallint not null default 0 check (kids2 between 0 and 50),
  parents           smallint not null default 0 check (parents between 0 and 2),
  sp_parents        smallint not null default 0 check (sp_parents between 0 and 2),
  disabled          smallint not null default 0 check (disabled between 0 and 50),
  ss                numeric(14, 2) not null default 0 check (ss >= 0),
  life              numeric(14, 2) not null default 0 check (life >= 0),
  health            numeric(14, 2) not null default 0 check (health >= 0),
  phealth           numeric(14, 2) not null default 0 check (phealth >= 0),
  retire            numeric(14, 2) not null default 0 check (retire >= 0),
  home              numeric(14, 2) not null default 0 check (home >= 0),
  donate            numeric(14, 2) not null default 0 check (donate >= 0),
  rent_type         numeric(5, 2) not null default 30 check (rent_type between 0 and 100),
  prof_type         numeric(5, 2) not null default 30 check (prof_type between 0 and 100),
  biz_rate          numeric(5, 2) not null default 60 check (biz_rate between 0 and 100),
  excl4             boolean not null default false,
  project_remaining boolean not null default true,   -- ประมาณรายได้เดือนที่เหลือของปีปัจจุบัน
  is_sample         boolean not null default false,  -- ข้อมูลตัวอย่าง
  updated_at        timestamptz not null default now()
);

-- ---------- ปีภาษี (พ.ศ.) ของผู้ใช้ ----------
create table tax_year (
  id         bigint generated always as identity primary key,
  user_id    uuid not null references users (id) on delete cascade,
  year_be    smallint not null check (year_be between 2500 and 2700),
  created_at timestamptz not null default now(),
  unique (user_id, year_be)
);

-- ---------- รายการรายได้ (ผู้ใช้เพิ่มได้หลายรายการต่อเดือนต่อประเภท) ----------
create table income_entry (
  id            bigint generated always as identity primary key,
  tax_year_id   bigint not null references tax_year (id) on delete cascade,
  month         smallint not null check (month between 1 and 12),
  category_code text not null references income_category (code),
  amount        numeric(14, 2) not null default 0 check (amount >= 0),
  withheld      numeric(14, 2) not null default 0 check (withheld >= 0),   -- ภาษีที่ถูกหักแล้ว (กรณีระบุเอง)
  method        text not null default '' check (method ~ '^(|auto|manual|none|[0-9]{1,2})$'),
  name          text not null default '' check (char_length(name) <= 200),
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);
create index income_entry_year_month_idx on income_entry (tax_year_id, month);
create index income_entry_category_idx on income_entry (category_code);

-- ---------- วิธีหักภาษีที่ผู้ใช้เลือกล่าสุดของแต่ละประเภท ----------
create table withhold_pref (
  id            bigint generated always as identity primary key,
  user_id       uuid not null references users (id) on delete cascade,
  category_code text not null references income_category (code),
  method        text not null check (method ~ '^(auto|manual|none|[0-9]{1,2})$'),
  unique (user_id, category_code)
);

-- =====================================================================
-- ย้ายข้อมูลเดิมจาก taxbook_state.state (JSONB) เข้าตารางใหม่
-- ค่าที่ผิดรูปแบบถูกแทนด้วยค่าเริ่มต้นหรือข้ามไป จึงไม่ทำให้การย้ายล้มเหลว
-- =====================================================================
create function _tb_num(t text) returns numeric language sql immutable as $$
  select case when t ~ '^-?[0-9]{1,12}(\.[0-9]+)?$' then t::numeric else 0 end
$$;
create function _tb_clamp(t text, lo numeric, hi numeric) returns numeric language sql immutable as $$
  select least(greatest(_tb_num(t), lo), hi)
$$;

insert into tax_profile (user_id, nick, marital, kids, kids2, parents, sp_parents, disabled,
                         ss, life, health, phealth, retire, home, donate,
                         rent_type, prof_type, biz_rate, excl4, project_remaining, is_sample)
select u.id,
       left(coalesce(p->>'nick', ''), 80),
       case when p->>'marital' in ('single', 'married_noinc', 'married_inc') then p->>'marital' else 'single' end,
       _tb_clamp(p->>'kids', 0, 50), _tb_clamp(p->>'kids2', 0, 50),
       _tb_clamp(p->>'parents', 0, 2), _tb_clamp(p->>'spParents', 0, 2), _tb_clamp(p->>'disabled', 0, 50),
       _tb_clamp(p->>'ss', 0, 999999999999), _tb_clamp(p->>'life', 0, 999999999999),
       _tb_clamp(p->>'health', 0, 999999999999), _tb_clamp(p->>'phealth', 0, 999999999999),
       _tb_clamp(p->>'retire', 0, 999999999999), _tb_clamp(p->>'home', 0, 999999999999),
       _tb_clamp(p->>'donate', 0, 999999999999),
       case when p->>'rentType' is null then 30 else _tb_clamp(p->>'rentType', 0, 100) end,
       case when p->>'profType' is null then 30 else _tb_clamp(p->>'profType', 0, 100) end,
       case when p->>'bizRate' is null then 60 else _tb_clamp(p->>'bizRate', 0, 100) end,
       coalesce(p->>'excl4', 'false') = 'true',
       coalesce(st->>'project', 'true') <> 'false',
       coalesce(st->>'sample', 'false') = 'true'
from users u
left join taxbook_state s on s.user_id = u.id
cross join lateral (select coalesce(s.state, '{}'::jsonb) as st) a
cross join lateral (select case when jsonb_typeof(st->'profile') = 'object' then st->'profile' else '{}'::jsonb end as p) b;

insert into tax_year (user_id, year_be)
select distinct s.user_id, (case when y.key ~ '^[0-9]{4}$' then y.key::int end)
from taxbook_state s
cross join lateral jsonb_each(case when jsonb_typeof(s.state->'years') = 'object' then s.state->'years' else '{}'::jsonb end) y
where (case when y.key ~ '^[0-9]{4}$' then y.key::int end) between 2500 and 2700;

with raw as (
  select s.user_id, y.key as ykey, mo.key as mkey, c.key as ckey, e.val, e.ord
  from taxbook_state s
  cross join lateral jsonb_each(case when jsonb_typeof(s.state->'years') = 'object' then s.state->'years' else '{}'::jsonb end) y
  cross join lateral jsonb_each(case when jsonb_typeof(y.value->'months') = 'object' then y.value->'months' else '{}'::jsonb end) mo
  cross join lateral jsonb_each(case when jsonb_typeof(mo.value) = 'object' then mo.value else '{}'::jsonb end) c
  cross join lateral jsonb_array_elements(case when jsonb_typeof(c.value) = 'array' then c.value else '[]'::jsonb end) with ordinality as e(val, ord)
  where jsonb_typeof(e.val) = 'object'
    and c.key in (select code from income_category)
    and (case when y.key ~ '^[0-9]{4}$' then y.key::int end) between 2500 and 2700
    and (case when mo.key ~ '^[0-9]{1,2}$' then mo.key::int end) between 1 and 12
)
insert into income_entry (tax_year_id, month, category_code, amount, withheld, method, name)
select ty.id,
       (case when r.mkey ~ '^[0-9]{1,2}$' then r.mkey::int end),
       r.ckey,
       _tb_clamp(r.val->>'i', 0, 999999999999),
       _tb_clamp(r.val->>'w', 0, 999999999999),
       case when r.val->>'t' ~ '^(auto|manual|none|[0-9]{1,2})$' then r.val->>'t' else '' end,
       left(coalesce(r.val->>'n', ''), 200)
from raw r
join tax_year ty on ty.user_id = r.user_id and ty.year_be = (case when r.ykey ~ '^[0-9]{4}$' then r.ykey::int end)
order by r.user_id, r.ykey, (case when r.mkey ~ '^[0-9]{1,2}$' then r.mkey::int end), r.ckey, r.ord;

insert into withhold_pref (user_id, category_code, method)
select s.user_id, w.key, w.value
from taxbook_state s
cross join lateral jsonb_each_text(case when jsonb_typeof(s.state->'wm') = 'object' then s.state->'wm' else '{}'::jsonb end) w
where w.key in (select code from income_category)
  and w.value ~ '^(auto|manual|none|[0-9]{1,2})$';

drop function _tb_clamp(text, numeric, numeric);
drop function _tb_num(text);

comment on table taxbook_state is 'เลิกใช้ตั้งแต่ V4: ข้อมูลย้ายไปตาราง tax_profile, tax_year, income_entry, withhold_pref แล้ว เก็บไว้เพื่อย้อนกลับ';
