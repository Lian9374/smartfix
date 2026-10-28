ALTER TABLE audit_entries ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'CORE';
ALTER TABLE audit_entries ADD COLUMN source_id BIGINT;
CREATE UNIQUE INDEX uq_audit_source_id ON audit_entries(source,source_id) WHERE source_id IS NOT NULL;
