CREATE TABLE recycle_batches (
 id VARCHAR(36) PRIMARY KEY, root_type VARCHAR(30) NOT NULL, root_id BIGINT NOT NULL,
 label VARCHAR(255) NOT NULL, deleted_by VARCHAR(100) NOT NULL,
 deleted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE recycle_items (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, batch_id VARCHAR(36) NOT NULL,
 entity_type VARCHAR(30) NOT NULL, record_id BIGINT NOT NULL, label VARCHAR(255) NOT NULL,
 payload LONGTEXT NOT NULL, restored_at TIMESTAMP NULL, restored_by VARCHAR(100),
 CONSTRAINT fk_recycle_batch FOREIGN KEY(batch_id) REFERENCES recycle_batches(id)
);
CREATE INDEX ix_recycle_type_state ON recycle_items(entity_type,restored_at);
