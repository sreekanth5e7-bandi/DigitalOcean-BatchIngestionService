# Deploy Batch Ingestion Service on DigitalOcean Droplet

This guide walks you through deploying the **Batch Ingestion Service** (Java 17 / Spring Boot) on a DigitalOcean Droplet you already created.

---

## What You Are Deploying

| Item | Value |
|------|-------|
| App type | Spring Boot REST API |
| Port | `8080` |
| Health check | `GET /health` |
| Database | Embedded H2 (file-based, stored on disk) |
| Data files | `data/batches` (DB) + `data/results/` (JSON output) |
| Build output | `target/batch-ingestion-1.0.0.jar` |

The service accepts batches of AI prompts, processes them concurrently, and stores results in H2 + JSON files. **Data lives on the Droplet disk** — use a persistent directory and back it up.

---

## Before You Start (Checklist)

- [ ] DigitalOcean Droplet created (Ubuntu 22.04 or 24.04 recommended)
- [ ] Droplet public IP address noted (e.g. `157.230.x.x`)
- [ ] SSH key added to the Droplet (DigitalOcean → Droplet → Access → SSH keys)
- [ ] GitHub SSH key added (if cloning via SSH) — see repo setup notes below
- [ ] Local terminal with SSH access to the Droplet

**Recommended Droplet size:** Basic plan, **1 GB RAM / 1 vCPU** is enough for demos and light load. Use **2 GB RAM** if you run large batches (500+ prompts).

---

## Step 1 — Connect to Your Droplet

From your local machine (or Cursor terminal):

```bash
ssh root@YOUR_DROPLET_IP
```

Replace `YOUR_DROPLET_IP` with your Droplet's public IP from the DigitalOcean dashboard.

**First login tip:** DigitalOcean emails you the root password if you did not use SSH keys only. Change the password when prompted, or rely on your SSH key.

---

## Step 2 — Update the Server and Install Dependencies

Run these on the Droplet as `root` (or a sudo user):

```bash
# Update packages
apt update && apt upgrade -y

# Install Java 17, Maven, Git, and curl
apt install -y openjdk-17-jdk maven git curl

# Verify installations
java -version    # should show 17.x
mvn -version     # should show Maven 3.x
git --version
```

---

## Step 3 — Create an Application User (Recommended)

Do not run the app as `root` in production.

```bash
# Create dedicated user
adduser --disabled-password --gecos "" batchapp

# Create app and data directories
mkdir -p /opt/batch-ingestion
mkdir -p /var/lib/batch-ingestion/data/results
chown -R batchapp:batchapp /opt/batch-ingestion /var/lib/batch-ingestion
```

---

## Step 4 — Clone the Repository on the Droplet

Switch to the app user and clone the repo.

### Option A — HTTPS (simplest)

```bash
su - batchapp
cd /opt/batch-ingestion
git clone https://github.com/sreekanth5e7-bandi/DigitalOcean-BatchIngestionService.git .
```

### Option B — SSH (if you added a Droplet SSH key to GitHub)

```bash
su - batchapp
cd /opt/batch-ingestion
git clone git@github.com:sreekanth5e7-bandi/DigitalOcean-BatchIngestionService.git .
```

---

## Step 5 — Build the Application

Still as `batchapp` inside `/opt/batch-ingestion`:

```bash
cd /opt/batch-ingestion
mvn -B clean package -DskipTests
```

This produces:

```
/opt/batch-ingestion/target/batch-ingestion-1.0.0.jar
```

**Optional — run tests before deploy:**

```bash
mvn -B verify
```

---

## Step 6 — Configure Production Settings

Create an environment file for production overrides.

```bash
sudo nano /etc/batch-ingestion.env
```

Paste the following (adjust paths and URLs as needed):

```bash
# Server
SERVER_PORT=8080

# Persist H2 database and results under /var/lib (survives redeploys)
SPRING_DATASOURCE_URL=jdbc:h2:file:/var/lib/batch-ingestion/data/batches;DB_CLOSE_DELAY=-1

# Disable H2 web console in production (security)
SPRING_H2_CONSOLE_ENABLED=false

# Inference API — point to your real service in production
# For demo, the app includes a built-in mock at /mock/infer on the same host:
BATCH_MOCK_API_URL=http://127.0.0.1:8080/mock/infer

# Results directory (absolute path)
BATCH_RESULTS_DIR=/var/lib/batch-ingestion/data/results

# Worker tuning (optional)
BATCH_WORKER_POOL_SIZE=10
BATCH_MAX_RETRIES=5
```

Secure the file:

```bash
sudo chown root:batchapp /etc/batch-ingestion.env
sudo chmod 640 /etc/batch-ingestion.env
```

Ensure data directory ownership:

```bash
sudo chown -R batchapp:batchapp /var/lib/batch-ingestion
```

---

## Step 7 — Create a systemd Service (Auto-Start on Boot)

Exit back to `root` (or use `sudo`) and create the service unit:

```bash
sudo nano /etc/systemd/system/batch-ingestion.service
```

Paste:

```ini
[Unit]
Description=Batch Ingestion Service (Spring Boot)
After=network.target

[Service]
Type=simple
User=batchapp
Group=batchapp
WorkingDirectory=/opt/batch-ingestion

EnvironmentFile=/etc/batch-ingestion.env

ExecStart=/usr/bin/java -jar /opt/batch-ingestion/target/batch-ingestion-1.0.0.jar

Restart=on-failure
RestartSec=10

# JVM memory (tune for your Droplet size)
Environment=JAVA_OPTS=-Xms256m -Xmx512m

[Install]
WantedBy=multi-user.target
```

Enable and start the service:

```bash
sudo systemctl daemon-reload
sudo systemctl enable batch-ingestion
sudo systemctl start batch-ingestion
sudo systemctl status batch-ingestion
```

**Useful commands:**

```bash
sudo systemctl restart batch-ingestion   # restart after config/code changes
sudo journalctl -u batch-ingestion -f     # live logs
sudo journalctl -u batch-ingestion -n 100 # last 100 log lines
```

---

## Step 8 — Open the Firewall

Allow SSH and HTTP traffic. If you expose the app directly on port 8080:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 8080/tcp
sudo ufw enable
sudo ufw status
```

**Production recommendation:** expose only ports `22` (SSH) and `80`/`443` (via Nginx in Step 9), and keep `8080` internal.

---

## Step 9 — (Recommended) Put Nginx in Front with Optional HTTPS

Installing Nginx lets you use port 80/443 and add TLS later.

```bash
sudo apt install -y nginx
sudo nano /etc/nginx/sites-available/batch-ingestion
```

Paste:

```nginx
server {
    listen 80;
    server_name YOUR_DROPLET_IP;   # or your domain, e.g. api.example.com

    client_max_body_size 50M;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Enable the site:

```bash
sudo ln -s /etc/nginx/sites-available/batch-ingestion /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
sudo ufw allow 'Nginx Full'
```

### Optional — HTTPS with Let's Encrypt (if you have a domain)

Point your domain's A record to the Droplet IP, then:

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d api.yourdomain.com
```

---

## Step 10 — Verify the Deployment

### Health check

```bash
# Direct to app
curl http://YOUR_DROPLET_IP:8080/health

# Through Nginx (if configured)
curl http://YOUR_DROPLET_IP/health
```

Expected response:

```json
{"status":"ok","workers":10}
```

### Submit a test batch

From your local machine or the Droplet:

```bash
curl -X POST http://YOUR_DROPLET_IP:8080/api/v1/batches \
  -H "Content-Type: application/json" \
  -d '[{"id":"p1","prompt":"Hello world"}]'
```

You receive `202 Accepted` with a `jobId`. Then:

```bash
# Replace JOB_ID with the value from the response
curl http://YOUR_DROPLET_IP:8080/api/v1/batches/JOB_ID/status
curl http://YOUR_DROPLET_IP:8080/api/v1/batches/JOB_ID/results
```

### Upload a JSON file

```bash
curl -X POST http://YOUR_DROPLET_IP:8080/api/v1/batches/upload \
  -F "file=@sample_data/prompts.json"
```

---

## Step 11 — DigitalOcean Dashboard Checks

In the DigitalOcean control panel:

1. **Droplet → Graphs** — watch CPU, memory, and bandwidth while running a batch.
2. **Networking → Firewalls** (optional) — create a Cloud Firewall allowing `22`, `80`, `443` (and `8080` only if needed).
3. **Snapshots** — take a snapshot after a successful deploy so you can restore quickly.
4. **Volumes** (optional) — attach a Block Storage volume and mount it at `/var/lib/batch-ingestion/data` for larger or persistent data.

---

## Step 12 — Deploying Updates

When you push new code to GitHub:

```bash
ssh root@YOUR_DROPLET_IP

sudo systemctl stop batch-ingestion

su - batchapp -c "cd /opt/batch-ingestion && git pull && mvn -B clean package -DskipTests"

sudo systemctl start batch-ingestion
sudo systemctl status batch-ingestion
```

Your H2 database and results under `/var/lib/batch-ingestion/data` are preserved across redeploys.

---

## API Reference (Production)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Health check for load balancers |
| `POST` | `/api/v1/batches` | Submit JSON array of prompts |
| `POST` | `/api/v1/batches/upload` | Upload a `.json` file (max 50 MB) |
| `GET` | `/api/v1/batches/{jobId}/status` | Job progress |
| `GET` | `/api/v1/batches/{jobId}/results` | Results when job is complete |
| `POST` | `/mock/infer` | Built-in mock inference (dev/demo) |

---

## Environment Variables Reference

Spring Boot maps these environment variables automatically:

| Variable | Purpose | Default |
|----------|---------|---------|
| `SERVER_PORT` | HTTP port | `8080` |
| `SPRING_DATASOURCE_URL` | H2 database file path | `./data/batches` |
| `SPRING_H2_CONSOLE_ENABLED` | H2 admin UI | `true` (set `false` in prod) |
| `BATCH_MOCK_API_URL` | Inference endpoint URL | `http://localhost:8080/mock/infer` |
| `BATCH_RESULTS_DIR` | JSON output directory | `data/results` |
| `BATCH_WORKER_POOL_SIZE` | Concurrent workers | `10` |
| `BATCH_MAX_RETRIES` | Retries on HTTP 429 | `5` |
| `BATCH_INITIAL_BACKOFF_MS` | Initial retry delay | `500` |
| `BATCH_MAX_BACKOFF_MS` | Max retry delay | `30000` |

---

## Troubleshooting

### Service won't start

```bash
sudo journalctl -u batch-ingestion -n 50 --no-pager
```

Common causes:
- JAR not built — run `mvn package` again
- Port 8080 in use — `sudo ss -tlnp | grep 8080`
- Permission denied on data dir — `sudo chown -R batchapp:batchapp /var/lib/batch-ingestion`

### Cannot reach the app from browser

- Check UFW: `sudo ufw status`
- Check service: `sudo systemctl status batch-ingestion`
- Check Nginx: `sudo nginx -t && sudo systemctl status nginx`
- DigitalOcean Cloud Firewall may block ports — allow 80/443/8080 in Networking → Firewalls

### `Permission denied (publickey)` when cloning on Droplet

Use HTTPS clone instead, or add the Droplet's SSH public key to GitHub → Settings → SSH keys.

### Out of memory on small Droplet

Reduce JVM heap in the systemd unit:

```ini
Environment=JAVA_OPTS=-Xms128m -Xmx384m
```

Or resize the Droplet to 2 GB RAM in DigitalOcean.

### Data lost after redeploy

Ensure `SPRING_DATASOURCE_URL` and `BATCH_RESULTS_DIR` point to `/var/lib/batch-ingestion/data/...`, not inside `/opt/batch-ingestion` (which gets overwritten on `git pull` + rebuild).

---

## Architecture on the Droplet

```
Internet
   │
   ▼
[UFW Firewall] ── port 80/443 (and optionally 8080)
   │
   ▼
[Nginx] (optional) ── reverse proxy
   │
   ▼
[systemd: batch-ingestion.service]
   │
   ▼
[Java 17 + batch-ingestion-1.0.0.jar] :8080
   │
   ├── H2 DB  → /var/lib/batch-ingestion/data/batches
   ├── Results → /var/lib/batch-ingestion/data/results/
   └── Inference → BATCH_MOCK_API_URL (mock or external API)
```

---

## Quick Command Summary

| Task | Command |
|------|---------|
| SSH to Droplet | `ssh root@YOUR_DROPLET_IP` |
| Build app | `mvn -B clean package -DskipTests` |
| Start service | `sudo systemctl start batch-ingestion` |
| Stop service | `sudo systemctl stop batch-ingestion` |
| View logs | `sudo journalctl -u batch-ingestion -f` |
| Health check | `curl http://YOUR_DROPLET_IP:8080/health` |

---

## Next Steps (Optional Improvements)

- Point `BATCH_MOCK_API_URL` to a real inference API instead of the built-in mock
- Add a domain + HTTPS via Certbot
- Replace H2 with PostgreSQL for multi-instance scaling (requires code changes)
- Add DigitalOcean Monitoring alerts for CPU/memory
- Set up automated deploys with GitHub Actions + SSH

---

**Document version:** 1.0  
**Service version:** 1.0.0  
**Target:** DigitalOcean Droplet (Ubuntu 22.04/24.04)
