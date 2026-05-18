package com.park.boatrental.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * SQLite CHECK constraints are not updated by Hibernate ddl-auto. Older databases
 * only allowed AVAILABLE, ASSIGNED, OUT; approve waitlist needs WAITLISTED.
 */
@Component
public class BoatStatusCheckMigration {

    public BoatStatusCheckMigration(JdbcTemplate jdbc) {
        if (!needsMigration(jdbc)) {
            return;
        }
        migrate(jdbc);
    }

    private static void migrate(JdbcTemplate jdbc) {
        jdbc.execute("PRAGMA foreign_keys = OFF");
        try {
            jdbc.execute("""
                    CREATE TABLE boats_migrated (
                        id integer NOT NULL,
                        boat_number varchar(255) NOT NULL UNIQUE,
                        boat_type varchar(255) NOT NULL,
                        status varchar(255) NOT NULL CHECK (status IN ('AVAILABLE','ASSIGNED','OUT','WAITLISTED')),
                        waitlist_entry_id bigint,
                        PRIMARY KEY (id)
                    )
                    """);
            jdbc.execute("""
                    INSERT INTO boats_migrated (id, boat_number, boat_type, status, waitlist_entry_id)
                    SELECT id, boat_number, boat_type, status, waitlist_entry_id FROM boats
                    """);
            jdbc.execute("DROP TABLE boats");
            jdbc.execute("ALTER TABLE boats_migrated RENAME TO boats");
        } finally {
            jdbc.execute("PRAGMA foreign_keys = ON");
        }
    }

    private static boolean needsMigration(JdbcTemplate jdbc) {
        String ddl = jdbc.queryForObject(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'boats'",
                String.class);
        return ddl != null && !ddl.contains("WAITLISTED");
    }
}
