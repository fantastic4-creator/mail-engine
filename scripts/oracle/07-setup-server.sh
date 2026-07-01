#!/usr/bin/env bash
# Run ONCE on a fresh OCI instance to install and configure everything.
# Usage: bash scripts/oracle/07-setup-server.sh
# Pre-requisite: compute instance must exist and OCI_IP must be set in .state

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
STATE_FILE="$SCRIPT_DIR/.state"
source "$STATE_FILE"

: "${OCI_IP:?OCI_IP not set in $STATE_FILE — run instance creation first}"

SSH_KEY="$HOME/.ssh/oci-key"
SSH="ssh -i $SSH_KEY -o StrictHostKeyChecking=no opc@$OCI_IP"
SCP="scp -i $SSH_KEY -o StrictHostKeyChecking=no"

echo "=== Setting up OCI instance at $OCI_IP ==="

# ── Wait for SSH ──────────────────────────────────────────────────────────────
echo "[1/7] Waiting for SSH..."
for i in {1..24}; do
  $SSH "true" 2>/dev/null && break
  echo "  attempt $i/24 — waiting 15s..."
  sleep 15
done

# ── Java 21 ───────────────────────────────────────────────────────────────────
echo "[2/7] Installing Java 21..."
$SSH "sudo yum install -y java-21-openjdk-headless 2>&1 | tail -3"
$SSH "java -version"

# ── PostgreSQL ────────────────────────────────────────────────────────────────
echo "[3/7] Installing PostgreSQL..."
$SSH "sudo yum install -y postgresql-server postgresql 2>&1 | tail -3"
$SSH "sudo postgresql-setup --initdb 2>&1 || true"
$SSH "sudo sed -i 's/ident/md5/g' /var/lib/pgsql/data/pg_hba.conf"
$SSH "sudo systemctl enable postgresql && sudo systemctl start postgresql"
$SSH "sudo -u postgres psql -c \"CREATE USER mailengine WITH PASSWORD 'mailengine';\" 2>/dev/null || true"
$SSH "sudo -u postgres psql -c \"CREATE DATABASE mailengine OWNER mailengine;\" 2>/dev/null || true"
$SSH "psql -h localhost -U mailengine -d mailengine -c 'SELECT 1 AS ok;'"

# ── Postfix ───────────────────────────────────────────────────────────────────
echo "[4/7] Installing and configuring Postfix..."
$SSH "sudo yum install -y postfix 2>&1 | tail -3"
$SSH "sudo postconf -e 'myhostname = mail.consult.rissolv.com'"
$SSH "sudo postconf -e 'mydomain = consult.rissolv.com'"
$SSH "sudo postconf -e 'myorigin = consult.rissolv.com'"
$SSH "sudo postconf -e 'inet_interfaces = loopback-only'"
$SSH "sudo postconf -e 'inet_protocols = ipv4'"
$SSH "sudo postconf -e 'smtp_tls_security_level = may'"
$SSH "sudo systemctl enable postfix && sudo systemctl restart postfix"
$SSH "sudo ss -tlnp | grep ':25'"

# ── App directory + env file ──────────────────────────────────────────────────
echo "[5/7] Creating app directory and environment config..."
HMAC_SECRET=$(openssl rand -hex 32)
$SSH "sudo mkdir -p /opt/mail-engine && sudo chown opc:opc /opt/mail-engine"

$SSH "cat > /opt/mail-engine/app.env <<'ENVEOF'
MAIL_ENGINE_DELIVERY_MODE=smtp
MAIL_ENGINE_STORAGE_MODE=postgres
MAIL_ENGINE_SMTP_HOST=localhost
MAIL_ENGINE_SMTP_PORT=25
MAIL_ENGINE_SMTP_AUTH_ENABLED=false
MAIL_ENGINE_SMTP_STARTTLS_ENABLED=false
MAIL_ENGINE_APP_BASE_URL=http://${OCI_IP}:8080
MAIL_ENGINE_UNSUBSCRIBE_HMAC_SECRET=${HMAC_SECRET}
MAIL_ENGINE_DKIM_SELECTOR=me2
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mailengine
SPRING_DATASOURCE_USERNAME=mailengine
SPRING_DATASOURCE_PASSWORD=mailengine
MAIL_ENGINE_MAX_SENDS_PER_HOUR=50
ENVEOF"

$SSH "chmod 600 /opt/mail-engine/app.env"

echo "  HMAC secret: $HMAC_SECRET"
echo "UNSUBSCRIBE_HMAC_SECRET=$HMAC_SECRET" >> "$STATE_FILE"

# ── Systemd service ───────────────────────────────────────────────────────────
echo "[6/7] Creating systemd service..."
$SSH "sudo tee /etc/systemd/system/mail-engine.service > /dev/null <<'EOF'
[Unit]
Description=Mail Engine Spring Boot Application
After=network.target postgresql.service

[Service]
User=opc
EnvironmentFile=/opt/mail-engine/app.env
ExecStart=/usr/bin/java -jar /opt/mail-engine/app.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=mail-engine

[Install]
WantedBy=multi-user.target
EOF"
$SSH "sudo systemctl daemon-reload && sudo systemctl enable mail-engine"

# ── OS-level firewall ─────────────────────────────────────────────────────────
echo "[7/7] Opening OS firewall ports..."
$SSH "sudo firewall-cmd --permanent --add-port=8080/tcp 2>/dev/null || true"
$SSH "sudo firewall-cmd --permanent --add-port=25/tcp 2>/dev/null || true"
$SSH "sudo firewall-cmd --reload 2>/dev/null || true"

echo ""
echo "=== Server setup complete ==="
echo "Next step: bash scripts/oracle/08-deploy-app.sh"
