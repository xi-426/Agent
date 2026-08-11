ALTER TABLE work_order RENAME TO todo_item;

ALTER TABLE todo_item RENAME CONSTRAINT fk_work_order_user TO fk_todo_item_user;
ALTER TABLE todo_item RENAME CONSTRAINT ck_work_order_status TO ck_todo_item_status;
ALTER TABLE todo_item RENAME CONSTRAINT ck_work_order_priority TO ck_todo_item_priority;

ALTER INDEX idx_work_order_user_id RENAME TO idx_todo_item_user_id;
ALTER INDEX idx_work_order_status RENAME TO idx_todo_item_status;

ALTER TABLE todo_item DROP CONSTRAINT ck_todo_item_status;
ALTER TABLE todo_item ALTER COLUMN status DROP DEFAULT;

UPDATE todo_item
SET status = CASE status
    WHEN 'OPEN' THEN 'PENDING'
    WHEN 'PROCESSING' THEN 'IN_PROGRESS'
    WHEN 'RESOLVED' THEN 'COMPLETED'
    WHEN 'CLOSED' THEN 'CANCELLED'
    ELSE status
END;

ALTER TABLE todo_item ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE todo_item
    ADD CONSTRAINT ck_todo_item_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));
