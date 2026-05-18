# Boat Rental (local)

Simple local boat rental tracker for front desk and beach. One Spring Boot app on the front-desk computer; iPad opens the same URL on your Wi‑Fi.

## Fleet (seeded on first run)

| Type | IDs |
|------|-----|
| Canoe (2 person) | C1–C10 |
| Kayak (1 person) | S1–S19 |
| Pedal boat (4 person) | P1–P4 |
| Double kayak (2 person) | T1–T9 |
| Stand-up paddleboard (1 person) | U1–U7 |

## What it does

1. **Front desk** — Enter customer name, click **Assign** next to an available boat.
2. Boat status becomes **Assigned**; customer can leave and come back.
3. **Beach** — See assigned customers with **Assigned 2:30 PM** (etc.); click **Send out** when they launch.
4. **Beach** — Click **Returned** when they’re back; return time is saved in the database.
5. **End of day** — On the front desk tab, click **Append to Excel** to add completed rentals to `exports/rental-log.xlsx`.

Both screens refresh automatically every 3 seconds.

## Requirements

- Java 17+
- Maven (`mvn`)

## Run

```bash
cd /path/to/BR
mvn spring-boot:run
```

- Front desk: http://localhost:8080
- iPad (same network): http://&lt;front-desk-computer-ip&gt;:8080

The SQLite database is at `./data/boatrental.db`. Excel log at `./exports/rental-log.xlsx`.

## Reset boats / database

The seeder only runs on an empty database. To load the new fleet:

```bash
rm data/boatrental.db
mvn spring-boot:run
```

## Excel export

- One row per **completed** rental (returned, not yet exported).
- Columns: renter name, boat number, date, time assigned, time returned, rental # that day (per boat).
- Clicking again only appends **new** trips (no duplicates).
- Timezone defaults to `America/New_York` in `application.properties`.

## API

| Method | Path | Action |
|--------|------|--------|
| GET | `/api/boats` | List boats and active rental info |
| POST | `/api/boats/{number}/assign` | Body: `{"customerName":"..."}` |
| POST | `/api/boats/{number}/send` | Mark sent out |
| POST | `/api/boats/{number}/return` | Check in |
| POST | `/api/export/excel` | Append completed rentals to Excel |

## WIPS (waitlist)

Open the **WIPS** tab to add customers with flexible requests:

| Mode | Example |
|------|---------|
| One boat type | John wants a kayak |
| All of these (AND) | Canoe and kayak |
| Any one of these (OR) | Canoe or kayak |
| Party size | Anything for 5 people; exclude SUPs |
| Advanced (JSON) | (canoe OR kayak) AND SUP; 2 canoes OR 2 kayaks |

When boats are **returned**, the matcher runs in FIFO order. Earlier customers who need a bundle (e.g. single **and** tandem) hold priority over later customers who would use only part of that bundle.

- **NOTIFIED** — staff see proposed boat numbers; call the customer.
- **Approve** — boats move to **Waitlisted** for that customer.
- **Front desk** — use **Assign (waivers)** on waitlisted boats after paperwork.
- **Remove** — customer no-show or left the list.
