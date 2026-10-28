package com.smartfix.communityapp;

import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** The authoritative audit and its projection event share the moderation transaction. */
@Service
public class LocalAuditService {
    private final JdbcTemplate db;
    private final DomainOutbox events;
    public LocalAuditService(JdbcTemplate db, DomainOutbox events) { this.db=db; this.events=events; }
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(Long actor, String action, String type, Long target, String outcome, Instant at) {
        Long id=db.queryForObject("INSERT INTO community_audit(actor_user_id,action,target_type,target_id,outcome,occurred_at) VALUES (?,?,?,?,?,?) RETURNING id",Long.class,actor,action,type,target,outcome,java.sql.Timestamp.from(at));
        long question=target;
        if(type.equals("ANSWER")) question=db.queryForObject("SELECT question_id FROM community_answers WHERE id=?",Long.class,target);
        if(type.equals("REPORT")) question=db.queryForObject("SELECT COALESCE(r.question_id,a.question_id) FROM community_reports r LEFT JOIN community_answers a ON a.id=r.answer_id WHERE r.id=?",Long.class,target);
        events.append(question,"CommunityAuditRecorded",Map.of("auditId",id,"actorUserId",actor,"action",action,"targetType",type,"targetId",target,"outcome",outcome,"occurredAt",at.toString()));
    }
}
