#!/bin/bash
# Retries OCI ARM instance creation every 30 minutes until capacity is available.
# On success: saves INSTANCE_ID and OCI_IP to .state, then exits.
# Run: bash scripts/oracle/retry-instance.sh

export PATH="/Users/jeebanjyotiswain/Library/Python/3.9/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
STATE_FILE="$SCRIPT_DIR/.state"
source "$STATE_FILE"

IMAGE_ID=ocid1.image.oc1.iad.aaaaaaaaiwubmcqc7ol27ixzqbiyz2qzzds4vkt2rjncauty2hsjezzb7oeq
ADS=("CIUO:US-ASHBURN-AD-1" "CIUO:US-ASHBURN-AD-2" "CIUO:US-ASHBURN-AD-3")

echo "Starting OCI instance retry loop — will try every 30 minutes"
echo "Press Ctrl+C to stop"
echo ""

while true; do
  for AD in "${ADS[@]}"; do
    echo "[$(date)] Trying $AD..."

    RESULT=$(oci compute instance launch \
      --compartment-id "$TENANCY" \
      --availability-domain "$AD" \
      --display-name mail-engine \
      --image-id "$IMAGE_ID" \
      --shape VM.Standard.A1.Flex \
      --shape-config '{"ocpus":4,"memoryInGBs":24}' \
      --subnet-id "$SUBNET_ID" \
      --assign-public-ip true \
      --ssh-authorized-keys-file ~/.ssh/oci-key.pub \
      --boot-volume-size-in-gbs 50 \
      --query 'data.id' --raw-output 2>&1)

    if echo "$RESULT" | grep -q "ocid1.instance"; then
      INSTANCE_ID="$RESULT"
      echo ""
      echo "========================================="
      echo "SUCCESS! Instance created: $INSTANCE_ID"
      echo "========================================="

      echo "INSTANCE_ID=$INSTANCE_ID" >> "$STATE_FILE"
      echo "INSTANCE_AD=$AD" >> "$STATE_FILE"

      # Wait for RUNNING state
      echo "Waiting for RUNNING state (up to 10 min)..."
      oci compute instance get \
        --instance-id "$INSTANCE_ID" \
        --wait-for-state RUNNING \
        --max-wait-seconds 600 \
        --query 'data."lifecycle-state"' --raw-output 2>&1 | tail -3

      # Get public IP
      echo "Getting public IP..."
      OCI_IP=""
      for i in {1..12}; do
        OCI_IP=$(oci compute instance list-vnics \
          --instance-id "$INSTANCE_ID" \
          --query 'data[0]."public-ip"' --raw-output 2>/dev/null || true)
        [[ -n "$OCI_IP" && "$OCI_IP" != "null" ]] && break
        echo "  IP not assigned yet, waiting 10s... ($i/12)"
        sleep 10
      done

      if [[ -z "$OCI_IP" || "$OCI_IP" == "null" ]]; then
        echo "WARNING: Could not get public IP automatically."
        echo "Get it from OCI Console and add: echo 'OCI_IP=<ip>' >> $STATE_FILE"
      else
        echo "OCI_IP=$OCI_IP" >> "$STATE_FILE"
        echo ""
        echo "  Instance IP : $OCI_IP"
        echo "  SSH         : ssh -i ~/.ssh/oci-key opc@$OCI_IP"
        echo ""
        echo "Next: bash scripts/oracle/07-setup-server.sh"
      fi

      exit 0
    else
      echo "[$(date)] $AD — Out of capacity, will retry"
    fi
  done

  echo "[$(date)] All ADs exhausted. Sleeping 30 minutes..."
  sleep 1800
done
