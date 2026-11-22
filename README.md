# Claim Management System — Healthcare Revenue Cycle Intelligence

> [!IMPORTANT]
> **Educational & Portfolio Demonstration Notice**: ClaimFlow is an educational, synthetic healthcare revenue cycle management (RCM) application built to demonstrate enterprise full-stack architecture, double-entry ledger financial reconciliation, automated EDI 835 remittance ingestion, and revenue cycle intelligence. It is **not** certified HIPAA-compliant production software, nor an official clearinghouse or certified ANSI ASC X12 engine. All patient names, medical record numbers (MRNs), policy identifiers, and claim transactions are completely synthetic.

Healthcare professional claim management: capture, workflow, audit trail, double-entry financial reconciliation, and revenue cycle intelligence.

**Java 21 · Spring Boot 4.1.1 · PostgreSQL 17 · React 19 + TypeScript · Docker Compose · Flyway V1–V9 · 147 Backend Tests**

| Document | What's in it |
|---|---|
| [`PLAN.md`](PLAN.md) | The build plan, phase split and technology decisions |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | 15 diagrams: context, containers, components, ERD, state machine, sequences, deployment |
| [`docs/PHASE2_HANDOFF.md`](docs/PHASE2_HANDOFF.md) | Specifications for the six features deliberately left unbuilt |
| [`docs/API.md`](docs/API.md) | Endpoint reference |

---

## Run it

Docker is the only prerequisite. No local JDK, Maven, Node or Postgres needed.

```bash
docker compose up --build
```

First build pulls images and resolves dependencies, so expect 3–5 minutes. After that, startup is about 30 seconds.

| | |
|---|---|
| **Application** | http://localhost:9000 |
| **Swagger UI** | http://localhost:9000/swagger-ui.html |
| **API, direct** | http://localhost:9090/api |
| **Health** | http://localhost:9090/actuator/health |
| **Postgres** | `localhost:54321`, database `claims`, user `claims`, password `claims` |

### Demo accounts

| Username | Password | Role | Can do |
|---|---|---|---|
| `admin` | `admin123` | ADMIN | Everything, including deletes and reference-data changes |
| `biller` | `biller123` | BILLER | Create and edit drafts, drive status transitions |
| `dana` | `biller123` | BILLER | Same as above, a second biller for the audit trail |
| `viewer` | `viewer123` | VIEWER | Read only — the API rejects mutations, not just the UI |

### Stop it

```bash
docker compose down          # keep the data
docker compose down -v       # wipe the database volume and re-seed on next start
```

---

## Five-minute demo script

1. **Sign in** as `biller / biller123`. The dashboard shows 12 seeded claims spread across every status, with charged, collected and outstanding AR.

2. **Filter.** Go to *All claims*, filter to `DRAFT`. Two claims. Clear, filter by payer, filter by service date. Filters combine and are reflected in the URL, so a filtered view is shareable.

3. **Open `CLM-2026-000010`** — a draft. Note the audit trail at the bottom, and that the only available transitions are `SUBMITTED` and `VOID`, because the server computed those from the state machine.

4. **Submit it.** The claim moves to `SUBMITTED`, `submitted_at` is stamped, the badge and button set change, and a new row appears in the timeline. Walk it on: `ACCEPTED`, then `PAID` with an allowed amount of 265.00 and a paid amount of 212.00. Patient responsibility computes to 53.00 and outstanding drops to zero.

5. **Try to break it.** With the claim now `PAID`, `PUT /api/claims/10` returns **409**: *"Claim CLM-2026-000010 is PAID and can no longer be edited."* Terminal means terminal.

6. **Show the role boundary.** Sign in as `viewer / viewer123`. Action buttons are gone. Then prove it is not cosmetic:

   ```bash
   TOKEN=$(curl -s localhost:9090/api/auth/login -H 'Content-Type: application/json' \
     -d '{"username":"viewer","password":"viewer123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

   curl -i -X POST localhost:9090/api/claims/11/transition \
     -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -d '{"targetStatus":"SUBMITTED"}'
   # HTTP/1.1 403 Forbidden
   ```

7. **Explore the Revenue Cycle Intelligence & ASC X12 Modules.** Navigate to *Analytics* (`/analytics`) to inspect the real-time AR aging distribution, payer scorecards, and smart denial worklists. Navigate to *Claim Exports* (`/claim-exports`) to run pre-export validation and generate ASC X12 837P batch files. Navigate to *Documents* (`/documents`) to verify secure, audit-logged document attachments.

---

## Running without Docker

```bash
# Postgres
docker run -d --name cms-db -p 54321:5432 \
  -e POSTGRES_DB=claims -e POSTGRES_USER=claims -e POSTGRES_PASSWORD=claims postgres:17-alpine

# Backend (Java 21+). DB_PORT must match the host port above.
cd backend && DB_PORT=54321 mvn spring-boot:run

# Frontend (Node 20+)
cd frontend && npm install && npm run dev     # http://localhost:5173, proxies /api to :9090
```

For the Vite dev server, set `CORS_ALLOWED_ORIGINS=http://localhost:5173` for the backend. Under Docker it stays empty because nginx proxies `/api`, so the browser sees a single origin.

---

## What Phase 1 covers

- **Claims** — create, edit, delete drafts; service lines with CPT/HCPCS codes, modifiers and units; ICD-10 diagnoses with sequencing; server-computed totals
- **Workflow** — a nine-state machine with guards, side effects and an append-only audit trail
- **Reference data** — patients, payers, providers, and coordination-of-benefits coverage
- **Search** — paged and filtered by status, payer, patient, provider, service date and free text
- **Security** — JWT authentication, three roles, method-level authorisation
- **Schema** — Flyway migrations, `CHECK` constraints on every coded field, `NUMERIC(12,2)` money
- **API docs** — OpenAPI 3 and Swagger UI, Phase 2 stubs included and marked
- **Docker** — one command, health-gated startup ordering, multi-stage images, non-root runtime

## Phase 2 Completed Capabilities

### Milestone 2.1 — Real-Time Eligibility Verification (270/271)
- Deterministic 270 inquiry and 271 response simulation engine with policy caching.
- Benefit breakdown: in-network copays, deductibles, remaining amounts, and coinsurance.
- Direct verification integration on claims workflow.

### Milestone 2.2 — Denial Management and Multi-Level Appeals
- CARC/RARC denial root cause categorization and assignment.
- Multi-level appeal lifecycles (Level 1: Redetermination, Level 2: Reconsideration, Level 3: ALJ).
- Escalation workflows, overturn/upheld decisions, deadline tracking, and audit logging.

### Milestone 2.3A — Synthetic ERA 835 Remittance Ingestion
- EDI 835 parsing into remittance batches, claim vouchers, and CAS adjustment segments.
- Automatic batch-level and voucher-level debit/credit balance validation.
- Out-of-balance detection and exception capture.

### Milestone 2.3B1 — Safe Payer Payment Posting & Double-Entry Ledger
- Double-entry immutable `payment_ledger` tracking posted payments and contractual adjustments.
- Atomic posting workflows with state transitions (`PARTIALLY_PAID`, `PAID`).
- Flyway V6 database migration and strict negative/positive balance guards.

### Milestone 2.3B2 — Payment Reversals, AR Reconciliation & Claim Reopening
- Append-only `PAYER_REVERSAL` and `CONTRACTUAL_REVERSAL` engine preserving original audit history.
- Claim reopening transition: `PAID → PARTIALLY_PAID` or `SUBMITTED`.
- Segmented AR Engine reconciling Gross Charges = Net Payments + Write-Offs + Total Outstanding Exposure with zero discrepancies.
- Remittance exception resolution workflows (`RESOLVED`, `IGNORED`).
- Flyway V7 database migration with strict unique reversal constraints.

### Milestone 2.3C — Remittance Advice & Reconciliation Frontend
- Remittances dashboard (`/remittances`), synthetic ERA 835 import modal.
- Voucher detail view (`/remittances/:id`) with collapsible CAS adjustments and individual/batch posting.
- Exceptions worklist (`/remittances/exceptions`) with in-app resolution modal.
- Financial Reconciliation & AR Position panel and immutable Payment Ledger table on Claim Detail (`/claims/:id`).

### Milestone 2.4 — Revenue Cycle Intelligence & Analytics
- Complete RCM Analytics suite (`/analytics` and `/api/analytics/*`):
  - **Executive Overview**: Financial KPIs, operational counts, RCM efficiency metrics.
  - **AR Aging**: 5 intervals (0–30, 31–60, 61–90, 91–120, 120+ days) excluding VOID and DRAFT claims.
  - **Payer Performance**: Side-by-side billed, payments, write-offs, aging, and denial rate comparison.
  - **Payment Trends**: Monthly historical posting, reversals, and net adjustments.
  - **Denial & Appeal Intelligence**: CARC root-cause distribution and appeal escalation outcomes.
  - **Smart Priority Worklist**: Actionable, explainable prioritized claims with direct navigation.
  - **RFC 4180 CSV Export**: Defense against CSV spreadsheet formula injection (`=`, `+`, `-`, `@`).

### Milestone 2.5 — ASC X12 837P Professional Claim Export
- Educational subset generator compliant with ASC X12 005010X222A1 transaction structure:
  - **Interchange & Functional Enveloping**: `ISA`/`IEA` control headers, `GS`/`GE` functional group wrappers, `ST`/`SE` transaction control with computed segment counts.
  - **Hierarchical Loop Architecture**:
    - Loop 1000A (Submitter) & 1000B (Receiver).
    - Loop 2000A (Billing Provider HL-20) & Loop 2010AA (`NM1*85*2`, `N3`, `N4`, `REF*EI`, `PRV*BI`).
    - Loop 2000B (Subscriber HL-22) & Loop 2010BA (`NM1*IL*1`, `N3`, `N4`, `DMG`, `REF*SY`) and Loop 2010BB Payer (`NM1*PR*2`).
    - Loop 2300 (Claim Information): `CLM`, `DTP*431`, `DTP*472`, `HI*BK` diagnosis codes (ICD-10 primary & secondary).
    - Loop 2400 (Service Lines): `LX` line counters, `SV1` CPT/HCPCS procedure codes, modifiers, charge amounts, units, diagnosis pointers, `DTP*472` service dates.
  - **Pre-Export Validation Engine**: Multi-point rule verification (taxonomy codes, NPI-10 format, place-of-service 2-digit codes, ICD-10 diagnosis pointers, CPT codes) returning structured blockers.
  - **Batching & Rebill Protection**: Atomic batch creation, manifest tracking, duplicate export guards with explicit `allowRebill` override.
  - **Integrity & Export UI**: SHA-256 batch checksum calculation, raw `.837` file download, export KPIs, and interactive generation wizard (`/claim-exports`).

### Milestone 2.6 — Secure Claim Document Attachments
- Enterprise document management and compliance attachment pipeline:
  - **Isolated Storage**: Dedicated container volume mount (`attachments_data` -> `/app/data/attachments`) with sanitized, non-executable storage paths.
  - **Security Sniffer & Magic Byte Verification**: Multi-layered defense validating file extensions, declared MIME types, and strict byte-level magic signature sniffing (`%PDF-` for PDF, `\xFF\xD8\xFF` for JPEG, `\x89PNG\r\n\x1a\n` for PNG). Strictly rejects disguised executables (`MZ`, `ELF`, scripts) with HTTP 400.
  > [!IMPORTANT]
  > **Security Notice**: Magic-byte signature sniffing validates file header format markers to prevent basic payload spoofing; it is **not** a replacement for comprehensive anti-malware/antivirus scanning. In enterprise production deployments, uploads must be passed through a dedicated virus scanning engine (e.g. ClamAV, AWS GuardDuty) and quarantined before being made accessible.
  - **Cryptographic File Integrity**: Computes SHA-256 hash upon streaming ingestion and verifies bit-level integrity against tampered downloads.
  - **Logical Archival & Mandatory Audit Reasons**: Documents are never hard-deleted; logical archival captures the exact reason, timestamp, and user in an append-only `claim_attachment_audit` trail.
  - **Tenant & Cross-Claim Boundary Enforcement**: Strict isolation ensuring requests for attachments belonging to other claims return HTTP 404 (preventing IDOR information leakage).
  - **Document UI**: Integrated attachment tab on Claim Detail (`/claims/:id`), single/multi-file upload modal, audit drawer, and global searchable document repository (`/documents`).

---

## Financial Accounting & Double-Entry Ledger Principles

The ClaimFlow financial reconciliation engine adheres to mathematical invariants across every claim and system-wide portfolio rollup:

### 1. Invariant Portfolio Equation
$$\text{Gross Billed Charges} = \text{Net Recorded Payments} + \text{Contractual Write-Offs} + \text{Total Outstanding Exposure}$$
*Discrepancy must equal \$0.00 across all active and historical claims.*

- **Gross Billed Charges**: **\$9,490.00** across all 20 claims.
- **Net Recorded Payer Payments**: **\$1,271.52** (posted from ingested ERA 835 remittances less reversals).
  > [!NOTE]
  > **Remittance Advice vs. Cleared Cash**: ERA 835 posted payments reflect transaction credits communicated via payer Electronic Remittance Advice. They represent adjudicated remittance data rather than verified bank cash receipts; formal cash accounting recognition requires lockbox deposit matching and bank statement reconciliation.
- **Contractual Write-Offs**: **\$100.00** (`CO-45` adjustments).
- **Total Outstanding Exposure**: **\$8,118.48**.
- **Portfolio Balance Discrepancy**: **\$0.00** (`isBalanced: true`).

### 2. Mutually Exclusive Exposure Category Reconciliation
Every claim's remaining balance is partitioned into mutually exclusive, non-overlapping categories that sum exactly to **\$8,118.48** (\$0.00 category discrepancy):
1. **Confirmed Collectible AR**: **\$781.00**
   - **Adjudicated Payer AR**: **\$534.00** (balances on adjudicated claims awaiting secondary/supplemental settlement, e.g. Claim 5 & 6 allowed balances).
   - **Patient Responsibility AR**: **\$247.00** (adjudicated patient deductibles/copays on active collectible claims, e.g. Claims 3 & 8).
2. **Disputed Denial Exposure**: **\$1,650.00** (outstanding balances on active `DENIED` claims under appeal or review).
3. **Unadjudicated Submitted Exposure**: **\$3,316.00** (billed charges on submitted/accepted claims awaiting initial 835 remittance adjudication, including pre-adjudication variance).
4. **Rejected Claim Exposure**: **\$180.00** (claims rejected by EDI pre-screening or clearinghouse).
5. **Draft Claim Exposure**: **\$150.00** (unsubmitted drafts in preparation).
6. **Void Rescinded Exposure**: **\$1,450.00** (voided claims with rescinded billing liability).
7. **Legacy Unclassified Balances**: **\$591.48** (uncertain residual balances on closed/paid claims from legacy seeds, segregated from active collectible debt).

$$\sum \text{Categories} = \$781.00 + \$1,650.00 + \$3,316.00 + \$180.00 + \$150.00 + \$1,450.00 + \$591.48 = \$8,118.48$$
$$\text{Category Discrepancy} = \$8,118.48 - \$8,118.48 = \$0.00$$

### 3. Double-Entry Append-Only Audit Ledger
All payments and contractual adjustments are logged to an append-only `claim_payment_ledger` table with strict foreign key constraints:
- `PAYER_PAYMENT`: Credit to cash receipts, debit to receivable.
- `CONTRACTUAL_WRITE_OFF`: Contractual adjustment (`CO-45`) adjusting billed charges to payer allowed fee schedule.
- `PAYER_REVERSAL`: Append-only negative reversal offset with mandatory reason and original transaction reference.
- `CONTRACTUAL_REVERSAL`: Reversal of write-off associated with retracted payment.

---

## Visual Verification & Acceptance Screenshots

| View | Path / Route | Description |
|---|---|---|
| **Login Screen** | `/login` | Modern authentication interface with quick-fill demo credentials (`admin`, `biller`, `viewer`). |
| **Executive Dashboard** | `/` | Portfolio overview with KPI cards, aging distribution bar, and recent activity. |
| **Claims Worklist** | `/claims` | Server-side paginated claims table with real-time multi-dimensional filters. |
| **Claim Detail & Attachments** | `/claims/:id` | Full claim view, line-item CPT breakdown, financial reconciliation panel, payment ledger, and secure document attachments tab. |
| **EDI 837P Claim Export** | `/claim-exports` | ASC X12 837P export dashboard, validation drawer, batch generation wizard, and `.837` download. |
| **Global Documents Worklist** | `/documents` | Centralized repository of supporting clinical and administrative documents with category and keyword search. |
| **Remittance Advice** | `/remittances` | Ingested ERA 835 batches, batch balance status, and raw payload audit preview. |
| **Remittance Exceptions** | `/remittances/exceptions` | Out-of-balance, unmapped codes, and duplicate claim exception management worklist. |
| **Revenue Cycle Analytics** | `/analytics` | Multi-tab analytics dashboard with AR aging, payer performance, denial CARC breakdown, and smart priority worklist. |
| **Denials & Appeals** | `/denials` | Active denials tracking, CARC categorization, and multi-level appeal escalation management. |
| **Eligibility Verification** | `/claims/new` | Integrated 270/271 real-time benefit verification engine with deductible and copay breakdown. |

---

## Project layout

```
claim-management/
├── docker-compose.yml          three services, health-gated ordering
├── .env.example                every value has a working default
├── PLAN.md                     the build plan
├── docs/
│   ├── ARCHITECTURE.md         15 Mermaid diagrams
│   ├── PHASE2_HANDOFF.md       specs for the unbuilt features
│   └── API.md                  endpoint reference
├── backend/
│   ├── Dockerfile              maven build stage → JRE alpine runtime, non-root
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/claire/claims/
│       │   ├── domain/         entities + ClaimStatus (owns the transition table)
│       │   ├── repository/     Spring Data JPA
│       │   ├── service/        ClaimService, ClaimStatusMachine, mappers, specifications
│       │   ├── web/            REST controllers
│       │   ├── dto/            Java records
│       │   ├── security/       JWT config, user details, current-user provider
│       │   ├── common/         exceptions + RFC 9457 handler
│       │   └── phase2/         Phase 2 controller markers
│       └── resources/
│           ├── application.yml
│           └── db/migration/   V1__schema.sql through V9__claim_document_attachments.sql
└── frontend/
    ├── Dockerfile              node build stage → nginx alpine runtime
    ├── nginx.conf              SPA fallback + /api proxy
    └── src/
        ├── api/                typed client, RFC 9457 unwrapping
        ├── auth/               token handling and role context
        ├── components/         layout and shared UI
        └── pages/              dashboard, claims, claim-exports, documents, analytics, remittances, denials
```

---

## Design decisions worth knowing

**The state machine has one home.** `ClaimStatus` (an enum) owns the transition table. `ClaimStatusMachine` is the only class that changes a claim's status. `GET /api/reference/status-transitions` serialises that same enum, and the React UI renders its buttons from `claim.allowedTransitions` on the response. The UI cannot offer an action the API will refuse.

**Totals are never trusted from the client.** `claim.total_charge` is recomputed from the service lines on every write. The form shows a running total for the user's benefit; the server ignores it.

**Only drafts are editable.** Anything past `DRAFT` is an immutable billing record. Corrections go `REJECTED → DRAFT`, or void and re-file — the same discipline a real clearinghouse enforces. Attempts return 409 with an explanation.

**Errors have one shape.** Five layers can reject a request — JWT filter, role check, bean validation, business rules, database constraints — and all five return RFC 9457 `application/problem+json`. Validation failures carry per-field detail.

**Complete end-to-end implementation with 147 automated tests.** All Phase 1 and Phase 2 modules (EDI 837P claim export, ERA 835 remittance ingestion, eligibility 270/271, denials & appeals, document attachments, AR reconciliation, and RCM analytics) are fully implemented, production-built, and covered by 147 automated backend unit, security, and integration tests.

**Money is `NUMERIC(12,2)` and `BigDecimal`, start to finish.** No floating point anywhere in the billing path.

---

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Backend exits with `Schema-validation: missing column ...` | A JPA mapping disagrees with `V1__schema.sql`. The message names the table and column. This is `ddl-auto: validate` doing its job — fix the mapping, do not switch to `update`. |
| Backend cannot reach the database | `db` has a healthcheck and `backend` waits for it. If it still fails, `docker compose logs db` — usually a stale volume from an earlier schema. `docker compose down -v` and start again. |
| `Failed to resolve org.springdoc:...` during the image build | springdoc is the only dependency not version-managed by the Spring Boot parent. Delete that block from `backend/pom.xml` and `config/OpenApiConfig.java`; nothing else imports it. You lose Swagger UI and nothing else. |
| Port already in use | Set `FRONTEND_PORT`, `BACKEND_PORT` or `DB_PORT_HOST` in `.env`. |
| Frontend loads but every call 401s | The JWT expired (8 hours by default). Sign in again, or raise `JWT_TTL_MINUTES`. |
| `app.jwt.secret must be at least 32 bytes` | Your `JWT_SECRET` is too short for HS256. `openssl rand -base64 48`. |
