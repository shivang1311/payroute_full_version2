# PayRoute Hub

> **Enterprise payments orchestration & routing platform** — a multi-service Spring Boot backend with a role-aware React frontend, modeling the kind of system a real bank uses to initiate, screen, route, post, and settle payments end-to-end.

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18.3-blue.svg)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-blue.svg)](https://www.typescriptlang.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)

---

## Table of Contents

1. [What It Does](#what-it-does)
2. [Architecture](#architecture)
3. [Tech Stack](#tech-stack)
4. [Microservices](#microservices)
5. [Features by Role](#features-by-role)
6. [Prerequisites](#prerequisites)
7. [Quick Start](#quick-start)
8. [Database Setup](#database-setup)
9. [Running the Backend](#running-the-backend)
10. [Running the Frontend](#running-the-frontend)
11. [Default Credentials](#default-credentials)
12. [Project Structure](#project-structure)
13. [Testing](#testing)
14. [Code Coverage](#code-coverage)
15. [Static Analysis (SonarQube)](#static-analysis-sonarqube)
16. [API Documentation](#api-documentation)
17. [Code Documentation (Javadoc / TypeDoc)](#code-documentation-javadoc--typedoc)
18. [Troubleshooting](#troubleshooting)

---

## What It Does

PayRoute Hub orchestrates the full lifecycle of a payment:

```
Customer initiates ──► Compliance screens ──► Routing picks rail ──►
Ledger posts entries ──► Settlement batches ──► Reconciliation closes
```

Plus everything around it — KYC, beneficiaries, scheduled payments, returns, holds, exceptions, fee schedules, account statements, dashboards, and notifications.

The system supports **5 distinct user roles** each with their own UI experience: `CUSTOMER`, `OPERATIONS`, `COMPLIANCE`, `RECONCILIATION`, and `ADMIN`.

---

## Architecture

```
                      ┌────────────────────────┐
                      │   React + Vite (5173)  │
                      │   AntD + Zustand       │
                      └────────────┬───────────┘
                                   │
                                   ▼
                      ┌────────────────────────┐
                      │  API Gateway (9080)    │   ◄── JWT auth + role gates
                      │  Spring Cloud Gateway  │
                      └────────────┬───────────┘
                                   │
                ┌──────────────────┼──────────────────┐
                ▼                                     ▼
       ┌─────────────────┐                  ┌──────────────────┐
       │ Eureka  (8761)  │  ◄──registers──  │  11 Microservices│
       └─────────────────┘                  └──────────────────┘
                                                     │
                                                     ▼
                                           ┌──────────────────┐
                                           │  MySQL  (3306)   │
                                           │  payroute_db     │
                                           └──────────────────┘
```

- All inter-service calls are **Feign clients with header forwarding** (JWT + role + party ID propagate downstream).
- Services discover each other via **Eureka**.
- All external traffic enters through the **API Gateway**.

---

## Tech Stack

### Backend
- **Java 17** + **Spring Boot 3.3.5**
- **Spring Cloud 2023.0.3** (Gateway, Eureka, OpenFeign)
- **Spring Data JPA** + **Hibernate 6**
- **MySQL 8.0**
- **JWT (jjwt 0.12.6)** for stateless auth
- **MapStruct** for DTO ↔ entity mapping
- **Lombok** for boilerplate reduction
- **Resilience4j** for circuit breaking
- **springdoc-openapi** for Swagger UI
- **JaCoCo** + **JUnit 5** + **Mockito** for testing

### Frontend
- **React 18.3** + **TypeScript 5**
- **Vite 8** as the dev server / bundler
- **Ant Design 5** for UI components
- **Zustand** for state management
- **Axios** for HTTP
- **React Router 7** for routing
- **Recharts** for dashboard charts
- **jsPDF** + **jspdf-autotable** for PDF generation
- **Vitest** + **React Testing Library** + **jsdom** for testing
- **TypeDoc** for API documentation

---

## Microservices

| # | Service | Port | Responsibility |
|---|---------|------|----------------|
| 1 | `discovery-server` | 8761 | Eureka service registry |
| 2 | `api-gateway` | 9080 | Auth gate, routing, CORS, rate limiting |
| 3 | `iam-service` | 8081 | Users, roles, JWT issuance, password rotation, transaction PIN |
| 4 | `party-service` | 8082 | Customers/corporates + account directory + beneficiaries |
| 5 | `payment-service` | 8083 | Payment lifecycle, scheduled payments, orchestration |
| 6 | `routing-service` | 8084 | Rail selection (NEFT/RTGS/IMPS/UPI/ACH/WIRE) |
| 7 | `ledger-service` | 8085 | Internal double-entry ledger, fees, statements |
| 8 | `notification-service` | 8086 | In-app notifications, broadcasts, webhooks |
| 9 | `compliance-service` | 8087 | KYC/AML screening, holds, sanctions |
| 10 | `exception-service` | 8088 | Exception queue, returns, reconciliation |
| 11 | `settlement-service` | 8089 | Settlement batches, payment reports |

---

## Features by Role

### 👤 CUSTOMER
- Register / login / change password / set transaction PIN
- View own accounts (₹10,00,000 opening balance auto-seeded on new INR accounts)
- Add / manage beneficiaries (account directory)
- Initiate one-off payments (channels: MOBILE / ONLINE only)
- Schedule future / recurring payments
- Download account statement (CSV + PDF)
- View own payment history with reference numbers (`PRXXXXXXXXXX`)
- Receive notifications

### 🛠 OPERATIONS
- View all payments across customers
- Manage exception queue (sticky-column horizontal scroll)
- Process returns and reconciliation breaks
- View dashboards with payment stats and rail breakdowns

### 🛡 COMPLIANCE
- **Custom dashboard** — Active Holds, Pending Screening, Released Today, Rejected Today
- Hold queue with release / reject actions
- **Real-time broadcast notifications** when a new hold is created
- View screening logs

### 📊 RECONCILIATION
- **Custom dashboard** — Open Breaks, Returns to Process, Failed Settlements, Matched Today
- Open breaks queue with resolution flow
- **3 broadcast notifications** — new breaks, new returns, failed settlement batches
- Reconciliation runs

### 👑 ADMIN
- All of the above
- User management (CRUD, role assignment, activation)
- Routing rules configuration
- Fee schedules CRUD
- SLA configuration
- Webhook endpoints
- System-wide reports

---

## Prerequisites

| Tool | Min Version | Why |
|------|-------------|-----|
| **Java JDK** | 17 | Backend |
| **Maven** | 3.8+ | Backend build |
| **Node.js** | 18+ | Frontend |
| **npm** | 9+ | Frontend packages |
| **MySQL** | 8.0+ | Database |
| **Git** | — | Cloning the repo |

Optional but recommended:
- **Docker** — for running SonarQube
- **Postman / Insomnia** — for testing APIs directly

---

## Quick Start

```bash
# 1. Clone the repo
git clone https://github.com/shivang1311/payroute_full_version2.git
cd payroute_full_version2

# 2. Database (one-time setup — see "Database Setup" below)
mysql -u root -p < backend/db/V1__iam_tables.sql
# ... run all 12 V*.sql files in order ...

# 3. Backend (in 11 separate terminals OR use IntelliJ run configs)
cd backend
mvn clean install -DskipTests
# Then start each service individually in this order:
#   discovery-server  →  api-gateway  →  iam-service  →  others
mvn spring-boot:run -pl discovery-server

# 4. Frontend
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173** and login with the default admin credentials below.

---

## Database Setup

### 1. Install MySQL 8.0+ and create the database

```sql
CREATE DATABASE payroute_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'payroute'@'localhost' IDENTIFIED BY 'payroute123';
GRANT ALL PRIVILEGES ON payroute_db.* TO 'payroute'@'localhost';
FLUSH PRIVILEGES;
```

### 2. Run the migrations in order

Apply the SQL files from `backend/db/` **in numerical order**:

```bash
cd backend/db
for f in V1__*.sql V2__*.sql V3__*.sql V4__*.sql V5__*.sql \
         V6__*.sql V7__*.sql V8__*.sql V9__*.sql V10__*.sql \
         V11__*.sql V12__*.sql; do
  mysql -u payroute -p payroute_db < "$f"
done
```

Or one by one:

```bash
mysql -u payroute -p payroute_db < V1__iam_tables.sql
mysql -u payroute -p payroute_db < V2__party_tables.sql
mysql -u payroute -p payroute_db < V3__payment_tables.sql
mysql -u payroute -p payroute_db < V4__routing_tables.sql
mysql -u payroute -p payroute_db < V5__ledger_tables.sql
mysql -u payroute -p payroute_db < V6__notification_tables.sql
mysql -u payroute -p payroute_db < V7__compliance_tables.sql
mysql -u payroute -p payroute_db < V8__exception_tables.sql
mysql -u payroute -p payroute_db < V9__settlement_tables.sql
mysql -u payroute -p payroute_db < V10__seed_data.sql
mysql -u payroute -p payroute_db < V11__account_aliases.sql
mysql -u payroute -p payroute_db < V12__sla_config.sql
```

### 3. Update DB credentials (if needed)

If you changed the username/password, update them in **each** service's `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/payroute_db
    username: payroute
    password: payroute123
```

---

## Running the Backend

### Build everything once

```bash
cd backend
mvn clean install -DskipTests
```

### Start order (important!)

Services **must** start in this order so registry / auth dependencies are up:

```
1. discovery-server   (8761)  ← others register here
2. api-gateway        (9080)  ← entry point
3. iam-service        (8081)  ← issues JWTs that others validate
4. party-service      (8082)
5. ledger-service     (8085)  ← party-service needs this for opening-balance seeding
6. payment-service    (8083)
7. routing-service    (8084)
8. notification-service (8086)
9. compliance-service (8087)
10. exception-service (8088)
11. settlement-service (8089)
```

### Run each service

In **separate terminals** (or use your IDE's run configurations):

```bash
mvn spring-boot:run -pl discovery-server
mvn spring-boot:run -pl api-gateway
mvn spring-boot:run -pl iam-service
mvn spring-boot:run -pl party-service
mvn spring-boot:run -pl ledger-service
mvn spring-boot:run -pl payment-service
mvn spring-boot:run -pl routing-service
mvn spring-boot:run -pl notification-service
mvn spring-boot:run -pl compliance-service
mvn spring-boot:run -pl exception-service
mvn spring-boot:run -pl settlement-service
```

### Verify everything is up

Open the **Eureka dashboard** at http://localhost:8761 — you should see all 10 client services registered.

---

## Running the Frontend

```bash
cd frontend
npm install            # one-time
npm run dev
```

Vite serves at **http://localhost:5173** with hot reload.

API requests are proxied to the gateway at `http://localhost:9080/api/v1` automatically (configured in `vite.config.ts`).

### Available scripts

| Command | What it does |
|---------|--------------|
| `npm run dev` | Start Vite dev server |
| `npm run build` | Type-check + production build |
| `npm run preview` | Preview the production build |
| `npm run lint` | Run ESLint |
| `npm test` | Run all Vitest tests once |
| `npm run test:watch` | Run tests in watch mode |
| `npm run test:ui` | Vitest UI dashboard |
| `npm run test:coverage` | Generate coverage report |
| `npm run docs` | Generate TypeDoc HTML at `docs/index.html` |

---

## Default Credentials

After running the seed migration (`V10__seed_data.sql`), one admin account is created:

| Field | Value |
|-------|-------|
| **Username** | `admin` |
| **Password** | `Admin@123` |
| **Role** | `ADMIN` |
| **Email** | `admin@payroute.com` |

> ⚠️ The admin user has `mustChangePassword=true` on first login, so you'll be redirected to set a new password before accessing the app.

To create users for the other roles, log in as admin → **User Management** → **+ Create User**.

---

## Project Structure

```
payroute-hub/
├── backend/
│   ├── pom.xml                          # Parent Maven POM
│   ├── db/                              # SQL migrations (V1..V12)
│   ├── discovery-server/                # Eureka
│   ├── api-gateway/                     # Spring Cloud Gateway
│   ├── iam-service/                     # Users, JWT, roles
│   ├── party-service/                   # Parties + accounts + beneficiaries
│   ├── payment-service/                 # Payment lifecycle
│   ├── routing-service/                 # Rail selection
│   ├── ledger-service/                  # Double-entry ledger + fees
│   ├── notification-service/            # In-app notifications
│   ├── compliance-service/              # KYC/AML, holds
│   ├── exception-service/               # Returns + recon + exceptions
│   └── settlement-service/              # Settlement batches + reports
└── frontend/
    ├── package.json
    ├── vite.config.ts                   # Vite + Vitest config
    ├── typedoc.json                     # TypeDoc config
    └── src/
        ├── api/                         # Axios wrappers per service
        ├── components/                  # Shared components
        ├── pages/                       # Route screens (per role/feature)
        ├── stores/                      # Zustand stores
        ├── types/                       # Shared TypeScript types
        ├── utils/                       # paymentRef, emailValidation, PDFs
        └── test/setup.ts                # Vitest global setup
```

---

## Testing

### Backend

```bash
cd backend

# Run all tests across all services
mvn test

# Run tests for a specific service
mvn test -pl ledger-service

# Run with coverage (generates JaCoCo XML + HTML)
mvn verify -pl ledger-service,party-service -DskipITs
```

**Test counts:**
- `party-service`: 92 tests
- `ledger-service`: 93 tests

### Frontend

```bash
cd frontend

# Run all tests once (CI mode)
npm test

# Watch mode (re-runs on file change)
npm run test:watch

# Coverage report
npm run test:coverage
```

**Test counts:** 101 tests across 7 test files

| Test file | Tests |
|-----------|-------|
| `emailValidation.test.ts` | 28 |
| `paymentRef.test.ts` | 14 |
| `reportPdf.test.ts` | 9 |
| `statementPdf.test.ts` | 9 |
| `Sidebar.test.tsx` | 13 |
| `authStore.test.ts` | 12 |
| `ProtectedRoute.test.tsx` | 16 |

---

## Code Coverage

### Backend (JaCoCo)

After running `mvn verify`, open the HTML report:

```bash
# Windows
start backend/ledger-service/target/site/jacoco/index.html
start backend/party-service/target/site/jacoco/index.html

# macOS / Linux
open backend/ledger-service/target/site/jacoco/index.html
```

**Current coverage:**
- `party-service`: ~85%
- `ledger-service`: ~83%

### Frontend (Vitest + V8)

```bash
cd frontend
npm run test:coverage
# Open coverage/index.html in your browser
```

---

## Static Analysis (SonarQube)

### 1. Start SonarQube via Docker

```bash
docker run -d --name sonarqube -p 9000:9000 sonarqube:lts-community
```

Open **http://localhost:9000** (default login: `admin` / `admin`, change on first login).

### 2. Create projects in the UI

- Project key: `payroute-hub-backend` → generate token
- Project key: `payroute-hub-frontend` → generate token

### 3. Run the scan

**Backend:**

```bash
cd backend
mvn clean verify sonar:sonar \
  -pl ledger-service,party-service \
  -Dsonar.token=YOUR_BACKEND_TOKEN
```

**Frontend** (requires `npm install -g sonarqube-scanner`):

```bash
cd frontend
npm run test:coverage           # generate lcov first
sonar-scanner -Dsonar.token=YOUR_FRONTEND_TOKEN
```

Results appear at http://localhost:9000.

---

## API Documentation

Each backend service exposes Swagger UI at:

```
http://localhost:<port>/swagger-ui.html
```

For example:
- IAM service: http://localhost:8081/swagger-ui.html
- Payment service: http://localhost:8083/swagger-ui.html
- Ledger service: http://localhost:8085/swagger-ui.html

The raw OpenAPI JSON is at `/v3/api-docs` on each service.

---

## Code Documentation (Javadoc / TypeDoc)

### Backend Javadoc

```bash
cd backend
mvn javadoc:javadoc -pl ledger-service,party-service

# Open the HTML
start ledger-service/target/reports/apidocs/index.html
start party-service/target/reports/apidocs/index.html
```

### Frontend TypeDoc

```bash
cd frontend
npm run docs

# Open the HTML
start docs/index.html
```

Scoped to `src/{api,utils,stores,types}` — covers the API surface area, skips UI components.

---

## Troubleshooting

### "Cannot connect to MySQL"
Verify MySQL is running and the credentials in each service's `application.yml` match what you set up. Default: `payroute / payroute123`.

### "Service not registered with Eureka"
Make sure `discovery-server` is started **first** and is up on port 8761 before starting other services. Check the Eureka dashboard at http://localhost:8761.

### "401 Unauthorized" from frontend
JWT may have expired. Log out and log back in. Tokens live in `localStorage` under `accessToken` / `refreshToken`.

### "Port already in use"
Another process is using the port. Find and kill it:

```bash
# Windows
netstat -ano | findstr :8081
taskkill /PID <pid> /F

# macOS / Linux
lsof -i :8081
kill -9 <pid>
```

### Maven build fails on Lombok / MapStruct
Run `mvn clean install -U -DskipTests` to refresh dependencies and regenerate annotation-processor output.

### Frontend tests can't find AntD
This is handled by `src/test/setup.ts` which polyfills `matchMedia`, `ResizeObserver`, and `IntersectionObserver`. If you add a test that uses a new AntD component and it crashes, check that file.

### "Eureka client cannot find peer" warnings
These are harmless on a single-node setup. Ignore them.

---

## Contributing

This is a portfolio / learning project. If you fork it:

1. Create a feature branch: `git checkout -b feature/your-thing`
2. Run tests before pushing: `mvn test` and `npm test`
3. Open a PR with a description of the change

---

## License

This project is provided as-is for learning and demonstration purposes.

---

## Author

Built by **Shivang Agrawal**
GitHub: [@shivang1311](https://github.com/shivang1311)

---

> _PayRoute Hub — payments, orchestrated end-to-end._
