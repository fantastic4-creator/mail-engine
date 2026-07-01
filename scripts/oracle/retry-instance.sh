#!/bin/bash
# Retries OCI ARM instance creation every 30 minutes until capacity is available
# Run: bash scripts/oracle/retry-instance.sh

export PATH="/Users/jeebanjyotiswain/Library/Python/3.9/bin:$PATH"
STATE_FILE="$(dirname "$0")/.state"
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
      --compartment-id $TENANCY \
      --availability-domain "$AD" \
      --display-name mail-engine \
      --image-id $IMAGE_ID \
      --shape VM.Standard.A1.Flex \
      --shape-config '{"ocpus":2,"memoryInGBs":12}' \
      --subnet-id $SUBNET_ID \
      --assign-public-ip true \
      --ssh-authorized-keys-file ~/.ssh/oci-key.pub \
      --boot-volume-size-in-gbs 50 \
      --query 'data.id' --raw-output 2>&1)

    if echo "$RESULT" | grep -q "ocid1.instance"; then
      echo ""
      echo "========================================="
      echo "SUCCESS! Instance created: $RESULT"
      echo "========================================="
      echo "INSTANCE_ID=$RESULT" >> "$STATE_FILE"
      echo "INSTANCE_AD=$AD" >> "$STATE_FILE"
      exit 0
    else
      echo "[$(date)] $AD — Out of capacity, will retry in 30 minutes"
    fi
  done

  echo "[$(date)] All ADs exhausted. Sleeping 30 minutes..."
  sleep 1800
done
