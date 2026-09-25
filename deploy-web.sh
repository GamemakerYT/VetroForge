#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
WEB_DIR="${ROOT}/web-dashboard"
REMOTE="${VF_WEB_REMOTE:-root@91.218.66.190}"
SSH_KEY="${VF_WEB_SSH_KEY:-$HOME/.ssh/vetroforge_codex_ed25519}"
REMOTE_DIR="${VF_WEB_DIR:-/opt/vetroforge/web-dashboard}"
SERVICE="${VF_WEB_SERVICE:-vetroforge-web}"
API_URL="${VF_FACTIONS_API_URL:-http://127.0.0.1:8500}"
PUBLIC_API_URL="${NEXT_PUBLIC_VETROFORGE_API:-https://vetroforge.net/api/factions}"
SITE_URL="${NEXT_PUBLIC_SITE_URL:-https://vetroforge.net}"
INSTALL_NGINX_PROXY="${VF_WEB_INSTALL_NGINX_PROXY:-0}"
NGINX_SITE="${VF_WEB_NGINX_SITE:-/etc/nginx/sites-available/vetroforge.net}"

if [[ ! -d "$WEB_DIR" ]]; then
  echo "[web-deploy] Fehler: web-dashboard fehlt unter $WEB_DIR" >&2
  exit 1
fi

echo "[web-deploy] Remote vorbereiten: ${REMOTE}:${REMOTE_DIR}"
ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no "$REMOTE" "mkdir -p '$REMOTE_DIR'"

echo "[web-deploy] Dateien synchronisieren"
tar \
  --exclude='node_modules' \
  --exclude='.next' \
  --exclude='.turbo' \
  --exclude='.env.local' \
  -C "$WEB_DIR" -czf - . \
  | ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no "$REMOTE" "tar -xzf - -C '$REMOTE_DIR'"

echo "[web-deploy] .env.production schreiben"
ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no "$REMOTE" "cat > '$REMOTE_DIR/.env.production' <<EOF
VF_FACTIONS_API_URL=${API_URL}
NEXT_PUBLIC_VETROFORGE_API=${PUBLIC_API_URL}
NEXT_PUBLIC_SITE_URL=${SITE_URL}
EOF"

echo "[web-deploy] npm install + build"
ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no "$REMOTE" "set -euo pipefail
cd '$REMOTE_DIR'
npm install
npm run build
"

echo "[web-deploy] PM2 Service starten/neu laden: ${SERVICE}"
ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no "$REMOTE" "set -euo pipefail
cd '$REMOTE_DIR'
if pm2 describe '$SERVICE' >/dev/null 2>&1; then
  pm2 reload '$SERVICE' --update-env
else
  pm2 start npm --name '$SERVICE' -- start
fi
pm2 save
"

if [[ "$INSTALL_NGINX_PROXY" == "1" ]]; then
  echo "[web-deploy] Nginx Proxy /api/factions installieren"
  ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no "$REMOTE" "set -euo pipefail
python3 - <<'PY'
from pathlib import Path
site = Path('$NGINX_SITE')
text = site.read_text()
block = '''
    location /api/factions/ {
        proxy_pass http://127.0.0.1:8500/;
        proxy_http_version 1.1;
        proxy_set_header Host \\$host;
        proxy_set_header X-Real-IP \\$remote_addr;
        proxy_set_header X-Forwarded-For \\$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \\$scheme;
    }

    location = /api/factions {
        return 301 /api/factions/;
    }
'''
parts = text.split('server {')
rebuilt = [parts[0]]
for part in parts[1:]:
    server = 'server {' + part
    if 'listen 443' in server and 'location /api/factions/' not in server:
        server = server.replace('    location / {', block + '\\n    location / {', 1)
    elif 'listen 443' not in server and 'location /api/factions/' not in server and 'return 301 https://' in server:
        server = server.replace('    location / {', block + '\\n    location / {', 1)
    rebuilt.append(server)
site.write_text(''.join(rebuilt))
PY
nginx -t
systemctl reload nginx
"
fi

echo "[web-deploy] Fertig. Logs:"
echo "ssh -i ${SSH_KEY} ${REMOTE} \"pm2 logs ${SERVICE} --lines 80\""
