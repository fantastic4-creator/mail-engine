#!/usr/bin/env bash
# Deploy or redeploy the Mail Engine JAR to OCI.
# Usage: bash scripts/oracle/08-deploy-app.sh
# Run 07-setup-server.sh once before this.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
STATE_FILE="$SCRIPT_DIR/.state"
source "$STATE_FILE"

: "${OCI_IP:?OCI_IP not set in $STATE_FILE — run retry-instance.sh first}"

SSH_KEY="$HOME/.ssh/oci-key"
SSH_OPTS="-i $SSH_KEY -o StrictHostKeyChecking=no"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
BACKEND_DIR="$PROJECT_ROOT/backend"

echo "=== Deploying Mail Engine to OCI ($OCI_IP) ==="

echo "[1/3] Building JAR..."
(cd "$BACKEND_DIR" && mvn clean package -DskipTests -q)
JAR=$(find "$BACKEND_DIR/target" -maxdepth 1 -name "mail-engine-backend-*.jar" ! -name "*.original" | head -1)
[[ -z "$JAR" ]] && echo "ERROR: JAR not found after build" && exit 1
echo "  Built: $(basename "$JAR")"

echo "[2/3] Copying JAR to OCI ($OCI_IP)..."
scp $SSH_OPTS "$JAR" "opc@$OCI_IP:/opt/mail-engine/app.jar"

echo "[3/3] Restarting service..."
ssh $SSH_OPTS "opc@$OCI_IP" "sudo systemctl restart mail-engine"
sleep 10
ssh $SSH_OPTS "opc@$OCI_IP" "sudo systemctl is-active mail-engine"

echo ""
echo "=== Deploy complete ==="
echo "  Health : curl http://$OCI_IP:8080/api/health"
echo "  Logs   : ssh -i $SSH_KEY opc@$OCI_IP 'sudo journalctl -fu mail-engine'"
echo ""
echo "Next: bash scripts/oracle/09-bootstrap-tenant.sh"
