# PayRoute Hub — Architecture & Flow Diagrams

End-to-end architecture, request flows, and module structure for the PayRoute Hub
enterprise payments platform. Use this document for onboarding, design reviews,
and interview/evaluation walkthroughs.

---

## Table of Contents
1. [High-Level Architecture](#1-high-level-architecture)
2. [Login & Authentication Flow](#2-login--authentication-flow)
3. [Create Payment Flow (End-to-End)](#3-create-payment-flow-end-to-end)
4. [Single Microservice Structure](#4-single-microservice-structure)
5. [User Roles & Permissions](#5-user-roles--permissions)
6. [Frontend Project Structure](#6-frontend-project-structure)
7. [Service Port Reference](#7-service-port-reference)
8. [Key Annotations Cheatsheet](#8-key-annotations-cheatsheet)

---

## 1. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          BROWSER (React)                             │
│              http://localhost:3000  (Vite Dev Server)                │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  Login → Dashboard → Payments → Accounts → Audit → Settings   │  │
│  │  - Zustand store (auth)  - Axios (HTTP)  - Ant Design (UI)    │  │
│  └───────────────────────────────────────────────────────────────┘  │
└────────────────────────────────┬────────────────────────────────────┘
                                 │  All HTTP requests
                                 │  Authorization: Bearer <JWT>
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    API GATEWAY  (port 9080)                          │
│                    Spring Cloud Gateway (reactive)                   │
│                                                                      │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │  JwtAuthenticationFilter (GlobalFilter, order = -1)         │   │
│   │   1. Skip /api/v1/auth/login, /register, /refresh           │   │
│   │   2. Extract "Bearer <token>" from header                   │   │
│   │   3. JwtUtil.validateToken() - verify HMAC + expiry         │   │
│   │   4. Inject X-User-Id, X-User-Role, X-Party-Id headers      │   │
│   │   5. Route via lb://service-name (Eureka load balanced)     │   │
│   └─────────────────────────────────────────────────────────────┘   │
└──┬───────┬─────────┬────────┬─────────┬────────┬────────┬───────────┘
   │       │         │        │         │        │        │
   ▼       ▼         ▼        ▼         ▼        ▼        ▼
┌─────┐ ┌─────┐ ┌──────┐ ┌────────┐ ┌──────┐ ┌──────┐ ┌────────┐
│ IAM │ │PARTY│ │PAYMNT│ │ROUTING │ │LEDGER│ │NOTIF │ │COMPLNCE│  ...
│8081 │ │8082 │ │ 8083 │ │  8084  │ │ 8085 │ │ 8086 │ │  8087  │
└──┬──┘ └──┬──┘ └───┬──┘ └────┬───┘ └───┬──┘ └──┬───┘ └────┬───┘
   │       │        │         │         │        │          │
   └───────┴────────┴─────────┴─────────┴────────┴──────────┘
                              │
                              ▼
        ┌──────────────────────────────────────────────┐
        │      EUREKA DISCOVERY (port 8761)            │
        │   - All services register on startup         │
        │   - Heartbeat every 30s                      │
        │   - Gateway queries Eureka for live instances│
        └──────────────────────────────────────────────┘
                              │
                              ▼
        ┌──────────────────────────────────────────────┐
        │             MySQL Database                   │
        │              payroute_hub                    │
        │   Tables: users, parties, accounts,          │
        │   payments, ledger_entries, audit_logs ...   │
        └──────────────────────────────────────────────┘

                   OBSERVABILITY (cross-cutting)
        ┌────────────┐  ┌────────────┐  ┌────────────┐
        │  Zipkin    │  │ Actuator   │  │ SonarQube  │
        │  :9411     │  │ /actuator/ │  │  :9000     │
        │ tracing    │  │  health    │  │  coverage  │
        └────────────┘  └────────────┘  └────────────┘
```

---

## 2. Login & Authentication Flow

```
┌──────────┐                ┌──────────┐                ┌──────────┐
│ Browser  │                │ Gateway  │                │   IAM    │
│ (React)  │                │  :9080   │                │  :8081   │
└────┬─────┘                └────┬─────┘                └────┬─────┘
     │                           │                           │
     │  1. POST /api/v1/auth/    │                           │
     │     login                 │                           │
     │     {username, password}  │                           │
     ├──────────────────────────►│                           │
     │                           │  Open path - skip JWT     │
     │                           │                           │
     │                           │  2. Forward to IAM        │
     │                           ├──────────────────────────►│
     │                           │                           │
     │                           │              ┌────────────┴────┐
     │                           │              │ AuthService     │
     │                           │              │ .login()        │
     │                           │              │                 │
     │                           │              │ - Find user     │
     │                           │              │ - bcrypt verify │
     │                           │              │ - Generate JWT  │
     │                           │              │ - Save refresh  │
     │                           │              │   token in DB   │
     │                           │              │ - Audit log     │
     │                           │              └────────────┬────┘
     │                           │                           │
     │                           │  3. AuthResponse          │
     │                           │  {accessToken,            │
     │                           │   refreshToken, role,...} │
     │                           │◄──────────────────────────┤
     │ 4. Same response          │                           │
     │◄──────────────────────────┤                           │
     │                           │                           │
     │ 5. Store in localStorage  │                           │
     │    + Zustand authStore    │                           │
     │                           │                           │
     │ 6. Redirect to /dashboard │                           │

  ▼ Subsequent requests (every API call)

     │  GET /api/v1/parties      │                           │
     │  Header:                  │                           │
     │  Authorization: Bearer    │                           │
     │  eyJhbGc...               │                           │
     ├──────────────────────────►│                           │
     │                           │ 7. JwtAuthFilter:         │
     │                           │   - Verify signature      │
     │                           │   - Check exp             │
     │                           │   - Extract claims        │
     │                           │   - Inject headers:       │
     │                           │     X-User-Role: CUSTOMER │
     │                           │     X-Party-Id: 30        │
     │                           │                           │
     │                           │ 8. Route to party-svc     │
     │                           ├─────────► party-svc       │
     │                           │           reads headers   │
     │                           │           enforces auth   │
     │                           │◄──────────                │
     │ 9. Response               │                           │
     │◄──────────────────────────┤                           │

  ⏰ After 15 minutes - token expires

     │  Any API call (token old) │                           │
     ├──────────────────────────►│ 401 Unauthorized          │
     │◄──────────────────────────┤ (ExpiredJwtException)     │
     │                           │                           │
     │ Axios interceptor catches │                           │
     │ 401 →                     │                           │
     │                           │                           │
     │  POST /auth/refresh       │                           │
     │  {refreshToken: "uuid"}   │                           │
     ├──────────────────────────►├──────────────────────────►│
     │                           │              Find UUID    │
     │                           │              in DB →      │
     │                           │              issue new    │
     │                           │              JWT pair     │
     │                           │◄──────────────────────────┤
     │ New accessToken           │                           │
     │◄──────────────────────────┤                           │
     │                           │                           │
     │ Retry original request    │                           │
     │ with new token            │                           │
     ├──────────────────────────►│ ...                       │
```

### Key implementation files
| File | Role |
|------|------|
| `iam-service/JwtService.java` | Signs tokens with HMAC-SHA256 |
| `iam-service/AuthService.java` | `login()`, `refreshToken()`, persists refresh tokens |
| `api-gateway/JwtUtil.java` | Verifies signature + expiry |
| `api-gateway/JwtAuthenticationFilter.java` | Global filter on every request |
| `frontend/src/api/axios.ts` | Interceptor that handles 401 → silent refresh |

### Token lifetimes
- Access token: **15 minutes** (`access-token-expiration-ms: 900000`)
- Refresh token: **7 days** (`refresh-token-expiration-ms: 604800000`)

---

## 3. Create Payment Flow (End-to-End)

```
┌─────────┐   ┌────────┐   ┌─────────┐   ┌────────┐   ┌───────┐   ┌───────┐
│ Browser │   │Gateway │   │Payment  │   │Routing │   │Ledger │   │ Notif │
│         │   │ :9080  │   │ :8083   │   │ :8084  │   │ :8085 │   │ :8086 │
└────┬────┘   └────┬───┘   └────┬────┘   └───┬────┘   └───┬───┘   └───┬───┘
     │             │            │            │            │           │
     │ 1. User     │            │            │            │           │
     │ submits     │            │            │            │           │
     │ form        │            │            │            │           │
     │             │            │            │            │           │
     │ POST        │            │            │            │           │
     │ /payments   │            │            │            │           │
     ├────────────►│            │            │            │           │
     │  JWT in hdr │JWT verify  │            │            │           │
     │             │+ inject hdr│            │            │           │
     │             ├───────────►│            │            │           │
     │             │            │ 2. Validate│            │           │
     │             │            │  ownership │            │           │
     │             │            │ (Feign →   │            │           │
     │             │            │ party-svc) │            │           │
     │             │            │            │            │           │
     │             │            │ 3. Save    │            │           │
     │             │            │ payment    │            │           │
     │             │            │ INITIATED  │            │           │
     │             │            │            │            │           │
     │             │            │ 4. Routing │            │           │
     │             │            ├───────────►│            │           │
     │             │            │  (Feign)   │ Pick rail  │           │
     │             │            │            │ Apply rule │           │
     │             │            │            │ Set status │           │
     │             │            │            │ → ROUTED   │           │
     │             │            │◄───────────┤            │           │
     │             │            │                         │           │
     │             │            │ 5. Post ledger entries  │           │
     │             │            ├────────────────────────►│           │
     │             │            │  (Feign)                │           │
     │             │            │              ┌──────────┴────────┐  │
     │             │            │              │ @Transactional    │  │
     │             │            │              │ SERIALIZABLE      │  │
     │             │            │              │                   │  │
     │             │            │              │ DEBIT  debtor     │  │
     │             │            │              │ CREDIT creditor   │  │
     │             │            │              │ FEE  (via         │  │
     │             │            │              │   FeeService)     │  │
     │             │            │              │                   │  │
     │             │            │              │ Skip FEE if UPI   │  │
     │             │            │              └──────────┬────────┘  │
     │             │            │◄───────────────────────┤            │
     │             │            │                                     │
     │             │            │ 6. Payment → COMPLETED              │
     │             │            │                                     │
     │             │            │ 7. Trigger notification             │
     │             │            ├────────────────────────────────────►│
     │             │            │  (Feign)                            │
     │             │            │                          ┌──────────┴──┐
     │             │            │                          │ Insert      │
     │             │            │                          │ in_app_notif│
     │             │            │                          │ row + fire  │
     │             │            │                          │ webhook     │
     │             │            │                          └──────────┬──┘
     │             │            │◄────────────────────────────────────┤
     │             │            │                                     │
     │             │ 8. Response│                                     │
     │             │◄───────────┤                                     │
     │ 9. Success  │            │                                     │
     │◄────────────┤            │                                     │
     │             │            │                                     │
     │ 10. UI shows│            │                                     │
     │ payment     │            │                                     │
     │ confirmation│            │                                     │
```

### Ledger entry rules (double-entry bookkeeping)
| Entry Type | Effect on balance |
|---|---|
| `CREDIT` | +amount (money in) |
| `REVERSAL` | +amount (offsets original) |
| `DEBIT` | -amount (money out) |
| `FEE` | -amount (charged to debtor) |
| `TAX` | -amount |

UPI payments skip the FEE leg (zero-fee per product policy).

---

## 4. Single Microservice Structure

Every microservice follows the same package layout — this is `payment-service` as an example.

```
┌────────────────────────────────────────────────────────────┐
│                  payment-service / port 8083               │
└────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌────────────────────────────────────────────────────────────┐
│  controller/                                               │
│     PaymentController                                      │
│       @RestController                                      │
│       @RequestMapping("/api/v1/payments")                  │
│       - Reads X-User-Role header                           │
│       - Delegates to service                               │
└──────────────────────┬─────────────────────────────────────┘
                       ▼
┌────────────────────────────────────────────────────────────┐
│  service/                                                  │
│     PaymentService                                         │
│       @Service                                             │
│       @Transactional                                       │
│       - Business logic                                     │
│       - Calls Feign clients for other services             │
└────────┬──────────────────────────────────┬────────────────┘
         ▼                                  ▼
┌────────────────────┐         ┌────────────────────────────┐
│  repository/       │         │  client/                   │
│  PaymentRepository │         │  LedgerServiceClient       │
│   extends          │         │  PartyServiceClient        │
│   JpaRepository    │         │  RoutingServiceClient      │
│                    │         │   @FeignClient(name=...)   │
└─────────┬──────────┘         └──────────────┬─────────────┘
          ▼                                   ▼
┌────────────────────┐         ┌────────────────────────────┐
│  entity/           │         │  Other microservices       │
│  Payment           │         │  (via Eureka load balancer)│
│   @Entity          │         └────────────────────────────┘
│   @EntityListeners │
│   (Auditing)       │
│  PaymentStatus     │  ◄── enum: INITIATED, ROUTED,
│   @Enumerated      │              SETTLED, FAILED, ...
│   (STRING)         │
└────────────────────┘

dto/        → request/response objects
mapper/     → MapStruct entity ↔ DTO
exception/  → custom exceptions + GlobalExceptionHandler
config/     → @Configuration beans, CORS, etc.
```

---

## 5. User Roles & Permissions

```
                    ┌──────────────┐
                    │  USER LOGS   │
                    │     IN       │
                    └──────┬───────┘
                           │
                           │  JWT.role
                           ▼
        ┌──────────────────┴──────────────────┐
        │                                     │
        ▼                                     ▼
┌──────────────┐                  ┌──────────────────┐
│   CUSTOMER   │                  │   ADMIN / OPS    │
└──────┬───────┘                  └────────┬─────────┘
       │                                   │
       │ Can do:                           │ Can do:
       │ - View OWN accounts only          │ - View ALL accounts
       │ - Create payments                 │ - Create payments for any
       │   (own → beneficiary)             │   party
       │ - View own statements             │ - Configure fee schedules
       │ - Manage beneficiaries            │ - View audit logs
       │ - View notifications              │ - Export GL ledger
       │                                   │ - Manage users
       │ Cannot:                           │ - Trigger reversals
       │ - See other parties               │ - Manage routing rules
       │ - Export full ledger              │
       │ - Access /admin/* routes          │ Backend enforcement:
       │                                   │ - enforceAccountOwnership()
       │ Backend enforcement:              │   skipped for non-CUSTOMER
       │ - LedgerController checks         │
       │   X-User-Role + X-Party-Id        │
       │   against account.partyId         │
       │ - Calls party-service via Feign   │
       │   to verify ownership             │
```

### Other roles
- **COMPLIANCE** — view-only access to compliance dashboards, can release holds
- **RECONCILIATION** — view ledger + reconciliation reports, no write access

---

## 6. Frontend Project Structure

```
frontend/
├── src/
│   ├── api/                 ← Axios clients per service
│   │   ├── axios.ts         (interceptors, 401 → refresh logic)
│   │   ├── auth.api.ts
│   │   ├── party.api.ts
│   │   ├── ledger.api.ts
│   │   └── ...
│   │
│   ├── stores/              ← Zustand global state
│   │   ├── authStore.ts     (user, token, role)
│   │   └── themeStore.ts    (light/dark)
│   │
│   ├── pages/               ← Route-level components
│   │   ├── auth/            (Login, Register)
│   │   ├── dashboard/       (Customer + Admin dashboards)
│   │   ├── payments/        (Create, List, Scheduled)
│   │   ├── parties/         (Parties, AccountDirectory)
│   │   ├── ledger/          (Statements, AccountSummary)
│   │   └── admin/           (AuditLog, UserMgmt)
│   │
│   ├── components/          ← Reusable UI
│   │   ├── layout/          (Sidebar, Header)
│   │   └── common/          (DataTable, FilterBar)
│   │
│   ├── types/               ← TypeScript interfaces
│   ├── utils/               ← validation, formatting
│   ├── routes/              ← React Router config + guards
│   └── App.tsx
│
├── package.json             (React 18, AntD, Zustand, Vite, TS)
├── vite.config.ts
└── tsconfig.json
```

### Tech stack
| Layer | Library |
|------|---------|
| Framework | React 18 + TypeScript |
| Build tool | Vite |
| UI components | Ant Design |
| State | Zustand (global), React state (local) |
| HTTP | Axios with interceptors |
| Routing | React Router v6 with guards |
| Charts | Recharts |
| Date | dayjs |

---

## 7. Service Port Reference

| Port | Service | Responsibility |
|------|---------|----------------|
| 3000 | Frontend (Vite) | React UI |
| 8761 | discovery-server | Eureka — service registry |
| 9080 | api-gateway | Single entry point, JWT auth, routing |
| 8081 | iam-service | Auth, users, roles, audit |
| 8082 | party-service | Parties, accounts, directory |
| 8083 | payment-service | Payment initiation, validation |
| 8084 | routing-service | Rail selection, orchestration |
| 8085 | ledger-service | Posting, fees, statements |
| 8086 | notification-service | In-app notifications, webhooks |
| 8087 | compliance-service | Screening, holds |
| 8088 | exception-service | Returns, reconciliation |
| 8089 | settlement-service | Settlement batches |
| 9000 | SonarQube | Code quality + coverage |
| 9411 | Zipkin | Distributed tracing UI |

---

## 8. Key Annotations Cheatsheet

### Spring core
| Annotation | Purpose |
|---|---|
| `@SpringBootApplication` | Main class — combines @Configuration + @EnableAutoConfiguration + @ComponentScan |
| `@RestController` | Controller that returns JSON, not views |
| `@Service` | Business logic bean |
| `@Repository` | DAO bean — translates JPA exceptions |
| `@Component` | Generic Spring-managed bean |
| `@Autowired` / constructor injection | Wire dependencies |
| `@Value("${prop}")` | Inject config property |

### REST mapping
| Annotation | Purpose |
|---|---|
| `@RequestMapping("/path")` | Base path on a class |
| `@GetMapping` / `@PostMapping` / etc. | HTTP verb mappings |
| `@PathVariable` | Path parameter (`/users/{id}`) |
| `@RequestParam` | Query string parameter (`?page=0`) |
| `@RequestBody` | JSON request body → object |
| `@RequestHeader` | Read a specific header |

### JPA / Persistence
| Annotation | Purpose |
|---|---|
| `@Entity` | Marks class as JPA entity (table) |
| `@Id` + `@GeneratedValue` | Primary key + auto-generation strategy |
| `@Column(nullable=false, length=...)` | Column metadata |
| `@Enumerated(EnumType.STRING)` | Store enum as text (NEVER use ORDINAL) |
| `@EntityListeners(AuditingEntityListener.class)` | Auto-fill @CreatedDate / @LastModifiedDate |
| `@CreatedDate` / `@LastModifiedDate` | Auto-managed audit timestamps |
| `@OneToMany` / `@ManyToOne` | Relationships |
| `@Transactional` | Wrap method in a DB transaction |
| `@Transactional(isolation=SERIALIZABLE)` | Strictest isolation — used in LedgerService |

### Validation
| Annotation | Purpose |
|---|---|
| `@Valid` | Trigger validation on `@RequestBody` |
| `@NotNull` / `@NotBlank` / `@Size` / `@Pattern` | Constraints on DTO fields |
| `@Email` | Validates email format |

### Spring Cloud
| Annotation | Purpose |
|---|---|
| `@EnableEurekaServer` | Discovery server |
| `@EnableEurekaClient` (implicit) | Register with Eureka |
| `@EnableFeignClients` | Scan for Feign client interfaces |
| `@FeignClient(name="service-name")` | Declarative HTTP client; name resolves via Eureka |
| `@LoadBalanced` | Tag a RestTemplate to use Eureka load balancing |

### Lombok
| Annotation | Purpose |
|---|---|
| `@Data` | Getters + setters + toString + equals + hashCode |
| `@Builder` | Generates builder pattern |
| `@RequiredArgsConstructor` | Constructor for final fields (preferred over @Autowired) |
| `@Slf4j` | Adds `log` field for logging |

### Testing
| Annotation | Purpose |
|---|---|
| `@SpringBootTest` | Full Spring context for integration tests |
| `@DataJpaTest` | JPA-only slice; uses H2 in-memory DB |
| `@WebMvcTest(Controller.class)` | Controller-only slice |
| `@ExtendWith(MockitoExtension.class)` | Plain Mockito unit tests |
| `@Mock` / `@InjectMocks` | Mockito fixture |

---

## Quick links

- Login flow → [Section 2](#2-login--authentication-flow)
- API Gateway JWT verification → [Section 2 — Key implementation files](#2-login--authentication-flow)
- Microservice template → [Section 4](#4-single-microservice-structure)
- All port numbers → [Section 7](#7-service-port-reference)
- Annotations reference → [Section 8](#8-key-annotations-cheatsheet)
