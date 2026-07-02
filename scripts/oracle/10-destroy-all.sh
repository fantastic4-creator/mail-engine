#!/usr/bin/env bash
# Destroy ALL OCI Mail Engine resources in dependency order.
# Safe to run even if some resources were already deleted.
# IRREVERSIBLE — prompts for confirmation.

set -euo pipefail
export PATH="/Users/jeebanjyotiswain/Library/Python/3.9/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
STATE_FILE="$SCRIPT_DIR/.state"
source "$STATE_FILE"

echo "=== OCI Mail Engine — DESTROY ALL ==="
echo ""
echo "Resources to delete:"
[[ -n "${INSTANCE_ID:-}" ]] && echo "  Compute instance : $INSTANCE_ID (IP: ${OCI_IP:-unknown})"
[[ -n "${SUBNET_ID:-}" ]]   && echo "  Subnet           : $SUBNET_ID"
[[ -n "${RT_ID:-}" ]]       && echo "  Route table      : $RT_ID"
[[ -n "${SL_ID:-}" ]]       && echo "  Security list    : $SL_ID"
[[ -n "${IGW_ID:-}" ]]      && echo "  Internet gateway : $IGW_ID"
[[ -n "${VCN_ID:-}" ]]      && echo "  VCN              : $VCN_ID"
echo ""
echo "This is IRREVERSIBLE. Press Ctrl+C within 10 seconds to cancel."
sleep 10

# ── 1. Terminate compute instance ─────────────────────────────────────────────
if [[ -n "${INSTANCE_ID:-}" ]]; then
  echo "[1/6] Terminating compute instance $INSTANCE_ID..."
  oci compute instance terminate \
    --instance-id "$INSTANCE_ID" \
    --preserve-boot-volume false \
    --force 2>/dev/null || echo "  (already gone or error — continuing)"

  echo "  Waiting for TERMINATED state (up to 10 min)..."
  oci compute instance get \
    --instance-id "$INSTANCE_ID" \
    --wait-for-state TERMINATED \
    --max-wait-seconds 600 2>/dev/null | tail -1 || echo "  (timeout or already terminated)"
  echo "  Instance terminated."
else
  echo "[1/6] No INSTANCE_ID — skipping instance termination"
fi

sleep 5

# ── 2. Delete subnet ──────────────────────────────────────────────────────────
if [[ -n "${SUBNET_ID:-}" ]]; then
  echo "[2/6] Deleting subnet $SUBNET_ID..."
  oci network subnet delete --subnet-id "$SUBNET_ID" --force 2>/dev/null || echo "  (already gone)"
else
  echo "[2/6] No SUBNET_ID — skipping"
fi

# ── 3. Delete custom route table ──────────────────────────────────────────────
if [[ -n "${RT_ID:-}" ]]; then
  echo "[3/6] Deleting route table $RT_ID..."
  oci network route-table delete --rt-id "$RT_ID" --force 2>/dev/null || echo "  (already gone)"
else
  echo "[3/6] No RT_ID — skipping"
fi

# ── 4. Delete custom security list ────────────────────────────────────────────
if [[ -n "${SL_ID:-}" ]]; then
  echo "[4/6] Deleting security list $SL_ID..."
  oci network security-list delete --security-list-id "$SL_ID" --force 2>/dev/null || echo "  (already gone)"
else
  echo "[4/6] No SL_ID — skipping"
fi

# ── 5. Delete internet gateway ────────────────────────────────────────────────
if [[ -n "${IGW_ID:-}" ]]; then
  echo "[5/6] Deleting internet gateway $IGW_ID..."
  oci network internet-gateway delete --ig-id "$IGW_ID" --force 2>/dev/null || echo "  (already gone)"
else
  echo "[5/6] No IGW_ID — skipping"
fi

# ── 6. Delete VCN ─────────────────────────────────────────────────────────────
if [[ -n "${VCN_ID:-}" ]]; then
  echo "[6/6] Deleting VCN $VCN_ID..."
  oci network vcn delete --vcn-id "$VCN_ID" --force 2>/dev/null || echo "  (already gone or still has dependents)"
else
  echo "[6/6] No VCN_ID — skipping"
fi

echo ""
echo "=== All OCI resources destroyed ==="
echo ""
echo "To redeploy from scratch:"
echo "  1. bash scripts/oracle/retry-instance.sh   (creates instance + saves OCI_IP)"
echo "  2. bash scripts/oracle/07-setup-server.sh  (installs Java/Postgres/Postfix)"
echo "  3. bash scripts/oracle/08-deploy-app.sh    (builds and deploys JAR)"
echo "  4. bash scripts/oracle/09-bootstrap-tenant.sh"
