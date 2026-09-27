CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE app_users (
  id uuid PRIMARY KEY,
  username varchar(80) NOT NULL UNIQUE,
  email varchar(255) UNIQUE,
  weekly_email_enabled boolean NOT NULL DEFAULT false,
  password_hash varchar(255) NOT NULL,
  role varchar(20) NOT NULL DEFAULT 'STUDENT' CHECK (role IN ('STUDENT','ADMIN')),
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE zones (
  id varchar(60) PRIMARY KEY,
  name varchar(120) NOT NULL,
  description varchar(500) NOT NULL DEFAULT '',
  active boolean NOT NULL DEFAULT true,
  sort_order integer NOT NULL DEFAULT 0
);

CREATE TABLE data_sources (
  id uuid PRIMARY KEY,
  zone_id varchar(60) NOT NULL REFERENCES zones(id),
  name varchar(200) NOT NULL,
  base_url text NOT NULL UNIQUE,
  active boolean NOT NULL DEFAULT true,
  last_crawled_at timestamptz,
  last_status varchar(30) NOT NULL DEFAULT 'NEVER',
  last_error text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE notices (
  id uuid PRIMARY KEY,
  source_id uuid REFERENCES data_sources(id) ON DELETE SET NULL,
  zone_id varchar(60) NOT NULL REFERENCES zones(id),
  title text NOT NULL,
  body text NOT NULL DEFAULT '',
  canonical_url text NOT NULL UNIQUE,
  published_at timestamptz,
  fetched_at timestamptz NOT NULL DEFAULT now(),
  content_hash varchar(64) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX notices_zone_date_idx ON notices(zone_id, published_at DESC NULLS LAST);
CREATE INDEX notices_title_trgm_idx ON notices USING gin(title gin_trgm_ops);
CREATE INDEX notices_body_trgm_idx ON notices USING gin(body gin_trgm_ops);

CREATE TABLE knowledge_documents (
  id uuid PRIMARY KEY,
  filename text NOT NULL,
  file_hash varchar(64) NOT NULL UNIQUE,
  imported_at timestamptz NOT NULL DEFAULT now(),
  page_count integer NOT NULL DEFAULT 0,
  chunk_count integer NOT NULL DEFAULT 0,
  status varchar(20) NOT NULL DEFAULT 'READY'
);

CREATE TABLE knowledge_chunks (
  id uuid PRIMARY KEY,
  document_id uuid NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
  page_no integer NOT NULL,
  section_title text NOT NULL DEFAULT '',
  content text NOT NULL,
  embedding vector(1536) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX knowledge_chunks_doc_idx ON knowledge_chunks(document_id, page_no);
CREATE INDEX knowledge_chunks_embedding_hnsw_idx ON knowledge_chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX knowledge_chunks_content_trgm_idx ON knowledge_chunks USING gin(content gin_trgm_ops);

CREATE TABLE chat_sessions (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  title varchar(200) NOT NULL DEFAULT '新会话',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX chat_sessions_user_idx ON chat_sessions(user_id, updated_at DESC);

CREATE TABLE chat_messages (
  id uuid PRIMARY KEY,
  session_id uuid NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
  role varchar(20) NOT NULL CHECK (role IN ('USER','ASSISTANT')),
  content text NOT NULL,
  citations_json jsonb NOT NULL DEFAULT '[]'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX chat_messages_session_idx ON chat_messages(session_id, created_at);

CREATE TABLE subscriptions (
  user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  zone_id varchar(60) NOT NULL REFERENCES zones(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id, zone_id)
);

CREATE TABLE weekly_reports (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  period_start date NOT NULL,
  period_end date NOT NULL,
  content text NOT NULL,
  status varchar(30) NOT NULL DEFAULT 'READY',
  email_status varchar(30) NOT NULL DEFAULT 'NOT_CONFIGURED',
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(user_id, period_start, period_end)
);
CREATE INDEX weekly_reports_user_idx ON weekly_reports(user_id, period_end DESC);

CREATE TABLE crawl_runs (
  id uuid PRIMARY KEY,
  started_at timestamptz NOT NULL DEFAULT now(),
  finished_at timestamptz,
  status varchar(30) NOT NULL DEFAULT 'RUNNING',
  source_count integer NOT NULL DEFAULT 0,
  notice_count integer NOT NULL DEFAULT 0,
  error_summary text
);

INSERT INTO zones(id,name,description,sort_order) VALUES
 ('mie','信息工程学院','信息工程学院官网通知与公告',1),
 ('university','学校官网','北京印刷学院官网通知与公告',2),
 ('news','校园新闻网','北京印刷学院新闻网内容',3),
 ('other-official','其他官方网页','经管理员确认的学校官方网页',4);

INSERT INTO data_sources(id,zone_id,name,base_url) VALUES
 ('a0000000-0000-4000-8000-000000000001','mie','信息工程学院官网','https://mie.bigc.edu.cn/'),
 ('a0000000-0000-4000-8000-000000000002','university','北京印刷学院官网','https://www.bigc.edu.cn/index.htm'),
 ('a0000000-0000-4000-8000-000000000003','news','北京印刷学院新闻网','https://news.bigc.edu.cn/');

