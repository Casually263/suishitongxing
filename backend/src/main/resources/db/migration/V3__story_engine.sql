CREATE TABLE chapter (
  id BIGINT PRIMARY KEY,
  code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
  title_i18n JSON NOT NULL,
  intro_i18n JSON NOT NULL,
  status VARCHAR(16) NOT NULL,
  release_mode VARCHAR(16) NOT NULL DEFAULT 'PRACTICE'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE knowledge_card (
  id BIGINT PRIMARY KEY,
  chapter_id BIGINT NOT NULL,
  content_i18n JSON NOT NULL,
  source_title VARCHAR(200) NOT NULL,
  source_url VARCHAR(800) NOT NULL,
  review_status VARCHAR(32) NOT NULL,
  FOREIGN KEY (chapter_id) REFERENCES chapter(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A published story row is one immutable version. New publications get a new id.
CREATE TABLE story (
  id BIGINT PRIMARY KEY,
  chapter_id BIGINT NOT NULL,
  version INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  start_node BIGINT NOT NULL,
  initial_variables JSON NOT NULL,
  UNIQUE KEY uk_story_version(chapter_id,version),
  FOREIGN KEY (chapter_id) REFERENCES chapter(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE story_node (
  id BIGINT PRIMARY KEY,
  story_id BIGINT NOT NULL,
  node_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  node_type VARCHAR(16) NOT NULL,
  content_i18n JSON NOT NULL,
  config JSON NOT NULL,
  UNIQUE KEY uk_story_node_key(story_id,node_key),
  UNIQUE KEY uk_story_node_owner(story_id,id),
  CONSTRAINT ck_story_node_type CHECK(node_type IN ('narrative','choice','dual','gift','checkin','ending')),
  FOREIGN KEY (story_id) REFERENCES story(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE story_edge (
  id BIGINT PRIMARY KEY,
  story_id BIGINT NOT NULL,
  from_node BIGINT NOT NULL,
  to_node BIGINT NOT NULL,
  action_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  priority INT NOT NULL DEFAULT 0,
  condition_json JSON NOT NULL,
  UNIQUE KEY uk_story_edge_priority(story_id,from_node,action_key,priority),
  FOREIGN KEY (story_id,from_node) REFERENCES story_node(story_id,id),
  FOREIGN KEY (story_id,to_node) REFERENCES story_node(story_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Story-scoped consent sessions; these are not the future general matching/pair system.
CREATE TABLE story_session (
  id BIGINT PRIMARY KEY,
  story_id BIGINT NOT NULL,
  user_a BIGINT NOT NULL,
  user_b BIGINT NULL,
  mode VARCHAR(16) NOT NULL,
  invite_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
  invite_deadline DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  FOREIGN KEY (story_id) REFERENCES story(id),
  FOREIGN KEY (user_a) REFERENCES sys_user(id),
  FOREIGN KEY (user_b) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE story_progress (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  chapter_id BIGINT NOT NULL,
  story_id BIGINT NOT NULL,
  session_id BIGINT NOT NULL,
  perspective CHAR(1) NOT NULL,
  current_node BIGINT NOT NULL,
  variables JSON NOT NULL,
  history JSON NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PLAYING',
  version BIGINT NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_story_user_chapter(user_id,chapter_id),
  FOREIGN KEY (user_id) REFERENCES sys_user(id),
  FOREIGN KEY (chapter_id) REFERENCES chapter(id),
  FOREIGN KEY (story_id) REFERENCES story(id),
  FOREIGN KEY (session_id) REFERENCES story_session(id),
  FOREIGN KEY (story_id,current_node) REFERENCES story_node(story_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE story_dual_round (
  id BIGINT PRIMARY KEY,
  session_id BIGINT NOT NULL,
  node_id BIGINT NOT NULL,
  answer_a VARCHAR(64) NULL,
  answer_b VARCHAR(64) NULL,
  state VARCHAR(24) NOT NULL DEFAULT 'OPEN',
  deadline DATETIME(3) NOT NULL,
  extended BOOLEAN NOT NULL DEFAULT FALSE,
  success BOOLEAN NULL,
  harmony_delta INT NOT NULL DEFAULT 0,
  resolution_mode VARCHAR(16) NULL,
  UNIQUE KEY uk_dual_round(session_id,node_id),
  FOREIGN KEY (session_id) REFERENCES story_session(id),
  FOREIGN KEY (node_id) REFERENCES story_node(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE story_action (
  id BIGINT PRIMARY KEY,
  progress_id BIGINT NOT NULL,
  request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  from_node BIGINT NOT NULL,
  to_node BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_story_request(progress_id,request_id),
  FOREIGN KEY (progress_id) REFERENCES story_progress(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE story_submission (
  id BIGINT PRIMARY KEY,
  progress_id BIGINT NOT NULL,
  node_id BIGINT NOT NULL,
  kind VARCHAR(16) NOT NULL,
  content_i18n JSON NOT NULL,
  visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
  audit_status VARCHAR(24) NOT NULL DEFAULT 'NOT_PUBLISHED',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_story_submission(progress_id,node_id),
  FOREIGN KEY (progress_id) REFERENCES story_progress(id),
  FOREIGN KEY (node_id) REFERENCES story_node(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE story_reward (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  chapter_id BIGINT NOT NULL,
  progress_id BIGINT NOT NULL,
  stamp_code VARCHAR(32) NOT NULL,
  ending_key VARCHAR(64) NOT NULL,
  partner_mode VARCHAR(16) NOT NULL,
  awarded_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_story_reward(user_id,chapter_id),
  FOREIGN KEY (user_id) REFERENCES sys_user(id),
  FOREIGN KEY (chapter_id) REFERENCES chapter(id),
  FOREIGN KEY (progress_id) REFERENCES story_progress(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
