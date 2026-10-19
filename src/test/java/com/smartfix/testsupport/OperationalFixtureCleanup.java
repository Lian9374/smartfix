package com.smartfix.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;

/** Old workflow fixtures also need to remove the operational rows added in V26/V27. */
public final class OperationalFixtureCleanup {
    private OperationalFixtureCleanup() {}
    public static void clear(JdbcTemplate jdbc) {
        for (String table : List.of("delivery_tasks", "sla_states", "sla_policies", "audit_entries")) {
            Integer count=jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE lower(table_name)=? AND table_schema=current_schema()",Integer.class,table);
            if (count != null && count > 0) jdbc.update("DELETE FROM " + table);
        }
    }
}
