#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/../config.sh"
load_state

require_state "ELASTIC_IP"
require_state "KEY_FILE"
require_state "RDS_ENDPOINT"
require_state "DB_PASSWORD"

# Set DELIVERY_MODE=smtp once Postfix is installed and a sending domain is configured.
DELIVERY_MODE="${DELIVERY_MODE:-local-outbox}"

BACKEND_DIR="$(dirname "$0")/../../backend"
JAR_PATTERN="${BACKEND_DIR}/target/mail-engine-backend-*.jar"

# ─── Build ────────────────────────────────────────────────────────────────────
info "Building fat JAR..."
(cd "$BACKEND_DIR" && mvn clean package -DskipTests -q)

JAR_PATH=$(find "$BACKEND_DIR/target" -maxdepth 1 -name "mail-engine-backend-*.jar" ! -name "*.original" 2>/dev/null | head -1)
[[ -z "$JAR_PATH" ]] && err "JAR not found after build. Check Maven output."
info "JAR: $(basename "$JAR_PATH")"

# ─── Copy JAR to EC2 ─────────────────────────────────────────────────────────
info "Copying JAR to EC2 (${ELASTIC_IP})..."
SSH_OPTS="-i ${KEY_FILE} -o StrictHostKeyChecking=no -o ConnectTimeout=15"

# Wait until SSH is responsive
for i in {1..12}; do
    ssh $SSH_OPTS "ec2-user@${ELASTIC_IP}" "true" 2>/dev/null && break
    warn "SSH not ready yet (attempt ${i}/12). Waiting 10s..."
    sleep 10
done

ssh $SSH_OPTS "ec2-user@${ELASTIC_IP}" "sudo mkdir -p ${APP_DIR} && sudo chown ec2-user:ec2-user ${APP_DIR}"
scp $SSH_OPTS "$JAR_PATH" "ec2-user@${ELASTIC_IP}:${APP_DIR}/app.jar"
ssh $SSH_OPTS "ec2-user@${ELASTIC_IP}" "sudo chown ${APP_USER}:${APP_USER} ${APP_DIR}/app.jar"

# ─── Write env config on EC2 ─────────────────────────────────────────────────
info "Writing app environment config..."
DB_URL="jdbc:postgresql://${RDS_ENDPOINT}:5432/${DB_NAME}"

ssh $SSH_OPTS "ec2-user@${ELASTIC_IP}" "sudo tee ${APP_DIR}/config/app.env > /dev/null" <<EOF
# Mail Engine runtime config — do not commit this file
SERVER_PORT=${APP_PORT}

MAIL_ENGINE_DELIVERY_MODE=${DELIVERY_MODE}
MAIL_ENGINE_STORAGE_MODE=postgres
MAIL_ENGINE_SMTP_HOST=localhost
MAIL_ENGINE_SMTP_PORT=25
MAIL_ENGINE_SMTP_AUTH_ENABLED=false
MAIL_ENGINE_SMTP_STARTTLS_ENABLED=false

SPRING_DATASOURCE_URL=${DB_URL}
SPRING_DATASOURCE_USERNAME=${DB_USERNAME}
SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
EOF

ssh $SSH_OPTS "ec2-user@${ELASTIC_IP}" "sudo chmod 600 ${APP_DIR}/config/app.env && sudo chown ${APP_USER}:${APP_USER} ${APP_DIR}/config/app.env"

# ─── Restart service ─────────────────────────────────────────────────────────
info "Restarting mail-engine service..."
ssh $SSH_OPTS "ec2-user@${ELASTIC_IP}" "sudo systemctl restart mail-engine"

sleep 3
ssh $SSH_OPTS "ec2-user@${ELASTIC_IP}" "sudo systemctl status mail-engine --no-pager -l"

success "Deployment complete."
echo ""
echo "  API health  : http://${ELASTIC_IP}:${APP_PORT}/actuator/health"
echo "  Tail logs   : ssh -i ${KEY_FILE} ec2-user@${ELASTIC_IP} 'sudo journalctl -fu mail-engine'"
