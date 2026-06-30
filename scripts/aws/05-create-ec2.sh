#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/../config.sh"
load_state

require_state "KEY_NAME"
require_state "SG_EC2_ID"

info "Creating EC2 instance in ${AWS_REGION}"

# ─── Find latest Amazon Linux 2023 AMI ───────────────────────────────────────
AMI_ID=$(aws ec2 describe-images \
    --owners amazon \
    --filters \
        "Name=name,Values=${EC2_AMI_NAME_FILTER}" \
        "Name=state,Values=available" \
        "Name=architecture,Values=x86_64" \
    --region "$AWS_REGION" \
    --query "sort_by(Images, &CreationDate)[-1].ImageId" \
    --output text)

[[ -z "$AMI_ID" || "$AMI_ID" == "None" ]] && err "Could not find Amazon Linux 2023 AMI in ${AWS_REGION}."
info "Using AMI: ${AMI_ID}"

# ─── Check if instance already exists ────────────────────────────────────────
if [[ -n "${EC2_INSTANCE_ID:-}" ]]; then
    STATE=$(aws ec2 describe-instances \
        --instance-ids "$EC2_INSTANCE_ID" \
        --region "$AWS_REGION" \
        --query "Reservations[0].Instances[0].State.Name" \
        --output text 2>/dev/null || echo "notfound")
    if [[ "$STATE" != "terminated" && "$STATE" != "notfound" ]]; then
        info "EC2 instance ${EC2_INSTANCE_ID} already exists (state: ${STATE})."
        success "EC2: ${EC2_INSTANCE_ID} — ${ELASTIC_IP:-no Elastic IP yet}"
        exit 0
    fi
fi

# ─── Launch instance ──────────────────────────────────────────────────────────
BOOTSTRAP_SCRIPT="$(dirname "$0")/../ec2/bootstrap.sh"
[[ ! -f "$BOOTSTRAP_SCRIPT" ]] && err "bootstrap.sh not found at ${BOOTSTRAP_SCRIPT}"

EC2_INSTANCE_ID=$(aws ec2 run-instances \
    --image-id "$AMI_ID" \
    --instance-type "$EC2_INSTANCE_TYPE" \
    --key-name "$KEY_NAME" \
    --security-group-ids "$SG_EC2_ID" \
    --user-data "file://${BOOTSTRAP_SCRIPT}" \
    --block-device-mappings "[{\"DeviceName\":\"/dev/xvda\",\"Ebs\":{\"VolumeSize\":20,\"VolumeType\":\"gp3\",\"DeleteOnTermination\":true}}]" \
    --tag-specifications \
        "ResourceType=instance,Tags=[{Key=Name,Value=${APP_NAME}},{Key=Project,Value=${APP_NAME}}]" \
    --region "$AWS_REGION" \
    --query "Instances[0].InstanceId" \
    --output text)

save_state "EC2_INSTANCE_ID" "$EC2_INSTANCE_ID"
info "Instance launched: ${EC2_INSTANCE_ID}. Waiting for it to be running..."

aws ec2 wait instance-running \
    --instance-ids "$EC2_INSTANCE_ID" \
    --region "$AWS_REGION"

success "Instance is running."

# ─── Allocate and associate Elastic IP ───────────────────────────────────────
info "Allocating Elastic IP..."
ALLOCATION=$(aws ec2 allocate-address \
    --domain vpc \
    --region "$AWS_REGION" \
    --tag-specifications "ResourceType=elastic-ip,Tags=[{Key=Name,Value=${APP_NAME}},{Key=Project,Value=${APP_NAME}}]")

ELASTIC_IP=$(echo "$ALLOCATION" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['PublicIp'])")
EIP_ALLOCATION_ID=$(echo "$ALLOCATION" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['AllocationId'])")

aws ec2 associate-address \
    --instance-id "$EC2_INSTANCE_ID" \
    --allocation-id "$EIP_ALLOCATION_ID" \
    --region "$AWS_REGION" > /dev/null

save_state "ELASTIC_IP" "$ELASTIC_IP"
save_state "EIP_ALLOCATION_ID" "$EIP_ALLOCATION_ID"

success "EC2 instance ready."
echo ""
echo "  Instance ID : ${EC2_INSTANCE_ID}"
echo "  Public IP   : ${ELASTIC_IP}  ← use this as your sending IP"
echo "  SSH         : ssh -i ${KEY_FILE} ec2-user@${ELASTIC_IP}"
echo ""
echo "Bootstrap is running in the background on the instance."
echo "Wait 3–5 minutes for Java installation to complete before deploying."
