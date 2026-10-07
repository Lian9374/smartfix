ALTER TABLE maintenance_requests DROP CONSTRAINT chk_maintenance_requests_status;
ALTER TABLE maintenance_requests ADD CONSTRAINT chk_maintenance_requests_status CHECK (status IN
    ('SUBMITTED','UNDER_REVIEW','ASSIGNED','IN_PROGRESS','RESOLVED','CONFIRMED','REOPENED','CLOSED','REJECTED','CANCELLED'));
ALTER TABLE maintenance_requests
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN reviewed_at TIMESTAMPTZ,
    ADD COLUMN reviewed_by_user_id BIGINT,
    ADD COLUMN final_urgency_level VARCHAR(20),
    ADD COLUMN resolved_at TIMESTAMPTZ,
    ADD COLUMN confirmed_at TIMESTAMPTZ,
    ADD COLUMN closed_at TIMESTAMPTZ;
ALTER TABLE maintenance_requests ADD CONSTRAINT fk_maintenance_requests_reviewer
    FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id);
ALTER TABLE maintenance_requests ADD CONSTRAINT chk_maintenance_requests_final_urgency
    CHECK (final_urgency_level IS NULL OR final_urgency_level IN ('LOW','MEDIUM','HIGH'));
CREATE INDEX idx_maintenance_requests_owner_created ON maintenance_requests(requester_id, created_at DESC, id DESC);
CREATE INDEX idx_maintenance_requests_status_created ON maintenance_requests(status, created_at DESC, id DESC);
