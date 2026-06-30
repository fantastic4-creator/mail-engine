#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/../config.sh"
load_state

warn "This will PERMANENTLY DELETE all Mail Engine AWS resources."
echo ""
echo "  EC2 instance   : ${EC2_INSTANCE_ID:-not set}"
echo "  Elastic IP     : ${ELASTIC_IP:-not set}  (${EIP_ALLOCATION_ID:-})"
echo "  RDS instance   : ${DB_IDENTIFIER}"
echo "  Security groups: ${SG_EC2_ID:-not set}, ${SG_RDS_ID:-not set}"
echo ""
read -rp "Type 'yes' to confirm: " confirm
[[ "$confirm" != "yes" ]] && echo "Aborted." && exit 0

# ─── Terminate EC2 ────────────────────────────────────────────────────────────
if [[ -n "${EC2_INSTANCE_ID:-}" ]]; then
    info "Terminating EC2 instance ${EC2_INSTANCE_ID}..."
    aws ec2 terminate-instances --instance-ids "$EC2_INSTANCE_ID" --region "$AWS_REGION" > /dev/null
    aws ec2 wait instance-terminated --instance-ids "$EC2_INSTANCE_ID" --region "$AWS_REGION"
    success "EC2 terminated."
fi

# ─── Release Elastic IP ───────────────────────────────────────────────────────
if [[ -n "${EIP_ALLOCATION_ID:-}" ]]; then
    info "Releasing Elastic IP ${ELASTIC_IP}..."
    aws ec2 release-address --allocation-id "$EIP_ALLOCATION_ID" --region "$AWS_REGION" || warn "Elastic IP may already be released."
    success "Elastic IP released."
fi

# ─── Delete RDS instance ──────────────────────────────────────────────────────
EXISTING_RDS=$(aws rds describe-db-instances \
    --db-instance-identifier "$DB_IDENTIFIER" \
    --region "$AWS_REGION" \
    --query "DBInstances[0].DBInstanceStatus" \
    --output text 2>/dev/null || echo "notfound")

if [[ "$EXISTING_RDS" != "notfound" ]]; then
    info "Deleting RDS instance ${DB_IDENTIFIER} (no final snapshot)..."
    aws rds delete-db-instance \
        --db-instance-identifier "$DB_IDENTIFIER" \
        --skip-final-snapshot \
        --region "$AWS_REGION" > /dev/null
    info "Waiting for RDS deletion (can take 5+ minutes)..."
    aws rds wait db-instance-deleted --db-instance-identifier "$DB_IDENTIFIER" --region "$AWS_REGION"
    success "RDS deleted."
fi

# ─── Delete security groups ───────────────────────────────────────────────────
for SG_ID in "${SG_EC2_ID:-}" "${SG_RDS_ID:-}"; do
    [[ -z "$SG_ID" ]] && continue
    info "Deleting security group ${SG_ID}..."
    aws ec2 delete-security-group --group-id "$SG_ID" --region "$AWS_REGION" 2>/dev/null \
        && success "Deleted ${SG_ID}." \
        || warn "Could not delete ${SG_ID} (may have dependencies)."
done

# ─── Clear state ──────────────────────────────────────────────────────────────
rm -f "$STATE_FILE"
success "All resources deleted and .state cleared."
