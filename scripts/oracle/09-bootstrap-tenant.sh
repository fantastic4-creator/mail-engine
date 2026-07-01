#!/usr/bin/env bash
# Bootstrap the OCI Mail Engine instance: tenant, API key, domain, IP pool.
# Usage: bash scripts/oracle/09-bootstrap-tenant.sh
# Run after 08-deploy-app.sh and confirming health endpoint returns UP.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
STATE_FILE="$SCRIPT_DIR/.state"
source "$STATE_FILE"

: "${OCI_IP:?OCI_IP not set in $STATE_FILE}"

BASE="http://$OCI_IP:8080"

echo "=== Bootstrapping Mail Engine on OCI ($BASE) ==="

# ── Health check ──────────────────────────────────────────────────────────────
echo "[0/5] Waiting for app to be healthy..."
for i in {1..20}; do
  STATUS=$(curl -sf "$BASE/api/health" 2>/dev/null | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || true)
  [[ "$STATUS" == "UP" ]] && echo "  UP" && break
  echo "  attempt $i/20 — waiting 10s..."
  sleep 10
done
[[ "$STATUS" != "UP" ]] && echo "ERROR: App not healthy after 200s" && exit 1

# ── Tenant ────────────────────────────────────────────────────────────────────
echo "[1/5] Creating tenant..."
TENANT_RESP=$(curl -sf -X POST "$BASE/api/tenants" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Rissolv"}')
echo "  $TENANT_RESP"
TENANT_ID=$(echo "$TENANT_RESP" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "  tenantId: $TENANT_ID"

# ── API Key ───────────────────────────────────────────────────────────────────
echo "[2/5] Creating API key..."
KEY_RESP=$(curl -sf -X POST "$BASE/api/tenants/$TENANT_ID/api-keys" \
  -H 'Content-Type: application/json' \
  -d '{"name":"admin"}')
echo "  $KEY_RESP"
API_KEY=$(echo "$KEY_RESP" | grep -o '"rawKey":"[^"]*"' | cut -d'"' -f4)
echo "  apiKey: $API_KEY"

# ── Sending domain ────────────────────────────────────────────────────────────
echo "[3/5] Adding sending domain (consult.rissolv.com)..."
DOMAIN_RESP=$(curl -sf -X POST "$BASE/api/tenants/$TENANT_ID/domains" \
  -H 'Content-Type: application/json' \
  -H "X-API-Key: $API_KEY" \
  -d '{"domainName":"consult.rissolv.com"}')
echo "  $DOMAIN_RESP"
DOMAIN_ID=$(echo "$DOMAIN_RESP" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "  domainId: $DOMAIN_ID"

# ── DNS records ───────────────────────────────────────────────────────────────
echo "[4/5] Fetching DNS records to add at Hostinger..."
curl -sf "$BASE/api/tenants/$TENANT_ID/domains/$DOMAIN_ID/dns-records" \
  -H "X-API-Key: $API_KEY" | python3 -m json.tool 2>/dev/null || \
curl -sf "$BASE/api/tenants/$TENANT_ID/domains/$DOMAIN_ID/dns-records" \
  -H "X-API-Key: $API_KEY"
echo ""

# ── IP pool ───────────────────────────────────────────────────────────────────
echo "[5/5] Creating IP pool and registering OCI public IP..."
POOL_RESP=$(curl -sf -X POST "$BASE/api/tenants/$TENANT_ID/ip-pools" \
  -H 'Content-Type: application/json' \
  -H "X-API-Key: $API_KEY" \
  -d '{"name":"default","trafficType":"bulk"}')
echo "  $POOL_RESP"
POOL_ID=$(echo "$POOL_RESP" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

curl -sf -X POST "$BASE/api/tenants/$TENANT_ID/ip-pools/$POOL_ID/ips" \
  -H 'Content-Type: application/json' \
  -H "X-API-Key: $API_KEY" \
  -d "{\"publicIpAddress\":\"$OCI_IP\",\"elasticAllocationId\":\"oci-reserved-ip\",\"reverseDnsName\":\"mail.consult.rissolv.com\"}"
echo ""

# ── Save to state ─────────────────────────────────────────────────────────────
cat >> "$STATE_FILE" <<EOF
OCI_TENANT_ID=$TENANT_ID
OCI_DOMAIN_ID=$DOMAIN_ID
OCI_POOL_ID=$POOL_ID
OCI_API_KEY=$API_KEY
EOF

echo ""
echo "=== Bootstrap complete ==="
echo ""
echo "  Tenant ID : $TENANT_ID"
echo "  Domain ID : $DOMAIN_ID"
echo "  Pool ID   : $POOL_ID"
echo "  API Key   : $API_KEY"
echo ""
echo "IMPORTANT — Add DNS records at Hostinger (shown above):"
echo "  1. me2._domainkey TXT  (DKIM — new selector for OCI)"
echo "  2. Update SPF to include OCI IP: v=spf1 ip4:3.208.157.146 ip4:$OCI_IP ~all"
echo ""
echo "After DNS propagates, verify domain:"
echo "  curl -X POST $BASE/api/tenants/$TENANT_ID/domains/$DOMAIN_ID/verify \\"
echo "    -H 'X-API-Key: $API_KEY'"
echo ""
echo "Send test email:"
echo "  curl -X POST $BASE/api/campaigns \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -H 'X-API-Key: $API_KEY' \\"
echo "    -d '{\"tenantId\":\"$TENANT_ID\",\"domainId\":\"$DOMAIN_ID\",\"name\":\"Test\",\"subject\":\"Hello from OCI\",\"body\":\"<p>OCI works!</p>\",\"recipientEmail\":\"jeebanjyoti.oec@gmail.com\"}'"
