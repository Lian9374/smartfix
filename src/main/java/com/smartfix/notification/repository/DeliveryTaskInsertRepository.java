package com.smartfix.notification.repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
@Repository
public class DeliveryTaskInsertRepository {
    private final JdbcTemplate jdbc;
    public DeliveryTaskInsertRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void insert(Long recipient,String channel,String type,String title,String message,Long reference,String key,Instant now){
        boolean h2=Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)c->c.getMetaData().getDatabaseProductName().equals("H2")));
        String sql=h2?"""
            MERGE INTO delivery_tasks t
            USING (VALUES(?,?,?,?,?,?,?,'PENDING',0,?,?)) v(recipient_id,channel,event_type,dedup_key,title,message,reference_id,status,attempts,next_attempt_at,created_at)
            ON t.dedup_key=v.dedup_key
            WHEN NOT MATCHED THEN INSERT(recipient_id,channel,event_type,dedup_key,title,message,reference_id,status,attempts,next_attempt_at,created_at)
            VALUES(v.recipient_id,v.channel,v.event_type,v.dedup_key,v.title,v.message,v.reference_id,v.status,v.attempts,v.next_attempt_at,v.created_at)
            """:"""
            INSERT INTO delivery_tasks(recipient_id,channel,event_type,dedup_key,title,message,reference_id,status,attempts,next_attempt_at,created_at)
            VALUES(?,?,?,?,?,?,?,'PENDING',0,?,?) ON CONFLICT(dedup_key) DO NOTHING
            """;
        var timestamp=java.sql.Timestamp.from(now);
        jdbc.update(sql,recipient,channel,type,key,title,message,reference,timestamp,timestamp);
    }
}
