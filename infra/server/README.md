# Production server (Terraform) and deploys

Issues #21 and #23. One Hetzner Cloud **CX23** (2 vCPU, 4 GB) runs the whole stack with
Docker Compose:

```
internet ─▶ Hetzner firewall (22, 80, 443) ─▶ caddy (TLS) ─▶ frontend (nginx, /api/ proxy) ─▶ backend-api ─▶ postgres
                                                                                 data-job ──┘
```

- **Terraform** (this folder) creates the server, firewall, SSH key and DNS records.
  cloud-init installs Docker, hardens SSH, enables unattended security upgrades, adds
  swap and log rotation, and installs the deploy script. It does not deploy the app.
- **CI** (`.github/workflows/deploy.yml`) builds the images on every merge to `master`,
  pushes them to GHCR (private) tagged with the commit SHA, and deploys over SSH.
- The **deploy script** (`files/whoshot-deploy`) is the only thing the CI key can run.
  It pulls the images and the release files (compose, Caddyfile, Alloy config, packed in
  the `whos-hot-nhl-deploy` image) for one SHA and starts them.

There are **no database backups**: data is re-derivable with a backfill. The server is
protected against replacement (`prevent_destroy`, Hetzner delete protection).

## What you need

| What | Where it goes |
|---|---|
| Hetzner Cloud account, project, API token (Read & Write) | `terraform.tfvars` |
| Your SSH key | `terraform.tfvars` (public half) |
| A new CI deploy key (below) | public half: `terraform.tfvars`; private half: GitHub |
| A domain (any registrar) | `terraform.tfvars`, then nameservers at the registrar |
| DB password, Grafana Cloud values | `/opt/whoshot/app.env` on the server |

Don't create the server by hand: Terraform creates it.

## First-time setup

Commands are PowerShell. Terraform 1.6+ is needed; without a local install, run it via
Docker as described in `infra/monitoring/README.md`.

1. **Deploy key**, only for CI:

   ```powershell
   ssh-keygen -t ed25519 -N '""' -C whoshot-deploy -f $HOME\.ssh\whoshot_deploy
   ```

2. **Terraform:** copy `terraform.tfvars.example` to `terraform.tfvars` and fill it in.
   Then:

   ```powershell
   terraform init
   terraform apply
   ```

   cloud-init takes a few minutes after the apply. Back up `terraform.tfstate` privately
   (e.g. a Bitwarden attachment); it holds the token. Without it, resources have to be
   imported by hand.

3. **Domain:** at the registrar, set the nameservers to the `nameservers` output. Until
   DNS resolves to the server, Caddy can't get a certificate (it keeps retrying).

4. **Server secrets:** fill in a copy of `app.env.example` (keep it in Bitwarden too)
   and install it:

   ```powershell
   scp app.env ops@<ipv4>:/tmp/app.env
   ssh ops@<ipv4> "sudo install -o deploy -g deploy -m 600 /tmp/app.env /opt/whoshot/app.env && rm /tmp/app.env"
   ```

   The first `ssh` asks you to confirm the host key. Check it against
   `ssh-keyscan -t ed25519 <ipv4>` from another network if you want to be thorough.

5. **GitHub** (repository settings):
   - **Environments → New environment `production`**. Under deployment branches, allow
     only `master`. Add these environment secrets:
     - `DEPLOY_HOST`: the server's IPv4 address
     - `DEPLOY_SSH_KEY`: contents of `$HOME\.ssh\whoshot_deploy` (the private key)
     - `DEPLOY_KNOWN_HOSTS`: output of `ssh-keyscan -t ed25519 <ipv4>`
   - **Variables** (repository): `SITE_DOMAIN` (e.g. `whoshotnhl.com`), and optionally
     `VITE_WEB3FORMS_ACCESS_KEY` and `VITE_DONATION_URL_SEK|EUR|USD`. These end up in the
     public JS bundle, so they are variables, not secrets.

6. **First deploy:** merge to `master`, or run the **deploy** workflow manually. Then
   load the season once (the database starts empty):

   ```sh
   ssh ops@<ipv4>
   sudo -iu deploy
   cd /opt/whoshot/current
   docker compose --profile prod stop data-job
   docker compose --profile prod run --rm -e SPRING_PROFILES_ACTIVE=prod,initial-load data-job
   docker compose --profile prod up -d data-job
   ```

7. **Monitoring:** set `site_url` in `infra/monitoring` and apply it there for the
   uptime checks.

## Deploys and rollback

- Every merge to `master`: tests, frontend build, nginx rate limit tests, image builds,
  deploy, then a smoke test (site and `/api` return 200 through Caddy, and a burst of
  requests gets rate limited).
- **Rollback:** Actions → deploy → Run workflow, with the full SHA of an earlier build.
  GHCR keeps the last 10 builds per image.
- Deploys queue; they never run in parallel. The data job only ever runs once.
- A failed deploy leaves the new containers as they are and fails the workflow. Roll back
  to the previous SHA if the site is affected.

## Day-to-day on the server

Log in as `ops` (sudo, Docker). The running release is `/opt/whoshot/current`:

```sh
sudo -iu deploy
cd /opt/whoshot/current
docker compose --profile prod ps
docker compose --profile prod logs -f backend-api
```

Edit secrets with `sudo -u deploy nano /opt/whoshot/app.env`; they apply on the next
deploy (or rerun the last one).

## Security notes

- SSH accepts keys only, no root login, and only `ops` and `deploy`. Port 22 is open to
  everyone because GitHub runner addresses change; set `ssh_allowed_cidrs` to narrow it
  if you deploy differently.
- The deploy key is pinned in `authorized_keys` to the deploy script with no shell or
  forwarding. The script accepts only `deploy <40-char SHA>` and runs only files from
  images CI pushed. The GHCR token comes from the workflow run and expires with it.
- Docker publishes only 80 and 443; Postgres, the API and Actuator stay on the Docker
  network. The Hetzner firewall sits outside the server, so Docker's iptables rules
  can't bypass it.
- Ubuntu security updates install daily; the server reboots at 12:00 UTC when an update
  needs it.

## Changing the server later

cloud-init only runs on first boot, and Terraform ignores changes to it so the server is
never replaced. To change something on a running server, do it by hand and update
`cloud-init.yaml.tftpl` to match. For a new version of the deploy script:

```powershell
scp infra/server/files/whoshot-deploy ops@<ipv4>:/tmp/
ssh ops@<ipv4> "sudo install -m 755 /tmp/whoshot-deploy /usr/local/bin/whoshot-deploy"
```
