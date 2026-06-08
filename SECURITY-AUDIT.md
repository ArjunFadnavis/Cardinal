# Cardinal — Security Audit

**Date:** 2026-06-07  
**Scope:** Boat rental Spring Boot app (`com.park.boatrental`), static UI, SQLite database, local Excel export  
**Method:** Static review of source, configuration, API surface, and deployment assumptions (no penetration test)

---

## Executive summary

Cardinal is built as a **trusted local network** tool: one front-desk machine runs Spring Boot on port **8080**, bound to **all interfaces**, with **no login, API keys, or role separation**. Anyone who can reach `http://<host>:8080` can list customers, change boat state, manipulate the waitlist, and trigger Excel export.

That is acceptable only if the Wi‑Fi is staff-only, the host firewall blocks port 8080 from the public internet, and physical access is controlled. It is **not** safe on a guest network, a port-forwarded home router, or any environment where untrusted devices share the LAN.

The highest-priority fixes are **network access control**, **authentication for write APIs**, and **stopping commit of customer data** (`data/boatrental.db` is currently tracked in git).

---

## Threat model

| Actor | Capability today |
|--------|------------------|
| Guest on park Wi‑Fi | If 8080 is reachable: full API access |
| Malicious page in staff browser | Limited CSRF risk; direct LAN API abuse is easier |
| Insider (beach iPad) | Intended user; same power as front desk |
| Remote attacker | Only if port 8080 is exposed beyond the LAN |
| Repo clone / backup theft | Customer names and rental history in SQLite if DB is committed |

**Assets:** customer names, call numbers, waitlist requests, rental times, Excel export path and contents.

**Out of scope for this app today:** payment data, passwords, health data, SMS credentials (not implemented).

---

## API inventory (all unauthenticated)

| Method | Path | Impact if abused |
|--------|------|------------------|
| GET | `/api/boats` | Read fleet + customer names on active boats |
| GET | `/api/rentals/active` | Read all active rentals |
| POST | `/api/boats/{n}/assign` | Assign boats under arbitrary names |
| POST | `/api/boats/{n}/send` | Mark boats sent out |
| POST | `/api/boats/{n}/return` | Check in boats, trigger waitlist matcher |
| POST | `/api/boats/{n}/reassign` | Move customers between boats |
| GET | `/api/waitlist` | Read full waitlist + requirements |
| POST | `/api/waitlist` | Add fake waitlist entries |
| PUT | `/api/waitlist/{id}` | Edit entries, requeue logic |
| POST | `/api/waitlist/{id}/approve` | Hold boats for a customer |
| POST | `/api/waitlist/{id}/requeue` | Jump queue |
| DELETE | `/api/waitlist/{id}` | Remove entries |
| POST | `/api/export/excel` | Append rows to configured `.xlsx` on disk |

Static assets: `/`, `/app.js`, `/wips.js`, `/styles.css`, `/Logo.png`.

---

## Findings

### Critical

#### C1 — No authentication or authorization

- **Location:** All controllers under `web/`; no `spring-boot-starter-security` in `pom.xml`.
- **Risk:** Complete integrity and confidentiality loss for anyone on the network. A guest could assign boats, approve waitlist holds, delete entries, or export data.
- **Recommendation:** Add Spring Security with at least a shared staff PIN or HTTP basic auth for `POST`/`PUT`/`DELETE` (and optionally reads). Separate read-only beach role vs front-desk write role if needed.

#### C2 — Service bound to all interfaces (`0.0.0.0`)

- **Location:** `application.properties` — `server.address=0.0.0.0`
- **Risk:** Intentional for iPad on LAN, but increases blast radius if the machine has multiple NICs or the network is not isolated.
- **Recommendation:** Keep `0.0.0.0` only on a dedicated staff VLAN; block 8080 on the host firewall from non-staff subnets. Do not port-forward 8080 to the internet.

---

### High

#### H1 — No TLS (HTTP only)

- **Risk:** Customer names and waitlist data cross the Wi‑Fi in cleartext. Passive sniffing on an open or compromised network exposes PII.
- **Recommendation:** Terminate TLS on the front-desk machine (reverse proxy or Spring Boot SSL) with a local cert, or restrict to a WPA3 staff-only SSID and accept residual risk for a small park deployment.

#### H2 — Customer database tracked in version control

- **Location:** `data/boatrental.db` is committed (`git ls-files` includes it).
- **Risk:** Customer names and rental history leak via git remote, forks, backups, or shared clones.
- **Recommendation:** Add `.gitignore` entries for `data/`, `exports/`, `*.xlsx`, and remove the DB from git history if it was ever pushed. Treat the DB like PII storage.

#### H3 — Unauthenticated filesystem export

- **Location:** `ExportController` → `ExcelExportService`; path from `boatrental.export.path`.
- **Risk:** Any API client can trigger writes to an arbitrary path configured on the server (currently an absolute user Desktop path in committed `application.properties`).
- **Recommendation:** Require auth for export; constrain export path to a dedicated directory under the app; avoid user-specific absolute paths in committed config.

#### H4 — Test mode enabled in default configuration

- **Location:** `boatrental.startup.mode=test` in `application.properties`.
- **Risk:** On empty or test DB, all boats start **OUT** with synthetic “Test customer” rentals — operational confusion and polluted export data if deployed to a live desk by mistake.
- **Recommendation:** Default to `normal`; use profiles (`application-dev.properties`) for test mode.

---

### Medium

#### M1 — No audit trail

- **Risk:** Cannot answer who assigned a boat, approved waitlist, or exported Excel after a dispute or misuse.
- **Recommendation:** Append-only `audit_events` table (timestamp, action, entity id, optional staff id once auth exists).

#### M2 — No rate limiting or abuse controls

- **Risk:** A script on the LAN can spam assign/return/waitlist endpoints; SQLite lock contention and UI confusion.
- **Recommendation:** Simple per-IP rate limit on mutating endpoints, or rely on network isolation plus auth.

#### M3 — Unbounded input size

- **Location:** `customerName`, `requirementJson` (LOB), nested `AND`/`OR` trees from waitlist API.
- **Risk:** Very long names or deeply nested JSON can increase CPU/memory use (matcher recursion).
- **Recommendation:** Max length on names (e.g. 200 chars), max nesting depth (e.g. 10), max children per group.

#### M4 — Excel formula injection

- **Location:** `ExcelExportService.writeRentalRow` writes raw `customerName` into cells.
- **Risk:** Names starting with `=`, `+`, `-`, `@` can execute as formulas when staff opens the sheet in Excel.
- **Recommendation:** Prefix risky values with `'` or sanitize; use POI cell type string explicitly.

#### M5 — Error responses may leak internal paths

- **Location:** `ExportController` — `"Could not write Excel file: " + e.getMessage()`.
- **Risk:** Reveals filesystem paths to API callers.
- **Recommendation:** Generic message to client; log details server-side only.

#### M6 — Schema auto-migration in production

- **Location:** `spring.jpa.hibernate.ddl-auto=update`; `BoatStatusCheckMigration` runs raw DDL at startup.
- **Risk:** Unexpected schema changes on upgrade; migration bugs could affect data integrity (not remote exploit, but availability).
- **Recommendation:** Frozen migrations (Flyway/Liquibase) for production; test migrations on a copy of `boatrental.db`.

#### M7 — No CSRF tokens on state-changing requests

- **Location:** `app.js` / `wips.js` use `fetch` POST/PUT/DELETE without CSRF headers.
- **Risk:** Low while unauthenticated (attacker calls API directly). Becomes relevant if cookie-based session auth is added without CSRF protection.
- **Recommendation:** When adding session auth, enable Spring Security CSRF for browser clients or use token-based API auth for the SPA.

---

### Low

#### L1 — Cross-site scripting (XSS) in UI — mostly mitigated

- **Location:** `app.js` and `wips.js` use `innerHTML` with `escapeHtml()` for customer names, summaries, and boat numbers.
- **Residual risk:** Future UI changes that omit escaping; `entry.status` is interpolated unescaped but is server-controlled enum.
- **Recommendation:** Keep using `escapeHtml` for all server-origin text; consider `textContent` / DOM APIs instead of `innerHTML` for new code.

#### L2 — SQL injection — low risk

- **Location:** JPA repositories and parameterized `JdbcTemplate` in `BoatStatusCheckMigration`.
- **Risk:** Low; no string-concatenated user SQL found.
- **Recommendation:** Maintain parameterized queries if adding native SQL.

#### L3 — Jackson deserialization of waitlist requirements

- **Location:** `RequirementJson.read` / `RequirementNode` polymorphic types.
- **Risk:** No `enableDefaultTyping`; sealed hierarchy limits gadget chains. Malicious deeply nested JSON is the main concern (see M3).
- **Recommendation:** Optional `StreamReadConstraints` max depth on `ObjectMapper`.

#### L4 — Sensitive configuration in repo

- **Location:** `boatrental.export.path=/Users/arjunfadnavis/Desktop/test.xlsx` exposes a username and layout.
- **Recommendation:** Use relative `exports/rental-log.xlsx` and environment-specific overrides.

#### L5 — Missing `.gitignore`

- **Risk:** DB, exports, JARs, OS files (`.DS_Store`) may be committed unintentionally.
- **Recommendation:** Standard Java + `data/`, `exports/`, `*.db`, `target/` (if not already ignored).

---

### Informational

#### I1 — Deployment model

- Single-process Spring Boot, SQLite file locking — not designed for multi-host writes or horizontal scale.
- **Recommendation:** One writer instance only; backup DB on a schedule.

#### I2 — Data at rest

- SQLite file is not encrypted. Physical theft of the front-desk machine exposes all history.
- **Recommendation:** OS full-disk encryption; restrict login to the desk PC.

#### I3 — Dependency hygiene

- Apache POI 5.2.5, Spring Boot 3.2.5 — track CVEs; run `mvn dependency-check` or GitHub Dependabot periodically.

#### I4 — Privacy (future SMS / cloud export)

- Planned Twilio or cloud Excel would introduce API keys, phone numbers, and third-party data processors — require separate DPIA, retention policy, and secret storage.

---

## What is done well

| Area | Notes |
|------|--------|
| **Parameterized persistence** | JPA/Hibernate for application queries |
| **XSS awareness** | `escapeHtml` used for displayed customer data in main views |
| **Business-rule validation** | Waitlist requirements validated server-side in `WaitlistService.validateRequirement` |
| **State machine checks** | Assign/send/return/reassign enforce boat status transitions |
| **Export idempotency** | `exportedAt` prevents duplicate rental rows on re-export |
| **Static resource exposure** | Only `Logo.png` mapped from classpath root; not broad directory listing |

---

## Recommended remediation roadmap

| Priority | Action | Effort |
|----------|--------|--------|
| P0 | Staff-only network + firewall 8080; never expose to internet | Ops |
| P0 | `.gitignore` + remove `data/boatrental.db` from git; rotate if pushed | Small |
| P1 | Spring Security: PIN or basic auth on mutating `/api/**` | Medium |
| P1 | Set `boatrental.startup.mode=normal` in production profile | Trivial |
| P2 | TLS on LAN or documented acceptance of HTTP risk | Medium |
| P2 | Sanitize Excel export cells; generic export errors | Small |
| P2 | Input limits on names and requirement tree depth | Small |
| P3 | Audit log table | Medium |
| P3 | Rate limiting on POST endpoints | Small |
| P3 | Flyway migrations instead of `ddl-auto=update` | Medium |

---

## Suggested security acceptance criteria (before “production”)

- [ ] Mutating APIs require staff authentication.
- [ ] Port 8080 reachable only from staff devices/VLAN.
- [ ] `data/boatrental.db` and export files excluded from git.
- [ ] `startup.mode=normal` in production config.
- [ ] Export path under `./exports/` with restricted OS permissions.
- [ ] Documented backup and restore for SQLite (encrypted disk).
- [ ] Dependency scan run on release builds.

---

## Review cadence

Re-run this checklist when adding:

- SMS or email notifications (secrets, consent, logging)
- Cloud Excel / Graph API (OAuth, token storage)
- NLP waitlist parsing (prompt injection, PII to third parties)
- Public internet access or multi-site deployment

---

*This document reflects the codebase as of the audit date. It is not a certification or penetration-test report.*
