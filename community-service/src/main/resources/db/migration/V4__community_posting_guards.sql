CREATE TABLE community_posting_guards (user_id BIGINT PRIMARY KEY CHECK(user_id>0));
-- The row lock is acquired before content/quota checks; no dependency on Core users.
