-- V6: แยกประเภทประกันให้ละเอียดขึ้น
--   ss (ประกันสังคม) แยกเป็น ss33 (มาตรา 33) / ss39 (มาตรา 39) / ss40 (มาตรา 40)
--   เพิ่ม slife (เบี้ยประกันชีวิตคู่สมรสที่ไม่มีเงินได้)
-- แถวเดิมที่เป็น ss ถูกย้ายเป็น ss33 (ผู้ที่เป็นลูกจ้างบริษัทส่วนใหญ่อยู่ มาตรา 33)

alter table deduction_entry drop constraint deduction_entry_kind_check;
update deduction_entry set kind = 'ss33' where kind = 'ss';
alter table deduction_entry add constraint deduction_entry_kind_check
  check (kind in ('ss33', 'ss39', 'ss40', 'life', 'health', 'phealth', 'slife', 'home', 'fund'));