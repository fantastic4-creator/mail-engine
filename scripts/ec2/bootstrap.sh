#!/usr/bin/env bash
# EC2 user-data script — runs once on first boot as root.
# Installs Java, creates the app user, and sets up the systemd service.
# The app JAR is deployed separately via 06-deploy-app.sh.
set -euo pipefail

APP_USER="mailengine"
APP_DIR="/opt/mail-engine"
APP_PORT="8080"
JAVA_PACKAGE="java-21-amazon-corretto-headless"

# ─── System update ────────────────────────────────────────────────────────────
dnf update -y --allowerasing
dnf install -y --allowerasing curl unzip zip tar which

# ─── Create app user ──────────────────────────────────────────────────────────
if ! id "$APP_USER" &>/dev/null; then
    useradd --system --shell /bin/false --home-dir "$APP_DIR" --create-home "$APP_USER"
fi

mkdir -p "${APP_DIR}/logs" "${APP_DIR}/config"
chown -R "${APP_USER}:${APP_USER}" "$APP_DIR"

# ─── Install Java 21 (Amazon Corretto — available in AL2023 repos) ───────────
dnf install -y "${JAVA_PACKAGE}"
JAVA_BIN="$(alternatives --list | grep java | grep -v javac | awk '{print $3}' | head -1)"
[[ -z "$JAVA_BIN" ]] && JAVA_BIN="/usr/bin/java"

# ─── systemd service ──────────────────────────────────────────────────────────
cat > /etc/systemd/system/mail-engine.service <<EOF
[Unit]
Description=Mail Engine Spring Boot Application
After=network.target

[Service]
User=${APP_USER}
Group=${APP_USER}
WorkingDirectory=${APP_DIR}
ExecStart=${JAVA_BIN} -jar ${APP_DIR}/app.jar
EnvironmentFile=${APP_DIR}/config/app.env
Restart=on-failure
RestartSec=10
StandardOutput=append:${APP_DIR}/logs/app.log
StandardError=append:${APP_DIR}/logs/app.log
SyslogIdentifier=mail-engine
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
EOF

# Placeholder env file — overwritten by deploy script
cat > "${APP_DIR}/config/app.env" <<EOF
MAIL_ENGINE_DELIVERY_MODE=local-outbox
MAIL_ENGINE_STORAGE_MODE=in-memory
SERVER_PORT=${APP_PORT}
EOF

chown "${APP_USER}:${APP_USER}" "${APP_DIR}/config/app.env"
chmod 600 "${APP_DIR}/config/app.env"

systemctl daemon-reload
systemctl enable mail-engine

echo "Bootstrap complete. Deploy the JAR with 06-deploy-app.sh to start the service."
