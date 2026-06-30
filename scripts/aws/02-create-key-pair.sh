#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/../config.sh"

info "Creating EC2 key pair: ${KEY_NAME} in ${AWS_REGION}"

# ─── If the local key file already exists, assume already done ────────────────
if [[ -f "$KEY_FILE" ]]; then
    success "Key file already exists at ${KEY_FILE}. Skipping creation."
    save_state "KEY_NAME" "$KEY_NAME"
    save_state "KEY_FILE" "$KEY_FILE"
    exit 0
fi

# ─── Delete stale remote key if present (no local file = unusable) ───────────
if aws ec2 describe-key-pairs --key-names "$KEY_NAME" --region "$AWS_REGION" &>/dev/null; then
    warn "Key pair ${KEY_NAME} exists in AWS but local file is missing. Deleting remote key."
    aws ec2 delete-key-pair --key-name "$KEY_NAME" --region "$AWS_REGION"
fi

# ─── Create key pair and save private key ─────────────────────────────────────
mkdir -p "$(dirname "$KEY_FILE")"
aws ec2 create-key-pair \
    --key-name "$KEY_NAME" \
    --key-type rsa \
    --key-format pem \
    --region "$AWS_REGION" \
    --query "KeyMaterial" \
    --output text > "$KEY_FILE"

chmod 400 "$KEY_FILE"

save_state "KEY_NAME" "$KEY_NAME"
save_state "KEY_FILE" "$KEY_FILE"

success "Key pair created and saved to ${KEY_FILE}"
echo "  Keep this file safe — it is the only way to SSH into your EC2 instance."
