#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/../config.sh"
load_state

info "Creating security groups in ${AWS_REGION}"

# ─── Get default VPC ──────────────────────────────────────────────────────────
VPC_ID=$(aws ec2 describe-vpcs \
    --filters "Name=isDefault,Values=true" \
    --region "$AWS_REGION" \
    --query "Vpcs[0].VpcId" \
    --output text)

[[ "$VPC_ID" == "None" || -z "$VPC_ID" ]] && err "No default VPC found in ${AWS_REGION}."
info "Using VPC: ${VPC_ID}"
save_state "VPC_ID" "$VPC_ID"

# ─── EC2 security group ───────────────────────────────────────────────────────
SG_EC2_NAME="${APP_NAME}-ec2"
EXISTING_EC2_SG=$(aws ec2 describe-security-groups \
    --filters "Name=group-name,Values=${SG_EC2_NAME}" "Name=vpc-id,Values=${VPC_ID}" \
    --region "$AWS_REGION" \
    --query "SecurityGroups[0].GroupId" \
    --output text 2>/dev/null || echo "None")

if [[ "$EXISTING_EC2_SG" == "None" || -z "$EXISTING_EC2_SG" ]]; then
    SG_EC2_ID=$(aws ec2 create-security-group \
        --group-name "$SG_EC2_NAME" \
        --description "Mail Engine EC2 instances" \
        --vpc-id "$VPC_ID" \
        --region "$AWS_REGION" \
        --query "GroupId" \
        --output text)
    info "Created EC2 security group: ${SG_EC2_ID}"

    # SSH from anywhere (restrict this to your IP in production)
    aws ec2 authorize-security-group-ingress \
        --group-id "$SG_EC2_ID" \
        --protocol tcp --port 22 --cidr 0.0.0.0/0 \
        --region "$AWS_REGION"

    # Spring Boot API port
    aws ec2 authorize-security-group-ingress \
        --group-id "$SG_EC2_ID" \
        --protocol tcp --port "$APP_PORT" --cidr 0.0.0.0/0 \
        --region "$AWS_REGION"

    # SMTP inbound for bounce/feedback ingestion (port 25)
    aws ec2 authorize-security-group-ingress \
        --group-id "$SG_EC2_ID" \
        --protocol tcp --port 25 --cidr 0.0.0.0/0 \
        --region "$AWS_REGION"
else
    SG_EC2_ID="$EXISTING_EC2_SG"
    info "EC2 security group already exists: ${SG_EC2_ID}"
fi

save_state "SG_EC2_ID" "$SG_EC2_ID"
success "EC2 security group: ${SG_EC2_ID}"

# ─── RDS security group ───────────────────────────────────────────────────────
SG_RDS_NAME="${APP_NAME}-rds"
EXISTING_RDS_SG=$(aws ec2 describe-security-groups \
    --filters "Name=group-name,Values=${SG_RDS_NAME}" "Name=vpc-id,Values=${VPC_ID}" \
    --region "$AWS_REGION" \
    --query "SecurityGroups[0].GroupId" \
    --output text 2>/dev/null || echo "None")

if [[ "$EXISTING_RDS_SG" == "None" || -z "$EXISTING_RDS_SG" ]]; then
    SG_RDS_ID=$(aws ec2 create-security-group \
        --group-name "$SG_RDS_NAME" \
        --description "Mail Engine RDS - only accessible from EC2 SG" \
        --vpc-id "$VPC_ID" \
        --region "$AWS_REGION" \
        --query "GroupId" \
        --output text)
    info "Created RDS security group: ${SG_RDS_ID}"

    # Postgres port only from the EC2 security group
    aws ec2 authorize-security-group-ingress \
        --group-id "$SG_RDS_ID" \
        --protocol tcp --port 5432 \
        --source-group "$SG_EC2_ID" \
        --region "$AWS_REGION"
else
    SG_RDS_ID="$EXISTING_RDS_SG"
    info "RDS security group already exists: ${SG_RDS_ID}"
fi

save_state "SG_RDS_ID" "$SG_RDS_ID"
success "RDS security group: ${SG_RDS_ID}"
