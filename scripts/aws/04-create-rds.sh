#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/../config.sh"
load_state

require_state "SG_RDS_ID"
require_state "VPC_ID"

info "Creating RDS Postgres instance (free tier) in ${AWS_REGION}"

# ─── Check if already exists ──────────────────────────────────────────────────
EXISTING=$(aws rds describe-db-instances \
    --db-instance-identifier "$DB_IDENTIFIER" \
    --region "$AWS_REGION" \
    --query "DBInstances[0].DBInstanceStatus" \
    --output text 2>/dev/null || echo "notfound")

if [[ "$EXISTING" != "notfound" ]]; then
    info "RDS instance ${DB_IDENTIFIER} already exists (status: ${EXISTING})."
    RDS_ENDPOINT=$(aws rds describe-db-instances \
        --db-instance-identifier "$DB_IDENTIFIER" \
        --region "$AWS_REGION" \
        --query "DBInstances[0].Endpoint.Address" \
        --output text)
    save_state "RDS_ENDPOINT" "$RDS_ENDPOINT"
    success "RDS endpoint: ${RDS_ENDPOINT}"
    exit 0
fi

# ─── Generate DB password ─────────────────────────────────────────────────────
DB_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)
save_state "DB_PASSWORD" "$DB_PASSWORD"
info "Generated DB password and saved to .state"

# ─── Create DB subnet group from default VPC subnets ─────────────────────────
SUBNET_GROUP_NAME="${APP_NAME}-subnet-group"
SUBNET_IDS=$(aws ec2 describe-subnets \
    --filters "Name=vpc-id,Values=${VPC_ID}" \
    --region "$AWS_REGION" \
    --query "Subnets[].SubnetId" \
    --output text | tr '\t' ' ')

EXISTING_SUBNET_GROUP=$(aws rds describe-db-subnet-groups \
    --db-subnet-group-name "$SUBNET_GROUP_NAME" \
    --region "$AWS_REGION" \
    --query "DBSubnetGroups[0].DBSubnetGroupName" \
    --output text 2>/dev/null || echo "notfound")

if [[ "$EXISTING_SUBNET_GROUP" == "notfound" ]]; then
    # shellcheck disable=SC2086
    aws rds create-db-subnet-group \
        --db-subnet-group-name "$SUBNET_GROUP_NAME" \
        --db-subnet-group-description "Mail Engine subnet group" \
        --subnet-ids $SUBNET_IDS \
        --region "$AWS_REGION" > /dev/null
    info "Created DB subnet group: ${SUBNET_GROUP_NAME}"
fi

# ─── Create RDS instance ──────────────────────────────────────────────────────
info "Creating RDS instance — this takes 5–10 minutes..."

aws rds create-db-instance \
    --db-instance-identifier "$DB_IDENTIFIER" \
    --db-instance-class "$RDS_INSTANCE_TYPE" \
    --engine "$RDS_ENGINE" \
    --engine-version "$RDS_ENGINE_VERSION" \
    --master-username "$DB_USERNAME" \
    --master-user-password "$DB_PASSWORD" \
    --db-name "$DB_NAME" \
    --allocated-storage 20 \
    --storage-type gp2 \
    --no-multi-az \
    --no-publicly-accessible \
    --vpc-security-group-ids "$SG_RDS_ID" \
    --db-subnet-group-name "$SUBNET_GROUP_NAME" \
    --backup-retention-period 1 \
    --no-deletion-protection \
    --region "$AWS_REGION" > /dev/null

info "Waiting for RDS instance to become available..."
aws rds wait db-instance-available \
    --db-instance-identifier "$DB_IDENTIFIER" \
    --region "$AWS_REGION"

RDS_ENDPOINT=$(aws rds describe-db-instances \
    --db-instance-identifier "$DB_IDENTIFIER" \
    --region "$AWS_REGION" \
    --query "DBInstances[0].Endpoint.Address" \
    --output text)

save_state "RDS_ENDPOINT" "$RDS_ENDPOINT"
success "RDS created: ${RDS_ENDPOINT}:5432 / database: ${DB_NAME}"
echo "  Username : ${DB_USERNAME}"
echo "  Password : stored in scripts/.state (DB_PASSWORD)"
