#!/usr/bin/env bash
# Run this ON the EC2 instance after AWS approves your port 25 unblocking request.
# SSH in first: ssh -i ~/.ssh/mail-engine-key.pem ec2-user@<ELASTIC_IP>
# Then run: sudo bash install-postfix.sh <ELASTIC_IP> <HOSTNAME>
# Example:  sudo bash install-postfix.sh 1.2.3.4 mail1.yourdomain.com
set -euo pipefail

ELASTIC_IP="${1:?Usage: $0 <elastic-ip> <hostname>}"
SMTP_HOSTNAME="${2:?Usage: $0 <elastic-ip> <hostname>}"

info() { echo "[INFO]  $*"; }
success() { echo "[OK]    $*"; }

# ─── Install Postfix ──────────────────────────────────────────────────────────
dnf install -y postfix

# ─── Configure Postfix for outbound relay ────────────────────────────────────
postconf -e "myhostname = ${SMTP_HOSTNAME}"
postconf -e "mydomain = $(echo "$SMTP_HOSTNAME" | cut -d. -f2-)"
postconf -e "myorigin = \$mydomain"
postconf -e "inet_interfaces = loopback-only"
postconf -e "inet_protocols = ipv4"
postconf -e "mydestination ="
postconf -e "relayhost ="
# Do NOT set smtp_bind_address to the Elastic IP — EC2 instances have only the
# private IP on their network interface; Elastic IP is AWS NAT and cannot be bound.
# Outbound packets from the private IP appear as the Elastic IP at the internet level.
postconf -e "smtp_helo_name = ${SMTP_HOSTNAME}"

# Bounce and delivery notifications
postconf -e "notify_classes = bounce, resource, software"
postconf -e "bounce_notice_recipient = postmaster"

# Queue and retry settings suitable for bulk sending
postconf -e "maximal_queue_lifetime = 1d"
postconf -e "bounce_queue_lifetime = 1d"
postconf -e "default_process_limit = 50"
postconf -e "smtp_connection_cache_on_demand = no"

# ─── Enable and start ─────────────────────────────────────────────────────────
systemctl enable postfix
systemctl restart postfix

success "Postfix configured and running."

echo ""
echo "NEXT STEPS — DNS records required before sending:"
echo ""
echo "  1. Forward A record:"
echo "       ${SMTP_HOSTNAME}  →  ${ELASTIC_IP}"
echo ""
echo "  2. Reverse DNS (PTR) — set in AWS Console:"
echo "       EC2 → Elastic IPs → select IP → Actions → Update reverse DNS"
echo "       Value: ${SMTP_HOSTNAME}"
echo ""
echo "  3. SPF record on your sending domain:"
echo "       TXT  v=spf1 ip4:${ELASTIC_IP} ~all"
echo ""
echo "  4. DKIM — generate key pair and add TXT record (the app has DkimKeyGenerator)"
echo ""
echo "  5. DMARC:"
echo "       TXT  _dmarc.yourdomain.com  v=DMARC1; p=none; rua=mailto:dmarc@yourdomain.com"
echo ""
echo "Test with: echo 'Test' | mail -s 'Test subject' your@email.com"
echo "Check logs: sudo journalctl -fu postfix"
