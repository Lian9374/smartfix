package com.smartfix.notification;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class UserEDeliveryMigrationPostgresIT{
    @Test void upgradesV25WithoutChangingAccountsNotificationsOrAudit()throws Exception{
        String url=required("TEST_DB_URL"),user=required("TEST_DB_USERNAME"),password=required("TEST_DB_PASSWORD"),schema="usere_upgrade_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url,user,password);var sql=c.createStatement()){
            assertTrue(c.getCatalog().endsWith("_test"));
            try{
                var baseline=Flyway.configure().callbacks(new com.smartfix.common.configuration.LegacyMigrationCompatibility()).dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("25").load();baseline.migrate();
                sql.execute("SET search_path TO "+schema);
                sql.execute("INSERT INTO users(username,display_name,password_hash,role) VALUES('upgrade.fixture','Upgrade fixture','synthetic-test-hash','REQUESTER')");
                sql.execute("INSERT INTO notifications(recipient_id,event_type,dedup_key,title,message,created_at) SELECT id,'TEST','upgrade-key','Generic','Generic',now() FROM users WHERE username='upgrade.fixture'");
                sql.execute("INSERT INTO audit_entries(actor_user_id,action,target_type,target_id,outcome,occurred_at) SELECT id,'TEST','USER',id,'KEPT',now() FROM users WHERE username='upgrade.fixture'");
                String before;try(var row=sql.executeQuery("SELECT md5(to_jsonb(u)::text) FROM users u WHERE username='upgrade.fixture'")){assertTrue(row.next());before=row.getString(1);}
                var latest=Flyway.configure().callbacks(new com.smartfix.common.configuration.LegacyMigrationCompatibility()).dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load();assertTrue(latest.migrate().migrationsExecuted >= 2);latest.validate();assertEquals(0,latest.migrate().migrationsExecuted);
                try(var row=sql.executeQuery("SELECT md5((to_jsonb(u)-'notification_email')::text),notification_email FROM users u WHERE username='upgrade.fixture'")){assertTrue(row.next());assertEquals(before,row.getString(1));assertNull(row.getString(2));}
                // Exact old-field values and notification/audit payloads remain unchanged.
                try(var row=sql.executeQuery("SELECT username,display_name,password_hash,role FROM users WHERE username='upgrade.fixture'")){assertTrue(row.next());assertEquals("Upgrade fixture",row.getString(2));assertEquals("synthetic-test-hash",row.getString(3));assertEquals("REQUESTER",row.getString(4));}
                try(var row=sql.executeQuery("SELECT count(*) FROM notifications WHERE dedup_key='upgrade-key' AND message='Generic'")){assertTrue(row.next());assertEquals(1,row.getLong(1));}
                try(var row=sql.executeQuery("SELECT count(*) FROM audit_entries WHERE actor_user_id IS NOT NULL AND outcome='KEPT'")){assertTrue(row.next());assertEquals(1,row.getLong(1));}
                try(var row=sql.executeQuery("SELECT (SELECT count(*) FROM delivery_tasks),(SELECT count(*) FROM sla_policies),(SELECT count(*) FROM sla_states)")){assertTrue(row.next());assertEquals(0,row.getLong(1));assertEquals(0,row.getLong(2));assertEquals(0,row.getLong(3));}
            }finally{sql.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
        }
    }
    private String required(String key){var value=System.getenv(key);assertNotNull(value,key+" required");return value;}
}
