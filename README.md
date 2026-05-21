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