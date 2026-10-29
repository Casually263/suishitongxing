CREATE TABLE ai_config (
  id BIGINT PRIMARY KEY,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  base_url VARCHAR(500) NULL,
  model VARCHAR(128) NULL,
  temperature DECIMAL(3,2) NOT NULL DEFAULT 0.40,
  timeout_ms INT NOT NULL DEFAULT 8000,
  retries INT NOT NULL DEFAULT 1,
  daily_limit INT NOT NULL DEFAULT 20,
  max_rounds INT NOT NULL DEFAULT 30,
  minute_limit INT NOT NULL DEFAULT 10,
  max_tokens INT NOT NULL DEFAULT 256,
  include_usage BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO ai_config(id) VALUES(1);

CREATE TABLE ai_persona (
  id BIGINT PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE,
  name_i18n JSON NOT NULL,
  style_i18n JSON NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO ai_persona VALUES(1,'XIAOMAN',JSON_OBJECT('zh','小满','en','Xiaoman'),JSON_OBJECT('zh','你是虚构的节气向导小满，温和、好奇，使用清晰简短的中文，不冒充真人或历史见证人。','en','You are Xiaoman, a fictional seasonal guide: warm, curious and concise. Speak clear English. Never claim to be human or a historical witness.'));

-- Approval is bound to the exact content AND source URL/title, not just a mutable status flag.
CREATE TABLE ai_knowledge_review (
  card_id BIGINT PRIMARY KEY,
  content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  decision VARCHAR(16) NOT NULL,
  reviewed_by BIGINT NOT NULL,
  note VARCHAR(500) NOT NULL,
  reviewed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  FOREIGN KEY(card_id) REFERENCES knowledge_card(id),
  FOREIGN KEY(reviewed_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_usage_daily (
  user_id BIGINT NOT NULL,
  usage_date DATE NOT NULL,
  calls INT NOT NULL DEFAULT 0,
  PRIMARY KEY(user_id,usage_date),
  FOREIGN KEY(user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_usage_progress (
  progress_id BIGINT PRIMARY KEY,
  rounds INT NOT NULL DEFAULT 0,
  FOREIGN KEY(progress_id) REFERENCES story_progress(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_chat_log (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  progress_id BIGINT NOT NULL,
  node_id BIGINT NOT NULL,
  request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  context_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  language VARCHAR(8) NOT NULL,
  user_text_masked TEXT NOT NULL,
  reply_masked TEXT NOT NULL,
  source_ids JSON NOT NULL,
  mode VARCHAR(16) NOT NULL DEFAULT 'SCRIPT',
  status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
  fallback_reason VARCHAR(40) NULL,
  model VARCHAR(128) NULL,
  attempts INT NOT NULL DEFAULT 0,
  latency_ms INT NOT NULL DEFAULT 0,
  prompt_tokens INT NULL,
  completion_tokens INT NULL,
  usage_complete BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  finished_at DATETIME(3) NULL,
  UNIQUE KEY uk_ai_request(user_id,request_id),
  KEY ix_ai_user_progress(user_id,progress_id,created_at),
  KEY ix_ai_created(created_at),
  FOREIGN KEY(user_id) REFERENCES sys_user(id),
  FOREIGN KEY(progress_id) REFERENCES story_progress(id),
  FOREIGN KEY(node_id) REFERENCES story_node(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_menu(id,code,permission_code,name,route) VALUES
 (10,'ai-read','ai:read','AI调用与配置','/admin/ai'),
 (11,'ai-write','ai:write','修改AI配置',NULL),
 (12,'knowledge-review','knowledge:review','AI知识审校',NULL);
INSERT INTO sys_role_menu(role_id,menu_id) VALUES(3,10),(3,11),(3,12);
