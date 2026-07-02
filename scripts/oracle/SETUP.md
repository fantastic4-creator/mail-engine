# Oracle Cloud Infrastructure (OCI) Deployment Guide

Mail Engine on OCI Always Free tier — zero cost, no port 25 restrictions.

## Automated Scripts (use these)

Once you have an OCI instance, the full deployment is 4 commands:

```bash
# 1. Wait for ARM capacity and create instance (saves OCI_IP to .state automatically)
bash scripts/oracle/retry-instance.sh

# 2. Install Java 21, PostgreSQL, Postfix, systemd service (run once)
bash scripts/oracle/07-setup-server.sh

# 3. Build JAR locally and deploy to OCI
bash scripts/oracle/08-deploy-app.sh

# 4. Create tenant, API key, domain, IP pool
bash scripts/oracle/09-bootstrap-tenant.sh
```

To tear down everything:
```bash
bash scripts/oracle/10-destroy-all.sh
```

Network infrastructure (VCN, subnet, security list) was provisioned in a prior session and IDs are saved in `scripts/oracle/.state`. If you need to recreate it, follow Steps 1-2 below manually via OCI Console.

---

## Why OCI over AWS

| | AWS (current) | OCI Always Free |
|---|---|---|
| Port 25 outbound | Blocked by default, need approval | Open by default |
| Monthly cost | ~$31/month (EC2 + RDS) | $0 |
| rDNS setup | Support ticket required | Self-serve in console |
| Compute (free) | None | 4 ARM cores + 24GB RAM |
| Database (free) | None | Autonomous DB (managed) |
| Bandwidth (free) | 1GB/month | 10TB/month |

---

## Prerequisites

- OCI account at https://cloud.oracle.com (credit card for identity only, not charged)
- Home region: `ap-mumbai-1` (Mumbai) for India, or pick closest
- SSH key pair (can reuse `~/.ssh/mail-engine-key.pem` or generate new)
- Domain DNS access (Hostinger for consult.rissolv.com)

---

## Step 1 — Create VCN (Virtual Cloud Network)

```
OCI Console → Networking → Virtual Cloud Networks
→ Start VCN Wizard → Create VCN with Internet Connectivity
  VCN Name: mail-engine-vcn
  CIDR: 10.0.0.0/16
→ Create
```

---

## Step 2 — Open firewall ports (Security List)

```
OCI Console → Networking → VCN → mail-engine-vcn
→ Security Lists → Default Security List
→ Add Ingress Rules:

Source CIDR: 0.0.0.0/0, Protocol: TCP, Port: 22    (SSH)
Source CIDR: 0.0.0.0/0, Protocol: TCP, Port: 8080  (App API)
Source CIDR: 0.0.0.0/0, Protocol: TCP, Port: 25    (SMTP outbound)
Source CIDR: 0.0.0.0/0, Protocol: TCP, Port: 587   (SMTP submission, optional)
```

---

## Step 3 — Create Compute Instance

```
OCI Console → Compute → Instances → Create Instance
  Name: mail-engine
  Image: Oracle Linux 8 (or Ubuntu 22.04)
  Shape: VM.Standard.A1.Flex
    OCPU: 2
    RAM: 12 GB
  Network: mail-engine-vcn, public subnet
  SSH Key: paste contents of ~/.ssh/mail-engine-key.pem.pub (or upload)
→ Create
```

Note the public IP assigned. You can also reserve a static public IP:
```
OCI Console → Networking → Reserved Public IPs → Reserve
→ Attach to the compute instance
```

---

## Step 4 — Set Reverse DNS (rDNS / PTR record)

Required for email deliverability. OCI allows this without a support ticket:
```
OCI Console → Compute → Instances → mail-engine
→ Attached VNICs → Primary VNIC → IPv4 Addresses
→ Edit → Reverse DNS (PTR): mail.consult.rissolv.com
→ Save
```

---

## Step 5 — SSH in and install dependencies

```bash
ssh -i ~/.ssh/mail-engine-key.pem opc@<OCI_PUBLIC_IP>

# Java 21
sudo yum install -y java-21-openjdk

# Verify
java -version
```

---

## Step 6 — Install and configure Postfix

```bash
sudo yum install -y postfix
sudo systemctl enable postfix

sudo postconf -e "myhostname = mail.consult.rissolv.com"
sudo postconf -e "mydomain = consult.rissolv.com"
sudo postconf -e "myorigin = consult.rissolv.com"
sudo postconf -e "inet_interfaces = loopback-only"
sudo postconf -e "inet_protocols = ipv4"
# Do NOT set smtp_bind_address — OCI NAT handles it, same as AWS

sudo systemctl restart postfix
sudo systemctl status postfix

# Verify Postfix listens on localhost:25
sudo ss -tlnp | grep :25
```

---

## Step 7 — Set up PostgreSQL

### Option A: OCI Autonomous Database (recommended — fully managed, always free)

```
OCI Console → Oracle Database → Autonomous Database → Create Autonomous Database
  Display Name: mail-engine-db
  Database Name: mailengine
  Workload Type: Transaction Processing
  Always Free: Yes (toggle on)
  Password: <choose strong password>
→ Create

After creation:
→ Database Connection → Download Instance Wallet (zip)
→ Copy connection string (use the JDBC URL format)
```

JDBC URL format for Autonomous DB:
```
jdbc:oracle:thin:@mailengine_high?TNS_ADMIN=/opt/wallet
```

Note: Autonomous DB uses Oracle dialect, not PostgreSQL. The app uses PostgreSQL-specific SQL (Flyway migrations). **Use Option B instead unless you migrate the schema.**

### Option B: Self-hosted PostgreSQL on same instance (simpler)

```bash
sudo yum install -y postgresql-server postgresql
sudo postgresql-setup --initdb
sudo systemctl enable postgresql
sudo systemctl start postgresql

sudo -u postgres psql <<EOF
CREATE USER mailengine WITH PASSWORD 'mailengine';
CREATE DATABASE mailengine OWNER mailengine;
\q
EOF

# Allow local connections
sudo sed -i 's/^local\s*all\s*all\s*peer/local all all md5/' /etc/postgresql/*/main/pg_hba.conf
# Or on Oracle Linux:
sudo sed -i 's/ident/md5/g' /var/lib/pgsql/data/pg_hba.conf
sudo systemctl restart postgresql

# Test
psql -h localhost -U mailengine -d mailengine -c "SELECT 1;"
```

---

## Step 8 — Deploy the app

### Create app directory and env file

```bash
sudo mkdir -p /opt/mail-engine
sudo chown opc:opc /opt/mail-engine

cat > /opt/mail-engine/app.env <<EOF
MAIL_ENGINE_DELIVERY_MODE=smtp
MAIL_ENGINE_STORAGE_MODE=postgres
MAIL_ENGINE_SMTP_HOST=localhost
MAIL_ENGINE_SMTP_PORT=25
MAIL_ENGINE_SMTP_AUTH_ENABLED=false
MAIL_ENGINE_SMTP_STARTTLS_ENABLED=false
MAIL_ENGINE_APP_BASE_URL=http://<OCI_PUBLIC_IP>:8080
MAIL_ENGINE_UNSUBSCRIBE_HMAC_SECRET=$(openssl rand -hex 32)
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mailengine
SPRING_DATASOURCE_USERNAME=mailengine
SPRING_DATASOURCE_PASSWORD=mailengine
MAIL_ENGINE_MAX_SENDS_PER_HOUR=10
EOF
```

### Create systemd service

```bash
sudo tee /etc/systemd/system/mail-engine.service <<EOF
[Unit]
Description=Mail Engine Spring Boot Application
After=network.target postgresql.service

[Service]
User=opc
EnvironmentFile=/opt/mail-engine/app.env
ExecStart=/usr/bin/java -jar /opt/mail-engine/app.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=mail-engine

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable mail-engine
```

### Copy and start the JAR (run from your local machine)

```bash
# Build first
cd "/Users/jeebanjyotiswain/Documents/New project/backend"
mvn clean package -DskipTests

# Copy to OCI
scp -i ~/.ssh/mail-engine-key.pem \
  target/mail-engine-backend-*.jar \
  opc@<OCI_PUBLIC_IP>:/opt/mail-engine/app.jar

# Start
ssh -i ~/.ssh/mail-engine-key.pem opc@<OCI_PUBLIC_IP> \
  'sudo systemctl start mail-engine'
```

### Verify startup

```bash
ssh -i ~/.ssh/mail-engine-key.pem opc@<OCI_PUBLIC_IP> \
  'sudo journalctl -fu mail-engine'

curl http://<OCI_PUBLIC_IP>:8080/api/health
# → {"status":"UP"}
```

---

## Step 9 — Open OS-level firewall (Oracle Linux)

OCI Security Lists control network-level firewall, but Oracle Linux also has its own firewall:

```bash
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --permanent --add-port=25/tcp
sudo firewall-cmd --reload
sudo firewall-cmd --list-ports
```

---

## Step 10 — Update DNS at Hostinger

Update the A record for `mail.consult.rissolv.com` to point to the new OCI IP:

```
Type: A
Name: mail.consult.rissolv.com
Value: <OCI_PUBLIC_IP>
TTL: 300
```

All other DNS records (DKIM, SPF, DMARC, _mailengine verification) stay the same — they are domain-based, not IP-based.

After DNS propagates (~5 min with TTL 300):
- Verify domain still shows VERIFIED (the _mailengine TXT check is re-run on demand)
- DKIM keys are stored in DB — migrated automatically if you copy the DB

---

## Step 11 — Bootstrap (if fresh DB)

If using a new database (not migrating from AWS RDS), run the bootstrap sequence:

```bash
BASE=http://<OCI_PUBLIC_IP>:8080

# Create tenant
curl -X POST $BASE/api/tenants -H 'Content-Type: application/json' \
  -d '{"name":"Rissolv"}'

# Create API key (copy rawKey — shown once)
curl -X POST $BASE/api/tenants/<tenantId>/api-keys \
  -H 'Content-Type: application/json' -d '{"name":"admin"}'

# Add domain
curl -X POST $BASE/api/tenants/<tenantId>/domains \
  -H 'Content-Type: application/json' -H 'X-API-Key: <key>' \
  -d '{"domainName":"consult.rissolv.com"}'

# Verify domain
curl -X POST $BASE/api/tenants/<tenantId>/domains/<domainId>/verify \
  -H 'X-API-Key: <key>'

# Create IP pool
curl -X POST $BASE/api/tenants/<tenantId>/ip-pools \
  -H 'Content-Type: application/json' -H 'X-API-Key: <key>' \
  -d '{"name":"default","trafficType":"bulk"}'

# Register OCI IP
curl -X POST $BASE/api/tenants/<tenantId>/ip-pools/<poolId>/ips \
  -H 'Content-Type: application/json' -H 'X-API-Key: <key>' \
  -d '{"publicIpAddress":"<OCI_PUBLIC_IP>","elasticAllocationId":"oci-reserved-ip","reverseDnsName":"mail.consult.rissolv.com"}'
```

---

## Step 12 — Migrate existing data from AWS RDS (optional)

If you want to keep existing campaigns, suppressions, and tenant data:

```bash
# Dump from AWS RDS (run locally)
PGPASSWORD=9ZuJSbL6clDYEEEIs0UTVovd55mMYR pg_dump \
  -h mail-engine-db.cqp0eagwea87.us-east-1.rds.amazonaws.com \
  -U mailengine -d mailengine \
  --no-owner --no-acl \
  -f /tmp/mailengine-backup.sql

# Copy to OCI instance
scp -i ~/.ssh/mail-engine-key.pem \
  /tmp/mailengine-backup.sql \
  opc@<OCI_PUBLIC_IP>:/tmp/

# Restore on OCI (stop app first)
ssh -i ~/.ssh/mail-engine-key.pem opc@<OCI_PUBLIC_IP> '
  sudo systemctl stop mail-engine
  PGPASSWORD=mailengine psql -h localhost -U mailengine -d mailengine \
    -f /tmp/mailengine-backup.sql
  sudo systemctl start mail-engine
'
```

---

## GitHub Actions — Update CI/CD for OCI

Update `.github/workflows/deploy.yml` secrets:
```
EC2_HOST  → <OCI_PUBLIC_IP>
EC2_SSH_KEY → contents of SSH private key for opc user
```

The deploy script SSHes as `ec2-user` — change to `opc` for OCI:
```yaml
# In deploy.yml, change:
ssh ec2-user@${{ secrets.EC2_HOST }} ...
# To:
ssh opc@${{ secrets.EC2_HOST }} ...
```

---

## Verify port 25 is open (OCI — no approval needed)

```bash
ssh -i ~/.ssh/mail-engine-key.pem opc@<OCI_PUBLIC_IP> \
  'telnet gmail-smtp-in.l.google.com 25'
# Should immediately show: 220 mx.google.com ESMTP ...
```

---

## Tail logs

```bash
ssh -i ~/.ssh/mail-engine-key.pem opc@<OCI_PUBLIC_IP> \
  'sudo journalctl -fu mail-engine'
```
