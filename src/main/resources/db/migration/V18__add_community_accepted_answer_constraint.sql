-- Sprint 3 community board, part 2 of 2: close the cycle opened by V17.
--
-- V17 creates community_questions.accepted_answer_id as a bare column, because at
-- CREATE TABLE time community_answers does not exist yet. Both tables exist by the time
-- this migration runs, so the constraint can now be declared.
--
-- The pair (accepted_answer_id, id) must match a row of community_answers on
-- (id, question_id). In words: the answer a question accepted must be an answer to that
-- same question. A plain accepted_answer_id -> community_answers(id) foreign key would
-- not say that, and a question could be marked solved by a stranger's answer.
--
-- MATCH SIMPLE, the default, leaves the check unevaluated whenever accepted_answer_id is
-- NULL, which is the normal state of an open question.
--
-- ON DELETE is left at its default (NO ACTION) and no CASCADE is declared: removing an
-- accepted answer is a status change that nulls accepted_answer_id inside the same
-- transaction, never a row deletion. See the note at the top of V17.
--
-- Note on the composite foreign key's cost: refitting is checked on every insert and
-- update of community_questions. The index below covers the referencing side, which
-- PostgreSQL does not create automatically.

ALTER TABLE community_questions
    ADD CONSTRAINT fk_community_questions_accepted_answer
    FOREIGN KEY (accepted_answer_id, id) REFERENCES community_answers (id, question_id);

CREATE INDEX idx_community_questions_accepted_answer
    ON community_questions(accepted_answer_id)
    WHERE accepted_answer_id IS NOT NULL;
