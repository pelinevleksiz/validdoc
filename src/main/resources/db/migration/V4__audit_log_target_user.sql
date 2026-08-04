ALTER TABLE audit_logs
    ADD COLUMN target_user_id BIGINT;

ALTER TABLE audit_logs
    ADD CONSTRAINT audit_logs_target_user_id_fk FOREIGN KEY (target_user_id) REFERENCES users (id);