#!/usr/bin/env bash
# EC2 user-data script — runs once on first boot as root.
# Installs Java, creates the app user, and sets up the systemd service.
# The app JAR is deployed separately via 06-deploy-app.sh.
set -euo pipefail

APP_USER="mailengine"
APP_DIR="/opt/mail-engine"
APP_PORT="8080"
JAVA_VERSION="25"
SDKMAN_DIR="/opt/sdkman"

# ─── System update ────────────────────────────────────────────────────────────
dnf update -y
dnf install -y curl unzip zip tar which

# ─── Create app user ──────────────────────────────────────────────────────────
if ! id "$APP_USER" &>/dev/null; then
    useradd --system --shell /bin/false --home-dir "$APP_DIR" --create-home "$APP_USER"
fi

mkdir -p "${APP_DIR}/logs" "${APP_DIR}/config"
chown -R "${APP_USER}:${APP_USER}" "$APP_DIR"

# ─── Install Java via SDKMAN (system-wide) ───────────────────────────────────
# SDKMAN is installed system-wide under /opt/sdkman so all users can use it.
export SDKMAN_DIR
curl -fsSL "https://get.sdkman.io" | bash
source "${SDKMAN_DIR}/bin/sdkman-init.sh"

# Install the latest Temurin build for Java 25 (or latest available)
sdk install java "${JAVA_VERSION}-tem" || sdk install java "$(sdk list java | grep 'tem' | head -1 | awk '{print $NF}')"
sdk default java "$(sdk list java | grep 'installed' | grep 'tem' | head -1 | awk '{print $NF}')"

JAVA_BIN="$(sdk home java current)/bin/java"

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
