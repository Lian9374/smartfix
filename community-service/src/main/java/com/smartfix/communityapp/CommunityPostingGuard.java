package com.smartfix.communityapp;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.function.Supplier;

/** Community-owned author lock, quota checks and save callback share one local transaction. */
@Service
public class CommunityPostingGuard {
    private final JdbcTemplate db;
    private final CommunityProperties properties;
    public CommunityPostingGuard(JdbcTemplate db) { this(db, new CommunityProperties()); }
    @Autowired
    public CommunityPostingGuard(JdbcTemplate db, CommunityProperties properties) {
        this.db = db;
        this.properties = properties;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public <T> T question(long actor, String title, String body, Supplier<T> save) {
        lock(actor);
        var posting = properties.getPosting();
        Instant now = Instant.now();
        if (posting.isDuplicateDetectionEnabled()) {
            if (!posting.getDuplicateWindow().isZero()) {
                long repeats = db.queryForObject("SELECT count(*) FROM community_questions WHERE author_id=? AND lower(title)=lower(?) AND lower(body)=lower(?) AND created_at>=?", Long.class, actor, title.trim(), body.trim(), Timestamp.from(now.minus(posting.getDuplicateWindow())));
                if (repeats > 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate question");
            }
            quota(actor, "community_questions", now);
        }
        return save.get();
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public <T> T answer(long actor, Supplier<T> save) {
        lock(actor);
        if (properties.getPosting().isDuplicateDetectionEnabled()) quota(actor, "community_answers", Instant.now());
        return save.get();
    }
    private void lock(long actor) {
        if (actor <= 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        db.update("INSERT INTO community_posting_guards(user_id) VALUES (?) ON CONFLICT DO NOTHING", actor);
        db.queryForObject("SELECT user_id FROM community_posting_guards WHERE user_id=? FOR UPDATE", Long.class, actor);
    }
    private void quota(long actor, String table, Instant now) {
        var posting = properties.getPosting();
        long count = db.queryForObject("SELECT count(*) FROM " + table + " WHERE author_id=? AND created_at>=?", Long.class, actor, Timestamp.from(now.minus(posting.getRateLimitWindow())));
        if (count >= posting.getMaxPerRateLimitWindow()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Posting limit reached");
    }
}
