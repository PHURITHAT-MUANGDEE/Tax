#!/usr/bin/env bash
# รันเฉพาะเทสต์ของโค้ดที่ไม่พึ่ง Spring (ตรรกะเตือน เทมเพลตอีเมล ตัวตรวจค่าตั้งค่า) โดยไม่ต้องใช้ Maven หรืออินเทอร์เน็ต
# ใช้ตัวจำลอง JUnit ขนาดเล็กใน tests/junit-shim แทน (เทสต์ไฟล์เดียวกับที่ `mvn test` ใช้)
# ต้องมี JDK 21 ขึ้นไป
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
javac -encoding UTF-8 -d "$OUT" $(find "$HERE/tests/junit-shim" -name '*.java') \
  "$HERE"/src/main/java/th/taxbook/reminder/*.java "$HERE"/src/main/java/th/taxbook/settings/*.java "$HERE"/src/main/java/th/taxbook/validation/*.java \
  "$HERE"/src/test/java/th/taxbook/reminder/*.java "$HERE"/src/test/java/th/taxbook/settings/*.java "$HERE"/src/test/java/th/taxbook/validation/*.java 2>&1 | grep -v '^Picked up' || true
fail=0
for c in th.taxbook.reminder.ReminderLogicTest th.taxbook.settings.SettingsValidatorTest th.taxbook.validation.DataValidatorTest; do
  echo "== $c"; java -cp "$OUT" Runner "$c" 2>&1 | grep -v '^Picked up' || fail=1
done
exit $fail
