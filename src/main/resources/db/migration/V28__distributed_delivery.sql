ALTER TABLE delivery_tasks DROP CONSTRAINT delivery_tasks_status_check;
ALTER TABLE delivery_tasks ADD CONSTRAINT delivery_tasks_status_check
    CHECK (status IN ('PENDING','QUEUED','DELIVERED','FAILED','SKIPPED'));
CREATE INDEX idx_delivery_tasks_queued ON delivery_tasks(created_at) WHERE status='QUEUED';
