CREATE TABLE IF NOT EXISTS dict_process_arrangement_node (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT,
    node_name VARCHAR(120) NOT NULL,
    sort_no INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_dict_process_arrangement_node_parent
        FOREIGN KEY (parent_id) REFERENCES dict_process_arrangement_node(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dict_process_arrangement_node_root_name
    ON dict_process_arrangement_node (node_name)
    WHERE parent_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_dict_process_arrangement_node_parent_name
    ON dict_process_arrangement_node (parent_id, node_name)
    WHERE parent_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_dict_process_arrangement_node_parent_sort
    ON dict_process_arrangement_node (parent_id, sort_no, id);

CREATE TABLE IF NOT EXISTS dict_process_arrangement_item (
    id BIGSERIAL PRIMARY KEY,
    parent_node_id BIGINT,
    source_type VARCHAR(20) NOT NULL,
    process_detail_id BIGINT,
    fee_item_id INTEGER,
    sort_no INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_dict_process_arrangement_item_parent
        FOREIGN KEY (parent_node_id) REFERENCES dict_process_arrangement_node(id) ON DELETE CASCADE,
    CONSTRAINT fk_dict_process_arrangement_item_detail
        FOREIGN KEY (process_detail_id) REFERENCES dict_process_detail(id) ON DELETE CASCADE,
    CONSTRAINT fk_dict_process_arrangement_item_fee
        FOREIGN KEY (fee_item_id) REFERENCES dict_extra_fee_item(id) ON DELETE CASCADE,
    CONSTRAINT chk_dict_process_arrangement_item_source
        CHECK (source_type IN ('process', 'extra')),
    CONSTRAINT chk_dict_process_arrangement_item_identity
        CHECK (
            (source_type = 'process' AND process_detail_id IS NOT NULL AND fee_item_id IS NULL)
            OR (source_type = 'extra' AND fee_item_id IS NOT NULL AND process_detail_id IS NULL)
        )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dict_process_arrangement_item_process
    ON dict_process_arrangement_item (process_detail_id)
    WHERE source_type = 'process';

CREATE UNIQUE INDEX IF NOT EXISTS uk_dict_process_arrangement_item_extra
    ON dict_process_arrangement_item (fee_item_id)
    WHERE source_type = 'extra';

CREATE INDEX IF NOT EXISTS idx_dict_process_arrangement_item_parent_sort
    ON dict_process_arrangement_item (parent_node_id, sort_no, id);

COMMENT ON TABLE dict_process_arrangement_node IS '工艺编排自定义分组节点';
COMMENT ON TABLE dict_process_arrangement_item IS '工艺编排叶子挂载关系，只保存编排位置，不替代工艺基础档案';
