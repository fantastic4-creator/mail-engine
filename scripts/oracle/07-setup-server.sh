#!/usr/bin/env bash
# Run ONCE on a fresh OCI instance to install and configure everything.
# Works on both 1 GB (VM.Standard.E2.1.Micro) and larger ARM instances.
# Usage: bash scripts/oracle/07-setup-server.sh

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
STATE_FILE="$SCRIPT_DIR/.state"
source "$STATE_FILE"

: "${OCI_IP:?OCI_IP not set in $STATE_FILE — run retry-instance.sh first}"

SSH_KEY="$HOME/.ssh/oci-key"
SSH_OPTS="-i $SSH_KEY -o StrictHostKeyChecking=no -o ConnectTimeout=10"
SSH="ssh $SSH_OPTS opc@$OCI_IP"

echo "=== Setting up OCI instance at $OCI_IP ==="

# ── Wait for SSH ──────────────────────────────────────────────────────────────
echo "[1/8] Waiting for SSH to become available..."
for i in {1..24}; do
  ssh $SSH_OPTS opc@$OCI_IP "true" 2>/dev/null && echo "  Connected!" && break
  echo "  attempt $i/24 — waiting 15s..."
  sleep 15
done

# ── Swap (critical on 1 GB instances) ─────────────────────────────────────────
echo "[2/8] Adding 2 GB swap (needed on 1 GB RAM instance)..."
$SSH "
  if ! swapon --show | grep -q swapfile; then
    sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 status=progress
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
    echo 'Swap created and enabled'
  else
    echo 'Swap already configured'
  fi
  free -h | grep -E 'Mem|Swap'
"

# ── Java 21 via Temurin tar.gz (avoids yum OOM on 1 GB instances) ─────────────
echo "[3/8] Installing Java 21 (Eclipse Temurin JRE, tar.gz)..."
$SSH '
  JRE_DIR=/opt/java
  if [ ! -f "$JRE_DIR/bin/java" ]; then
    JRE_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.7%2B6/OpenJDK21U-jre_x64_linux_hotspot_21.0.7_6.tar.gz"
    echo "Downloading Temurin 21 JRE (~74 MB)..."
    curl -L --progress-bar -o /tmp/temurin21-jre.tar.gz "$JRE_URL"
    sudo mkdir -p "$JRE_DIR"
    sudo tar -xzf /tmp/temurin21-jre.tar.gz -C "$JRE_DIR" --strip-components=1
    rm -f /tmp/temurin21-jre.tar.gz
  fi
  /opt/java/bin/java -version
'

# ── PostgreSQL ────────────────────────────────────────────────────────────────
echo "[4/8] Installing PostgreSQL + Postfix via yum..."
$SSH "sudo yum install -y postgresql-server postgresql postfix 2>&1 | tail -5"
$SSH "sudo postgresql-setup --initdb 2>&1 || true"

# Allow TCP password auth
$SSH "sudo sed -i 's/\bident\b/md5/g; s/\bpeer\b/md5/g' /var/lib/pgsql/data/pg_hba.conf"

# Tune for 1 GB RAM (reduce defaults)
$SSH "
  PG_CONF=\$(sudo find /var/lib/pgsql -name postgresql.conf | head -1)
  echo '
# Mail Engine memory tuning for 1 GB instance
shared_buffers = 32MB
work_mem = 2MB
maintenance_work_mem = 16MB
max_connections = 10
' | sudo tee -a \"\$PG_CONF\" > /dev/null
  echo \"Tuned: \$PG_CONF\"
"

$SSH "sudo systemctl enable postgresql && sudo systemctl start postgresql"
$SSH "sudo -u postgres psql -c \"CREATE USER mailengine WITH PASSWORD 'mailengine';\" 2>/dev/null || true"
$SSH "sudo -u postgres psql -c \"CREATE DATABASE mailengine OWNER mailengine;\" 2>/dev/null || true"
echo "  Testing connection..."
$SSH "PGPASSWORD=mailengine psql -h localhost -U mailengine -d mailengine -c 'SELECT 1 AS ok;'"

# ── Postfix ───────────────────────────────────────────────────────────────────
echo "[5/8] Configuring Postfix..."
$SSH "sudo postconf -e 'myhostname = mail.consult.rissolv.com'"
$SSH "sudo postconf -e 'mydomain = consult.rissolv.com'"
$SSH "sudo postconf -e 'myorigin = consult.rissolv.com'"
$SSH "sudo postconf -e 'inet_interfaces = loopback-only'"
$SSH "sudo postconf -e 'inet_protocols = ipv4'"
$SSH "sudo postconf -e 'smtp_tls_security_level = may'"
$SSH "sudo systemctl enable postfix && sudo systemctl restart postfix"
$SSH "sudo ss -tlnp | grep ':25' && echo 'Postfix listening on :25 OK'"

# ── App directory + env file ──────────────────────────────────────────────────
echo "[6/8] Creating app directory and environment config..."
$SSH "sudo mkdir -p /opt/mail-engine && sudo chown opc:opc /opt/mail-engine"

HMAC_SECRET=$(openssl rand -hex 32)

TMP_ENV=$(mktemp)
cat > "$TMP_ENV" <<EOF
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
MAIL_ENGINE_MAX_SENDS_PER_HOUR=100
EOF

scp $SSH_OPTS "$TMP_ENV" "opc@$OCI_IP:/opt/mail-engine/app.env"
rm -f "$TMP_ENV"
$SSH "chmod 600 /opt/mail-engine/app.env"
echo "  HMAC secret: $HMAC_SECRET"
echo "UNSUBSCRIBE_HMAC_SECRET=$HMAC_SECRET" >> "$STATE_FILE"

# ── Systemd service (with JVM tuning for 1 GB) ────────────────────────────────
echo "[7/8] Creating systemd service..."
# JVM flags for 1 GB RAM:
#   -Xmx256m     cap heap at 256 MB
#   -Xms64m      start heap small
#   -XX:MaxMetaspaceSize=128m  cap class metadata
#   -XX:+UseSerialGC  single-threaded GC, lower overhead than G1GC
TMP_SVC=$(mktemp)
cat > "$TMP_SVC" <<'EOF'
[Unit]
Description=Mail Engine Spring Boot Application
After=network.target postgresql.service

[Service]
User=opc
EnvironmentFile=/opt/mail-engine/app.env
ExecStart=/opt/java/bin/java \
  -Xmx256m -Xms64m \
  -XX:MaxMetaspaceSize=128m \
  -XX:+UseSerialGC \
  -Djava.awt.headless=true \
  -jar /opt/mail-engine/app.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=mail-engine

[Install]
WantedBy=multi-user.target
EOF

scp $SSH_OPTS "$TMP_SVC" "opc@$OCI_IP:/tmp/mail-engine.service"
rm -f "$TMP_SVC"
$SSH "sudo mv /tmp/mail-engine.service /etc/systemd/system/mail-engine.service"
$SSH "sudo systemctl daemon-reload && sudo systemctl enable mail-engine"
echo "  Systemd service enabled (not started — no JAR yet)"

# ── OS-level firewall ─────────────────────────────────────────────────────────
echo "[8/8] Opening OS firewall ports..."
$SSH "sudo firewall-cmd --permanent --add-port=8080/tcp 2>/dev/null || true"
$SSH "sudo firewall-cmd --permanent --add-port=25/tcp 2>/dev/null || true"
$SSH "sudo firewall-cmd --reload 2>/dev/null || true"
$SSH "sudo firewall-cmd --list-ports 2>/dev/null || echo '(firewalld not running — OCI security list is the firewall)'"

echo ""
echo "=== Server setup complete ==="
echo "  RAM  : ~1 GB (JVM capped at 256m heap + 128m metaspace)"
echo "  Swap : 2 GB"
echo "Next: bash scripts/oracle/08-deploy-app.sh"
