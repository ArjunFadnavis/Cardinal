# Cardinal — Security Audit

**Initial audit:** 2026-06-07  
**Updated:** 2026-06-07 (after `.gitignore` + network config change)  
**Scope:** Boat rental Spring Boot app (`com.park.boatrental`), static UI, SQLite database, local Excel export  
**Method:** Static review of source, configuration, API surface, and deployment assumptions (no penetration test)

---

## Executive summary

Cardinal is a **trusted local network** tool: one front-desk computer runs Spring Boot on port **8080**. Beach iPad and front desk open the same URL using the **front-desk machine’s LAN IP** (e.g. `http://192.168.1.42:8080`). There is still **no login, API keys, or role separation**.

Anyone on the network who can reach that IP and port can list customers, change boat state, manipulate the waitlist, and trigger Excel export. That is acceptable only on a **staff-only Wi‑Fi**, with **no port forwarding** to the internet, and controlled physical access.

**Since the last audit:**

- **Remediated:** `data/` and `*.db` added to `.gitignore`; `data/boatrental.db` removed from git tracking (file remains on disk).
- **Config change:** `server.address=0.0.0.0` was removed from `application.properties`. Spring Boot’s default is still to **listen on all local interfaces**, so iPad access via the computer’s IP is unchanged. This does **not** by itself reduce who can connect—only firewall, network isolation, or binding to a specific interface/IP would.

**Top remaining priorities:** authentication for write APIs, staff-only network / host firewall, and clearing the DB from git **history** if it was ever pushed to a remote.

---

## Deployment & network model

| Component | Behavior |
|-----------|----------|
| **Front-desk PC** | Runs `mvn spring-boot:run` (or JAR); holds SQLite at `./data/boatrental.db` |
| **Beach iPad** | Browser → `http://<front-desk-LAN-IP>:8080` (same Wi‑Fi as the PC) |
| **Bind address** | No `server.address` in config → embedded Tomcat listens on **all interfaces** on port 8080 (Spring Boot default) |
| **Not in config** | TLS, reverse proxy, firewall rules, Wi‑Fi segmentation |

**Operational note:** Removing explicit `0.0.0.0` does not mean “localhost only.” To restrict to one NIC you would set `server.address` to that interface’s IP; to block remote LAN clients you need OS firewall rules, not omission of `0.0.0.0`.

---

## Threat model

| Actor | Capability today |
|--------|------------------|
| Device on staff Wi‑Fi | Full API access if it can reach `<desk-IP>:8080` |
| Guest on public park Wi‑Fi | Full API access **if** guest Wi‑Fi can route to the desk IP (avoid shared L2/L3 with guests) |
| Malicious page in staff browser | Minor CSRF angle; direct `fetch` to desk IP is simpler for an attacker on the LAN |
| Insider (beach iPad) | Intended user; same API power as front desk |
| Remote attacker | Only if port 8080 is port-forwarded or the desk is reachable from the internet |
| Repo clone / old commits | Customer PII **may still exist** in git history if `boatrental.db` was pushed before `.gitignore` |

**Assets:** customer names, call numbers, waitlist requests, rental times, Excel export path and contents.

**Out of scope today:** payment data, passwords, health data, SMS credentials (not implemented).

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
- **Risk:** Complete integrity and confidentiality loss for anyone who can reach the desk on port 8080.
- **Recommendation:** Add Spring Security with at least a shared staff PIN or HTTP basic auth for `POST`/`PUT`/`DELETE` (and optionally reads). Consider a read-only beach role vs front-desk write role.

---

### High

#### H1 — No TLS (HTTP only)

- **Risk:** Customer names and waitlist data cross Wi‑Fi in cleartext between iPad and front-desk PC.
- **Recommendation:** TLS on the desk (reverse proxy or Spring Boot SSL), or documented acceptance of risk on a locked staff WPA3 SSID.

#### H2 — Customer database in version control (partially remediated)

- **Was:** `data/boatrental.db` committed to git.
- **Now:** `.gitignore` includes `data/` and `*.db`; file untracked via `git rm --cached` (local DB unchanged).
- **Residual risk:** Historical commits may still contain the DB if it was **pushed** to GitHub/GitLab.
- **Recommendation:** If the remote ever had the DB: rotate treats as breach, use `git filter-repo` or BFG to purge history, force-push only after team agreement. Do not re-add `data/` to git.

#### H3 — Unauthenticated filesystem export

- **Location:** `ExportController` → `ExcelExportService`; path from `boatrental.export.path`.
- **Risk:** Any LAN client can trigger writes to the configured `.xlsx` path (currently an absolute Desktop path in committed config).
- **Recommendation:** Require auth for export; use `./exports/rental-log.xlsx`; restrict OS permissions on export directory.

#### H4 — Test mode enabled in default configuration

- **Location:** `boatrental.startup.mode=test` in `application.properties`.
- **Risk:** Synthetic “Test customer” rentals on startup if DB/fleet state allows — wrong data for a live desk.
- **Recommendation:** Default `normal`; use `application-dev.properties` for test mode.

#### H5 — LAN-wide listen on port 8080 (default bind)

- **Location:** No `server.address` in `application.properties` (explicit `0.0.0.0` removed).
- **Risk:** Service accepts connections on **every** host interface (Wi‑Fi, Ethernet, VPN, etc.), not only the IP staff type into the iPad. Exposure depends on who can route to any of those addresses.
- **iPad model:** Connecting to the computer’s LAN IP is correct; it does not limit listeners to that IP alone.
- **Recommendation:** OS firewall: allow inbound 8080 only from staff subnet or iPad IPs; keep desk off guest Wi‑Fi; never port-forward 8080. Optional: `server.address=<desk-static-LAN-IP>` if you only want one interface (test that iPad still reaches it).

---

### Medium

#### M1 — No audit trail

- **Risk:** No record of who assigned, approved waitlist, or exported.
- **Recommendation:** Append-only `audit_events` table (timestamp, action, entity id; staff id once auth exists).

#### M2 — No rate limiting or abuse controls

- **Risk:** LAN script can spam mutating endpoints; SQLite contention and UI chaos.
- **Recommendation:** Per-IP rate limits on POST/PUT/DELETE, or auth + network isolation.

#### M3 — Unbounded input size

- **Location:** `customerName`, `requirementJson` (LOB), nested waitlist `AND`/`OR` trees.
- **Risk:** CPU/memory pressure via huge names or deep JSON (matcher recursion).
- **Recommendation:** Max name length (~200), max nesting depth (~10), max children per group.

#### M4 — Excel formula injection

- **Location:** `ExcelExportService.writeRentalRow` writes raw `customerName`.
- **Risk:** Names like `=CMD(...)` may run as formulas when opened in Excel.
- **Recommendation:** Prefix/sanitize formula triggers; explicit string cell type in POI.

#### M5 — Error responses may leak internal paths

- **Location:** `ExportController` — `"Could not write Excel file: " + e.getMessage()`.
- **Recommendation:** Generic client message; log details server-side only.

#### M6 — Schema auto-migration in production

- **Location:** `spring.jpa.hibernate.ddl-auto=update`; `BoatStatusCheckMigration` at startup.
- **Recommendation:** Flyway/Liquibase for production; test migrations on DB copy.

#### M7 — No CSRF tokens on state-changing requests

- **Location:** `app.js` / `wips.js` — `fetch` without CSRF.
- **Risk:** Low today (unauthenticated LAN API). Required when adding cookie sessions.
- **Recommendation:** Spring Security CSRF or token-based SPA auth if sessions are added.

---

### Low

#### L1 — Cross-site scripting (XSS) — mostly mitigated

- **Location:** `escapeHtml()` on customer names, summaries, boat numbers in `app.js` / `wips.js`.
- **Residual:** `entry.status` unescaped (server enum); future `innerHTML` without escaping.
- **Recommendation:** Keep escaping; prefer `textContent` for new UI.

#### L2 — SQL injection — low risk

- JPA and parameterized `JdbcTemplate` in migrations.

#### L3 — Jackson deserialization of waitlist requirements

- Sealed `RequirementNode` hierarchy; main risk is deep nesting (M3).

#### L4 — Sensitive configuration in repo

- `boatrental.export.path` points at a user Desktop path in committed properties.
- **Recommendation:** Relative path under `./exports/`.

#### L5 — `.gitignore` (remediated)

- **Now:** `.gitignore` covers only `data/` and `*.db` (JARs and other build artifacts remain committable).
- **Recommendation:** Commit `.gitignore`; do not add `data/boatrental.db` back to the repo.

---

### Informational

#### I1 — Single-writer SQLite deployment

- Not safe for multiple hosts writing the same DB.

#### I2 — Data at rest unencrypted

- Physical theft of desk PC exposes `boatrental.db`.
- **Recommendation:** Full-disk encryption; backup to encrypted storage.

#### I3 — Dependency hygiene

- Spring Boot 3.2.5, POI 5.2.5 — periodic CVE checks.

#### I4 — Future SMS / cloud export

- Would require secrets management, consent, and third-party risk review.

---

## Resolved / changed since initial audit

| Item | Status |
|------|--------|
| C2 explicit `server.address=0.0.0.0` | **Removed from config** — reframed as H5 (default bind + firewall) |
| H2 DB in git | **Partially fixed** — `.gitignore` + untracked; history purge may still be needed |
| L5 missing `.gitignore` | **Fixed** |

---

## What is done well

| Area | Notes |
|------|--------|
| **Parameterized persistence** | JPA/Hibernate for application queries |
| **XSS awareness** | `escapeHtml` on displayed customer data |
| **Business-rule validation** | Waitlist requirements validated server-side |
| **State machine checks** | Assign/send/return/reassign enforce transitions |
| **Export idempotency** | `exportedAt` prevents duplicate export rows |
| **Static resources** | Only `Logo.png` mapped from classpath root |
| **PII out of future commits** | `data/` gitignored |

---

## Recommended remediation roadmap

| Priority | Action | Effort | Status |
|----------|--------|--------|--------|
| P0 | Staff-only Wi‑Fi; firewall 8080 to desk + iPad subnet | Ops | Open |
| P0 | Purge `boatrental.db` from git remote history if ever pushed | Small–medium | Open |
| P0 | `.gitignore` + untrack DB | Small | **Done** |
| P1 | Spring Security on mutating `/api/**` | Medium | Open |
| P1 | `startup.mode=normal` in production | Trivial | Open |
| P1 | Document iPad URL = desk LAN IP; verify firewall | Ops | Open |
| P2 | TLS or documented HTTP-on-LAN risk | Medium | Open |
| P2 | Excel formula sanitization; generic export errors | Small | Open |
| P2 | Input limits on waitlist JSON / names | Small | Open |
| P3 | Audit log, rate limits, Flyway migrations | Medium | Open |

---

## Suggested security acceptance criteria (before “production”)

- [ ] Mutating APIs require staff authentication.
- [ ] Port 8080 reachable only from staff devices (firewall + network design).
- [x] `data/boatrental.db` excluded from new git commits (`.gitignore`).
- [ ] DB purged from remote git history if it was ever pushed.
- [ ] `startup.mode=normal` in production config.
- [ ] Export path under `./exports/` with restricted OS permissions.
- [ ] iPad uses desk LAN IP; desk not reachable from guest network.
- [ ] Documented backup for SQLite (encrypted disk).
- [ ] Dependency scan on release builds.

---

## Review cadence

Re-run when adding SMS, cloud Excel, NLP waitlist parsing, or any internet-facing deployment.

---

*This document reflects the codebase and configuration as of the update date. It is not a certification or penetration-test report.*
