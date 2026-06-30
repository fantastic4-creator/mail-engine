#!/usr/bin/env bash
# Shared configuration sourced by all scripts.
# Override any value by setting it in your environment before running a script.
# Example: AWS_REGION=ap-south-1 ./aws/04-create-rds.sh

APP_NAME="mail-engine"
AWS_REGION="${AWS_REGION:-us-east-1}"

EC2_INSTANCE_TYPE="t3.micro"
EC2_AMI_NAME_FILTER="al2023-ami-2023*-x86_64"
KEY_NAME="mail-engine-key"
KEY_FILE="$HOME/.ssh/${KEY_NAME}.pem"

RDS_INSTANCE_TYPE="db.t3.micro"
RDS_ENGINE="postgres"
RDS_ENGINE_VERSION="16"
DB_IDENTIFIER="mail-engine-db"
DB_NAME="mailengine"
DB_USERNAME="mailengine"

APP_DIR="/opt/mail-engine"
APP_USER="mailengine"
APP_PORT="8080"

SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE_FILE="${SCRIPTS_DIR}/.state"

load_state() {
    [[ -f "$STATE_FILE" ]] && source "$STATE_FILE"
}

save_state() {
    local key="$1"
    local value="$2"
    touch "$STATE_FILE"
    if grep -q "^${key}=" "$STATE_FILE" 2>/dev/null; then
        sed -i.bak "s|^${key}=.*|${key}=${value}|" "$STATE_FILE" && rm -f "${STATE_FILE}.bak"
    else
        echo "${key}=${value}" >> "$STATE_FILE"
    fi
}

require_state() {
    local key="$1"
    load_state
    local value="${!key}"
    if [[ -z "$value" ]]; then
        echo "ERROR: $key is not set. Run the prerequisite script first."
        exit 1
    fi
}

info()    { echo "[INFO]  $*"; }
success() { echo "[OK]    $*"; }
warn()    { echo "[WARN]  $*"; }
err()     { echo "[ERROR] $*" >&2; exit 1; }
