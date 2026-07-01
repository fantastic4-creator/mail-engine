#!/usr/bin/env bash
# Deploy or redeploy the Mail Engine JAR to OCI.
# Usage: bash scripts/oracle/08-deploy-app.sh
# Run 07-setup-server.sh once before this.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
STATE_FILE="$SCRIPT_DIR/.state"
source "$STATE_FILE"

: "${OCI_IP:?OCI_IP not set in $STATE_FILE — create compute instance first}"

SSH_KEY="$HOME/.ssh/oci-key"
BACKEND_DIR="$(dirname "$SCRIPT_DIR")/backend"
SSH="ssh -i $SSH_KEY -o StrictHostKeyChecking=no opc@$OCI_IP"
SCP_OPTS="-i $SSH_KEY -o StrictHostKeyChecking=no"

echo "=== Deploying Mail Engine to OCI ($OCI_IP) ==="

echo "[1/3] Building JAR..."
(cd "$BACKEND_DIR" && mvn clean package -DskipTests -q)
JAR=$(find "$BACKEND_DIR/target" -maxdepth 1 -name "mail-engine-backend-*.jar" ! -name "*.original" | head -1)
echo "  Built: $(basename "$JAR")"

echo "[2/3] Copying JAR to OCI..."
scp $SCP_OPTS "$JAR" "opc@$OCI_IP:/opt/mail-engine/app.jar"

echo "[3/3] Restarting service..."
$SSH "sudo systemctl restart mail-engine"
sleep 8
$SSH "sudo systemctl is-active mail-engine"

echo ""
echo "=== Deploy complete ==="
echo "  Health: http://$OCI_IP:8080/api/health"
echo "  Logs:   ssh -i $SSH_KEY opc@$OCI_IP 'sudo journalctl -fu mail-engine'"
echo ""
echo "Next step: bash scripts/oracle/09-bootstrap-tenant.sh"
