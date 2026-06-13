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
## Fleet modification
Boats can be added, or marked out of service.  These modifications are stored in the sqlite database, so it is essential that this database is not deleted prematurely, or else all modifications to the original fleet will be undone, and staff will need to re-mark boats as out of service, or re-add boats that are not in the original fleet as described above.  Permenantly adding boats will require code modification.  Anybody who is crazy enough to mess with the codebase can do so at their own risk
## Requirements

- Java 17+


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

## Waitlist boat assignment rules

Party composition uses separate headcount boxes (each person in exactly one): **Adults (18+)**, **Under 16**, **16–18**, **Under 90 lbs**, **Under 50 lbs** (under-50 also counts toward canoe under-90 rules). The matcher assigns boats using the rules below. Edit `PartyCompositionMatcher.java` to change behavior.

| Boat type | Max on one boat | Allowed configurations |
|-----------|-----------------|------------------------|
| **Canoe (2 person)** | 4 | **2 people:** two adults; two 16–18; or one adult with one other (under 16, 16–18, under 90 lbs, or under 50 lbs). **3 people:** at least one adult and at least one under 90 lbs (under-50 box counts). **4 people:** at least one adult, at least two under 50 lbs, plus anyone else. |
| **Pedal boat (4 person)** | 4 | **Youth** = anyone not in the adult box (all child/teen/weight categories). Allowed adult + youth counts: **3+0**, **2+0**, **3+1**, **2+1**, **1+1**, **2+2**, **1+2**, **1+3**. |
| **Kayak (1 person) / Stand-up paddleboard** | 1 | One adult **or** one 16–18 year old only (no younger children or weight-based categories). |
| **Double kayak (2 person)** | 2 | Two adults; **or** two 16–18 year olds; **or** one adult with one other person (any category: second adult, under 16, 16–18, under 90 lbs, or under 50 lbs). |
| **Other multi-seat types** | Per boat name | Adults and 16–18 may share; under 16 requires an adult on the boat; no mixing 16–18 with under 16; weight categories not used. |

### Pedal boat quick reference

| Adults | Youths allowed |
|--------|----------------|
| 3 | 0 or 1 |
| 2 | 0, 1, or 2 |
| 1 | 1, 2, or 3 |
### POST SEASON CLEANUP
In order to ensure that the sqlite database does not get to big, at the end of the season, delete that file  (Everything should be backed up to the excel).  In the next season(Unless the codebase is modified), staff will need to re-add everything past the original 10 canoes, 19 singles, 4 pedal boats, 9 tandems, and 7 stand ups.  This file can be deleted by removing the two colons before the line del data\boatrental.db, and double clicking the file.  After deleting the file, make sure to add the two colons back

### Writeup
A writeup with screenshots is here: https://docs.google.com/document/d/17CKr-lXI5lGvKcVre0oe6H9HeFLtDg5TAx5ipxTnRrY/edit?usp=sharing
