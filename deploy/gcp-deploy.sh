#!/usr/bin/env bash
# ติดตั้งสมุดภาษีบน Google Cloud: Cloud Run + Cloud SQL (PostgreSQL) + Secret Manager + Cloud Scheduler
# ใช้: แก้ตัวแปรด้านล่าง (หรือ export ไว้ก่อน) แล้วรัน  bash deploy/gcp-deploy.sh
# สคริปต์รันซ้ำได้ (สร้างเฉพาะสิ่งที่ยังไม่มี) ยังไม่เคยทดสอบกับโปรเจกต์จริง ตรวจทุกขั้นก่อนใช้งาน
set -euo pipefail

: "${PROJECT:?ตั้ง PROJECT=รหัสโปรเจกต์ GCP}"
REGION="${REGION:-asia-southeast1}"
SERVICE="${SERVICE:-taxbook}"
SQL_INSTANCE="${SQL_INSTANCE:-taxbook-db}"
DB_NAME="${DB_NAME:-taxbook}"
DB_USER="${DB_USER:-taxbook}"
: "${GOOGLE_CLIENT_ID:?สร้าง OAuth client (Web) แล้วตั้ง GOOGLE_CLIENT_ID}"
: "${GOOGLE_CLIENT_SECRET:?ตั้ง GOOGLE_CLIENT_SECRET}"
: "${SMTP_USER:?อีเมล Gmail ที่ใช้ส่ง เช่น you@gmail.com}"
: "${SMTP_PASSWORD:?App Password 16 หลักของ Gmail (ต้องเปิด 2-Step Verification)}"
APP_URL="${APP_URL:-}"      # ว่างได้ในรอบแรก สคริปต์จะตั้งให้จาก URL ของ Cloud Run แล้วอัปเดตอีกครั้ง

gcloud config set project "$PROJECT" >/dev/null
gcloud services enable run.googleapis.com sqladmin.googleapis.com secretmanager.googleapis.com \
  cloudscheduler.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com

# 1) Cloud SQL
if ! gcloud sql instances describe "$SQL_INSTANCE" >/dev/null 2>&1; then
  gcloud sql instances create "$SQL_INSTANCE" --database-version=POSTGRES_16 --region="$REGION" \
    --tier=db-f1-micro --storage-size=10GB --availability-type=zonal
fi
gcloud sql databases describe "$DB_NAME" --instance="$SQL_INSTANCE" >/dev/null 2>&1 || \
  gcloud sql databases create "$DB_NAME" --instance="$SQL_INSTANCE"
DB_PASSWORD="$(openssl rand -base64 24 | tr -d '/+=')"
CONN="$PROJECT:$REGION:$SQL_INSTANCE"

# 2) Secret Manager (สร้างใหม่ถ้ายังไม่มี มิฉะนั้นเพิ่มเวอร์ชัน)
put_secret() { # ชื่อ ค่า
  if gcloud secrets describe "$1" >/dev/null 2>&1; then printf %s "$2" | gcloud secrets versions add "$1" --data-file=- >/dev/null
  else printf %s "$2" | gcloud secrets create "$1" --data-file=- >/dev/null; fi
}
if ! gcloud secrets describe taxbook-db-password >/dev/null 2>&1; then
  put_secret taxbook-db-password "$DB_PASSWORD"
  if gcloud sql users list --instance="$SQL_INSTANCE" --format='value(name)' | grep -qx "$DB_USER"; then
    gcloud sql users set-password "$DB_USER" --instance="$SQL_INSTANCE" --password="$DB_PASSWORD"
  else
    gcloud sql users create "$DB_USER" --instance="$SQL_INSTANCE" --password="$DB_PASSWORD"
  fi
fi
put_secret taxbook-google-secret "$GOOGLE_CLIENT_SECRET"
put_secret taxbook-smtp-password "$SMTP_PASSWORD"
gcloud secrets describe taxbook-admin-token >/dev/null 2>&1 || put_secret taxbook-admin-token "$(openssl rand -hex 24)"

# 3) สิทธิ์ของ service account ที่ Cloud Run ใช้
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT" --format='value(projectNumber)')"
SA="${SA:-$PROJECT_NUMBER-compute@developer.gserviceaccount.com}"
for role in roles/cloudsql.client roles/secretmanager.secretAccessor; do
  gcloud projects add-iam-policy-binding "$PROJECT" --member="serviceAccount:$SA" --role="$role" --condition=None >/dev/null
done

# 4) Build + deploy จากซอร์ส (ใช้ Dockerfile ที่มีอยู่)
deploy() {
  gcloud run deploy "$SERVICE" --source . --region="$REGION" --allow-unauthenticated \
    --service-account="$SA" --add-cloudsql-instances="$CONN" \
    --memory=768Mi --cpu=1 --min-instances=0 --max-instances=3 \
    --set-env-vars="^|^DB_URL=jdbc:postgresql:///$DB_NAME?cloudSqlInstance=$CONN&socketFactory=com.google.cloud.sql.postgres.SocketFactory|DB_USER=$DB_USER|GOOGLE_CLIENT_ID=$GOOGLE_CLIENT_ID|SMTP_USER=$SMTP_USER|MAIL_FROM=$SMTP_USER|APP_URL=$APP_URL|REMINDERS_ENABLED=false|COOKIE_SECURE=true" \
    --set-secrets="DB_PASSWORD=taxbook-db-password:latest,GOOGLE_CLIENT_SECRET=taxbook-google-secret:latest,SMTP_PASSWORD=taxbook-smtp-password:latest,ADMIN_TOKEN=taxbook-admin-token:latest"
}
deploy
URL="$(gcloud run services describe "$SERVICE" --region="$REGION" --format='value(status.url)')"
if [ -z "$APP_URL" ]; then APP_URL="$URL"; deploy; fi

# 5) Cloud Scheduler เรียกงานแจ้งเตือนทุก 30 นาที (เวลาไทย) ตัวตั้งเวลาในแอปปิดไว้ด้วย REMINDERS_ENABLED=false
TOKEN="$(gcloud secrets versions access latest --secret=taxbook-admin-token)"
JOB="$SERVICE-reminders"
ARGS=(--location="$REGION" --schedule="0,30 * * * *" --time-zone="Asia/Bangkok"
      --uri="$APP_URL/admin/reminders/run" --http-method=POST
      --headers="Content-Type=application/json,X-Admin-Token=$TOKEN"
      --message-body='{"dry":false}' --attempt-deadline=300s)
if gcloud scheduler jobs describe "$JOB" --location="$REGION" >/dev/null 2>&1; then
  gcloud scheduler jobs update http "$JOB" "${ARGS[@]}"
else
  gcloud scheduler jobs create http "$JOB" "${ARGS[@]}"
fi

cat <<MSG

เสร็จแล้ว  เว็บ: $APP_URL
ขั้นตอนที่ต้องทำเองใน Google Cloud Console:
  1) APIs & Services > Credentials > OAuth client (Web) เพิ่ม Authorized redirect URI:
       $APP_URL/login/oauth2/code/google
  2) OAuth consent screen: ใส่ลิงก์ $APP_URL/privacy.html และเพิ่ม test users (ถ้ายังเป็นโหมดทดสอบ)
  3) เข้าเว็บ ล็อกอิน แล้วกด "ส่งอีเมลทดสอบถึงฉัน" ในแท็บตั้งค่าแจ้งเตือน
MSG
