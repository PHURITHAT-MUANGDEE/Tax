#!/usr/bin/env bash
# รันสมุดภาษีใน GitHub Codespace:  ./run-codespace.sh
# ต้องมีไฟล์ .env (คัดลอกจาก .env.example) ที่ใส่ GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET
set -euo pipefail
cd "$(dirname "$0")"

[ -f .env ] || { echo "ไม่พบ .env  ทำ: cp .env.example .env แล้วแก้ค่า"; exit 1; }

# อ่านเฉพาะบรรทัด KEY=VALUE (ไม่ source ทั้งไฟล์ กันค่าที่มีเว้นวรรคทำให้พัง)
while IFS='=' read -r k v; do
  case "$k" in ''|\#*) continue ;; esac
  export "$k=$v"
done < .env

: "${DB_PASSWORD:=taxbook}"; export DB_PASSWORD
export DB_URL="jdbc:postgresql://localhost:5432/taxbook" DB_USER="taxbook"

if [ -z "${GOOGLE_CLIENT_ID:-}" ] || [[ "$GOOGLE_CLIENT_ID" == xxxx* ]]; then
  echo "ใส่ GOOGLE_CLIENT_ID และ GOOGLE_CLIENT_SECRET จริงใน .env ก่อน"; exit 1
fi

# ที่อยู่เว็บใน Codespace (พอร์ต 8080 ถูกส่งต่อเป็น https)
if [ -n "${CODESPACE_NAME:-}" ]; then
  export APP_URL="https://${CODESPACE_NAME}-8080.${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:-app.github.dev}"
  export COOKIE_SECURE=true
else
  export APP_URL="${APP_URL:-http://localhost:8080}"
  export COOKIE_SECURE=false
fi

echo "==> เริ่มฐานข้อมูล PostgreSQL"
docker compose -f docker-compose.dev.yml up -d db
until [ "$(docker inspect -f '{{.State.Health.Status}}' "$(docker compose -f docker-compose.dev.yml ps -q db)")" = healthy ]; do sleep 2; done

echo
echo "เว็บ:           $APP_URL"
echo "Redirect URI ที่ต้องเพิ่มใน Google Cloud Console (ครั้งแรก):"
echo "                $APP_URL/login/oauth2/code/google"
echo

exec mvn -q spring-boot:run
