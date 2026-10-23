# Parsfilo VPS deployment

This directory hosts the admin notifications dashboard and static mobile website behind Caddy. Google Play, Firebase Auth, FCM, AdMob and Crashlytics remain with their existing providers.

## Prerequisites

- Linux host with Docker Engine, Docker Compose v2 or later, and DNS pointing the selected apex, `www`, `admin` and `api` hosts at the VPS.
- TLS/ACME email address, accessible Firebase project configuration, and a correctly configured Functions upstream.
- A checkout of the complete repository (the admin Vite build imports `.ci/apps.json`).

## Prepare environment

```bash
cd android-multi-app-framework/side-projects/vps
cp .env.template .env
# Edit .env locally. Do not commit populated .env files.
bash scripts/preflight.sh
```

Set all the actual `VITE_FIREBASE_*`, function endpoint, domain and admin-authorization values in `.env`. This file is consumed only at runtime/by the Docker build; it must not be committed. Frontend `VITE_` parameters become public browser configuration; **never** place private credentials in these variables.

## Verify images without deploying

The admin build uses Node 24.12 and a Dockerfile-specific allowlist (`side-projects/admin-notifications/Dockerfile.dockerignore`). It includes only the admin sources plus `.ci/apps.json`, excluding root credentials, other projects, and generated files.

```bash
docker compose config --quiet
docker compose build admin-notifications mobil-web
```

Both NGINX-based images run as unprivileged UID 101, listen internally on port 8080, and provide a Docker HTTP health check. Caddy forwards to `admin-notifications:8080` and `mobil-web:8080`. Caddy's public ports 80/443 are unchanged.

## Controlled deployment

Only after configuration, verification, and explicit deployment approval:

```bash
docker compose up -d --build
docker compose ps
docker compose logs --tail=100 caddy admin-notifications mobil-web
```

Verify that both web containers report **healthy**, the admin dashboard and static website answer HTTPS through Caddy, and Firebase sign-in is authorized for the configured domains. Check all DNS entries and certificate issuance before opening production traffic.

## Rollback

Record the deployed Git commit and last known-good image identifiers. If health checks or production requests fail, restore the previous revision/images and run `docker compose up -d --build`, followed by `docker compose ps` and HTTP smoke checks. Rebuild from a trusted, reviewed Git revision; do not delete Docker volumes or alter live Firebase data as part of an ordinary web rollback.
