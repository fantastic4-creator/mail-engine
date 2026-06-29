#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/../config.sh"

# ─── Check if already installed ──────────────────────────────────────────────
if command -v aws &>/dev/null; then
    success "AWS CLI already installed: $(aws --version)"
    exit 0
fi

info "Installing AWS CLI v2 for macOS..."

# ─── Download and install ─────────────────────────────────────────────────────
TMPDIR_AWSCLI=$(mktemp -d)
curl -fsSL "https://awscli.amazonaws.com/AWSCLIV2.pkg" -o "${TMPDIR_AWSCLI}/AWSCLIV2.pkg"
sudo installer -pkg "${TMPDIR_AWSCLI}/AWSCLIV2.pkg" -target /
rm -rf "$TMPDIR_AWSCLI"

success "Installed: $(aws --version)"

# ─── Configure credentials ────────────────────────────────────────────────────
echo ""
echo "Next: configure your AWS credentials."
echo "You need an IAM user with AdministratorAccess (or at minimum EC2, RDS, IAM permissions)."
echo ""
echo "To create one:"
echo "  1. Log in to https://console.aws.amazon.com/iam"
echo "  2. Users → Create user → Attach 'AdministratorAccess' policy"
echo "  3. Security credentials tab → Create access key → CLI use case"
echo "  4. Copy the Access Key ID and Secret Access Key"
echo ""
read -rp "Run 'aws configure' now? [Y/n] " answer
if [[ "${answer:-Y}" =~ ^[Yy]$ ]]; then
    aws configure
    aws sts get-caller-identity && success "Credentials verified." || err "Credential check failed. Re-run aws configure."
else
    echo "Run 'aws configure' manually before proceeding."
fi
