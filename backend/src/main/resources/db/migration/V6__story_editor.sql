-- Mutable authoring drafts are separate from immutable runtime story versions.
CREATE TABLE story_editor_draft (
  chapter_id BIGINT PRIMARY KEY,
  revision BIGINT NOT NULL,
  graph_json JSON NOT NULL,
  updated_by BIGINT NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  FOREIGN KEY (chapter_id) REFERENCES chapter(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE story_publication (
  story_id BIGINT PRIMARY KEY,
  chapter_id BIGINT NOT NULL,
  draft_revision BIGINT NOT NULL,
  graph_json JSON NOT NULL,
  published_by BIGINT NOT NULL,
  review_note VARCHAR(500) NOT NULL,
  published_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_story_publication_revision(chapter_id,draft_revision),
  FOREIGN KEY (story_id) REFERENCES story(id),
  FOREIGN KEY (chapter_id) REFERENCES chapter(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Audit actor ids intentionally survive account deletion, like sys_oper_log.
CREATE TABLE story_editor_preview (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  owner_id BIGINT NOT NULL,
  chapter_id BIGINT NOT NULL,
  graph_json JSON NOT NULL,
  state_json JSON NOT NULL,
  revision BIGINT NOT NULL DEFAULT 0,
  expires_at DATETIME(3) NOT NULL,
  KEY idx_story_preview_expiry(expires_at),
  FOREIGN KEY (chapter_id) REFERENCES chapter(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_menu(id,code,permission_code,name,route) VALUES
 (20,'story-read','story:read','剧情编辑器','/admin/stories'),
 (21,'story-write','story:write','保存剧情草稿',NULL),
 (22,'story-preview','story:preview','剧情隔离预览',NULL),
 (23,'story-publish','story:publish','审校发布剧情',NULL);
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) VALUES
 (3,20),(3,21),(3,22),(3,23),
 (4,1),(4,20),(4,21),(4,22),
 (5,1),(5,20),(5,22),(5,23);
