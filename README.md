# ArgusIQ

Real-time backend observability platform built with Spring Boot, React, PostgreSQL, WebSockets, and Docker.

## Architecture

```text
ArgusIQ/
├── backend/     # Spring Boot backend
├── frontend/    # React + Vite frontend
├── docs/        # Architecture and documentation
├── docker/      # Docker configs
├── scripts/     # Helper scripts
```

## Tech Stack

### Backend
- Java 21
- Spring Boot
- Spring WebSocket
- Spring Data JPA
- PostgreSQL

### Frontend
- React
- Vite
- STOMP.js
- SockJS

## Features

- Real-time API trace monitoring
- Live websocket updates
- Request execution tracking
- PostgreSQL persistence
- REST + WebSocket architecture
- Trace filtering infrastructure

## Running Locally

### Required configuration

ArgusIQ reads deployment credentials from environment variables. Do not commit their values or place them in tracked configuration files.

| Variable | Required | Purpose |
| --- | --- | --- |
| `ARGUSIQ_DB_PASSWORD` | Yes | PostgreSQL password |
| `ARGUSIQ_INVESTIGATION_PASSWORD` | Yes | Password for investigation REST and WebSocket access; minimum 16 characters |
| `ARGUSIQ_INGESTION_PASSWORD` | Yes | Password for OTLP ingestion; minimum 16 characters |
| `ARGUSIQ_DB_URL` | No | JDBC URL; defaults to local `argusiq` PostgreSQL |
| `ARGUSIQ_DB_USERNAME` | No | PostgreSQL username; defaults to `argusiq` |
| `ARGUSIQ_INVESTIGATION_USERNAME` | No | Investigation principal; defaults to `investigator` |
| `ARGUSIQ_INGESTION_USERNAME` | No | Collector principal; defaults to `collector` |
| `ARGUSIQ_ALLOWED_ORIGINS` | No | Comma-separated frontend origins; defaults to `http://localhost:5173` |

Supply required values through the shell, IDE run configuration, container secret, or deployment secret manager. The application intentionally fails startup when a required password is absent.

The current boundary uses HTTP Basic with separate investigator and collector roles and must be deployed behind TLS. There is intentionally no login UI yet. For local browser use, open a protected backend URL such as `http://localhost:8080/api/v1/traces` and authenticate with the investigator principal before opening the frontend. Configure the OpenTelemetry collector to use the collector principal when exporting to `/v1/traces`.

The frontend connection targets can be changed with `VITE_ARGUSIQ_API_BASE_URL` and `VITE_ARGUSIQ_WEBSOCKET_URL`. These are public build settings and must never contain credentials.

The database credential previously committed to this repository must be rotated or revoked. Removing it from the current tree does not remove it from Git history; history cleanup is a separate, potentially disruptive operation and has not been performed.

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend:
http://localhost:5173

Backend:
http://localhost:8080
