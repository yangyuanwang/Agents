-- MySQL DDL DRAFT — DESIGN ONLY. NEVER EXECUTED.
-- Conditional target: MySQL 8.0.16+ InnoDB, utf8mb4 / utf8mb4_0900_ai_ci.
-- This draft is intentionally blocked pending physical choices listed in ddl-validation-report.md.
-- UUID values are BINARY(16), encoded as RFC 4122 bytes in network order by application code.
SET NAMES utf8mb4;

CREATE TABLE `user` (
  id BINARY(16) NOT NULL, username VARCHAR(100) NULL, email VARCHAR(254) NULL,
  password_hash VARCHAR(255) NOT NULL, display_name VARCHAR(150) NOT NULL,
  status VARCHAR(24) NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  deleted_at DATETIME(3) NULL, PRIMARY KEY(id), UNIQUE KEY uq_user_username(username), UNIQUE KEY uq_user_email(email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tenant (
 id BINARY(16) NOT NULL, name VARCHAR(160) NOT NULL, status VARCHAR(24) NOT NULL,
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, deleted_at DATETIME(3) NULL,
 PRIMARY KEY(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tenant_member (
 id BINARY(16) NOT NULL, tenant_id BINARY(16) NOT NULL, user_id BINARY(16) NOT NULL,
 role VARCHAR(16) NOT NULL, status VARCHAR(24) NOT NULL, joined_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY uq_tm_user_tenant(user_id,tenant_id), UNIQUE KEY uq_tm_tenant_user(tenant_id,user_id),
 KEY ix_tm_user_status(user_id,status), KEY ix_tm_tenant_status_role(tenant_id,status,role),
 CONSTRAINT fk_tm_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_tm_user FOREIGN KEY(user_id) REFERENCES `user`(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE embedding_profile (
 id BINARY(16) NOT NULL, scope VARCHAR(16) NOT NULL, owner_tenant_id BINARY(16) NULL,
 provider VARCHAR(64) NOT NULL, model_name VARCHAR(160) NOT NULL, model_revision VARCHAR(160) NULL,
 dimensions INT UNSIGNED NOT NULL, distance_metric VARCHAR(24) NOT NULL,
 collection_name VARCHAR(255) NOT NULL, secret_ref VARCHAR(512) NULL, config_snapshot JSON NULL,
 status VARCHAR(24) NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY uq_profile_collection(collection_name), UNIQUE KEY uq_profile_scope_owner_id(scope,owner_tenant_id,id),
 KEY ix_profile_owner(owner_tenant_id),
 CONSTRAINT fk_profile_owner FOREIGN KEY(owner_tenant_id) REFERENCES tenant(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT ck_profile_scope_owner CHECK ((scope='SYSTEM' AND owner_tenant_id IS NULL) OR (scope='TENANT' AND owner_tenant_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_base (
 id BINARY(16) NOT NULL, tenant_id BINARY(16) NOT NULL, name VARCHAR(160) NOT NULL,
 description VARCHAR(1000) NULL, active_embedding_profile_id BINARY(16) NULL, status VARCHAR(24) NOT NULL,
 created_by BINARY(16) NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, deleted_at DATETIME(3) NULL,
 PRIMARY KEY(id), UNIQUE KEY uq_kb_tenant_id(tenant_id,id), UNIQUE KEY uq_kb_tenant_name(tenant_id,name),
 KEY ix_kb_tenant_status_updated(tenant_id,status,updated_at),
 CONSTRAINT fk_kb_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_kb_creator FOREIGN KEY(created_by) REFERENCES `user`(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_kb_profile FOREIGN KEY(active_embedding_profile_id) REFERENCES embedding_profile(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_base_member (
 id BINARY(16) NOT NULL, tenant_id BINARY(16) NOT NULL, knowledge_base_id BINARY(16) NOT NULL,
 user_id BINARY(16) NOT NULL, role VARCHAR(32) NOT NULL, status VARCHAR(24) NOT NULL,
 created_by BINARY(16) NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY uq_kbm_kb_user(knowledge_base_id,user_id),
 KEY ix_kbm_tenant_user(tenant_id,user_id), KEY ix_kbm_user_status_kb(user_id,status,knowledge_base_id),
 CONSTRAINT fk_kbm_kb_tenant FOREIGN KEY(tenant_id,knowledge_base_id) REFERENCES knowledge_base(tenant_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_kbm_tm FOREIGN KEY(tenant_id,user_id) REFERENCES tenant_member(tenant_id,user_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_kbm_creator FOREIGN KEY(created_by) REFERENCES `user`(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE file_object (
 id BINARY(16) NOT NULL, tenant_id BINARY(16) NOT NULL, storage_key VARCHAR(512) NOT NULL,
 sha256 BINARY(32) NOT NULL, byte_size BIGINT UNSIGNED NOT NULL, media_type VARCHAR(127) NOT NULL,
 storage_status VARCHAR(24) NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 delete_requested_at DATETIME(3) NULL, deleted_at DATETIME(3) NULL,
 PRIMARY KEY(id), UNIQUE KEY uq_file_tenant_id(tenant_id,id), UNIQUE KEY uq_file_tenant_sha(tenant_id,sha256), UNIQUE KEY uq_file_storage_key(storage_key),
 KEY ix_file_cleanup(storage_status,updated_at),
 CONSTRAINT fk_file_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE document (
 id BINARY(16) NOT NULL, tenant_id BINARY(16) NOT NULL, knowledge_base_id BINARY(16) NOT NULL,
 display_name VARCHAR(255) NOT NULL, status VARCHAR(24) NOT NULL, active_version_id BINARY(16) NULL,
 created_by BINARY(16) NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, deleted_at DATETIME(3) NULL,
 PRIMARY KEY(id), UNIQUE KEY uq_doc_tenant_id(tenant_id,id), UNIQUE KEY uq_doc_kb_id(knowledge_base_id,id),
 KEY ix_doc_kb_status_created(knowledge_base_id,status,created_at), KEY ix_doc_tenant_status_deleted(tenant_id,status,deleted_at),
 CONSTRAINT fk_doc_kb_tenant FOREIGN KEY(tenant_id,knowledge_base_id) REFERENCES knowledge_base(tenant_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_doc_creator FOREIGN KEY(created_by) REFERENCES `user`(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE document_version (
 id BINARY(16) NOT NULL, document_id BINARY(16) NOT NULL, version_no INT UNSIGNED NOT NULL, tenant_id BINARY(16) NOT NULL,
 source_file_id BINARY(16) NOT NULL, source_sha256 BINARY(32) NOT NULL, media_type VARCHAR(127) NOT NULL,
 byte_size BIGINT UNSIGNED NOT NULL, original_filename VARCHAR(255) NULL, status VARCHAR(24) NOT NULL,
 processing_config JSON NULL, error_code VARCHAR(64) NULL, error_message VARCHAR(1000) NULL,
 created_by BINARY(16) NOT NULL, created_at DATETIME(3) NOT NULL, activated_at DATETIME(3) NULL, retired_at DATETIME(3) NULL,
 PRIMARY KEY(id), UNIQUE KEY uq_dv_doc_version(document_id,version_no), UNIQUE KEY uq_dv_doc_id(document_id,id), UNIQUE KEY uq_dv_tenant_id(tenant_id,id),
 KEY ix_dv_doc_status_created(document_id,status,created_at),
 CONSTRAINT fk_dv_doc_tenant FOREIGN KEY(tenant_id,document_id) REFERENCES document(tenant_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_dv_file_tenant FOREIGN KEY(tenant_id,source_file_id) REFERENCES file_object(tenant_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_dv_creator FOREIGN KEY(created_by) REFERENCES `user`(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE document ADD CONSTRAINT fk_doc_active_version FOREIGN KEY(id,active_version_id) REFERENCES document_version(document_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT;

CREATE TABLE processing_task (
 id BINARY(16) NOT NULL, tenant_id BINARY(16) NOT NULL, resource_type VARCHAR(32) NOT NULL, resource_id BINARY(16) NOT NULL,
 task_type VARCHAR(32) NOT NULL, status VARCHAR(24) NOT NULL, stage VARCHAR(32) NULL, priority SMALLINT NOT NULL DEFAULT 0,
 available_at DATETIME(3) NOT NULL, lease_owner VARCHAR(128) NULL, lease_expires_at DATETIME(3) NULL, heartbeat_at DATETIME(3) NULL,
 attempt_count INT UNSIGNED NOT NULL DEFAULT 0, max_attempts INT UNSIGNED NULL, checkpoint JSON NULL,
 error_code VARCHAR(64) NULL, error_message VARCHAR(1000) NULL, created_by BINARY(16) NULL,
 created_at DATETIME(3) NOT NULL, started_at DATETIME(3) NULL, completed_at DATETIME(3) NULL, updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY(id), KEY ix_task_claim(status,available_at,lease_expires_at,priority), KEY ix_task_tenant_status_created(tenant_id,status,created_at),
 CONSTRAINT fk_task_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_task_creator FOREIGN KEY(created_by) REFERENCES `user`(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chunk (
 id BINARY(16) NOT NULL, document_version_id BINARY(16) NOT NULL, chunk_index INT UNSIGNED NOT NULL,
 content LONGTEXT NOT NULL, content_sha256 BINARY(32) NULL, token_count INT UNSIGNED NULL, metadata JSON NULL, created_at DATETIME(3) NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY uq_chunk_version_index(document_version_id,chunk_index), UNIQUE KEY uq_chunk_version_id(document_version_id,id),
 CONSTRAINT fk_chunk_version FOREIGN KEY(document_version_id) REFERENCES document_version(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chunk_vector (
 chunk_id BINARY(16) NOT NULL, embedding_profile_id BINARY(16) NOT NULL, index_status VARCHAR(24) NOT NULL,
 last_indexed_at DATETIME(3) NULL, last_error_code VARCHAR(64) NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY(chunk_id,embedding_profile_id), KEY ix_cv_profile_status_updated(embedding_profile_id,index_status,updated_at),
 CONSTRAINT fk_cv_chunk FOREIGN KEY(chunk_id) REFERENCES chunk(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_cv_profile FOREIGN KEY(embedding_profile_id) REFERENCES embedding_profile(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_session (
 id BINARY(16) NOT NULL, tenant_id BINARY(16) NOT NULL, user_id BINARY(16) NOT NULL, knowledge_base_id BINARY(16) NOT NULL,
 title VARCHAR(255) NULL, status VARCHAR(24) NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, deleted_at DATETIME(3) NULL,
 PRIMARY KEY(id), KEY ix_session_tenant_user_updated(tenant_id,user_id,updated_at),
 CONSTRAINT fk_session_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_session_user FOREIGN KEY(user_id) REFERENCES `user`(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_session_kb_tenant FOREIGN KEY(tenant_id,knowledge_base_id) REFERENCES knowledge_base(tenant_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_message (
 id BINARY(16) NOT NULL, session_id BINARY(16) NOT NULL, sequence_no BIGINT UNSIGNED NOT NULL, role VARCHAR(16) NOT NULL,
 content LONGTEXT NOT NULL, status VARCHAR(24) NOT NULL, model_name VARCHAR(160) NULL, token_input INT UNSIGNED NULL,
 token_output INT UNSIGNED NULL, error_code VARCHAR(64) NULL, created_at DATETIME(3) NOT NULL, completed_at DATETIME(3) NULL,
 PRIMARY KEY(id), UNIQUE KEY uq_message_session_sequence(session_id,sequence_no),
 CONSTRAINT fk_message_session FOREIGN KEY(session_id) REFERENCES chat_session(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE citation (
 id BINARY(16) NOT NULL, message_id BINARY(16) NOT NULL, `rank` SMALLINT UNSIGNED NOT NULL, document_id BINARY(16) NOT NULL,
 document_version_id BINARY(16) NOT NULL, chunk_id BINARY(16) NOT NULL,
 document_title_snapshot VARCHAR(255) NULL, excerpt_snapshot TEXT NULL, source_locator VARCHAR(512) NULL, created_at DATETIME(3) NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY uq_citation_message_rank(message_id,`rank`),
 KEY ix_citation_doc_version(document_id,document_version_id), KEY ix_citation_version_chunk(document_version_id,chunk_id),
 CONSTRAINT fk_citation_message FOREIGN KEY(message_id) REFERENCES chat_message(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_citation_document_version FOREIGN KEY(document_id,document_version_id) REFERENCES document_version(document_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_citation_version_chunk FOREIGN KEY(document_version_id,chunk_id) REFERENCES chunk(document_version_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_log (
 id BINARY(16) NOT NULL, tenant_id BINARY(16) NULL, actor_user_id BINARY(16) NULL, action VARCHAR(64) NOT NULL,
 resource_type VARCHAR(32) NULL, resource_id BINARY(16) NULL, trace_id VARCHAR(64) NULL, outcome VARCHAR(16) NOT NULL,
 details JSON NULL, created_at DATETIME(3) NOT NULL, PRIMARY KEY(id),
 KEY ix_audit_tenant_created(tenant_id,created_at), KEY ix_audit_action_created(action,created_at),
 CONSTRAINT fk_audit_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_audit_actor FOREIGN KEY(actor_user_id) REFERENCES `user`(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tenant_quota (
 id BINARY(16) NOT NULL, tenant_id BINARY(16) NOT NULL, quota_key VARCHAR(64) NOT NULL,
 limit_value BIGINT UNSIGNED NOT NULL, used_value BIGINT UNSIGNED NOT NULL DEFAULT 0,
 period_start DATETIME(3) NULL, period_end DATETIME(3) NULL, updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY uq_quota_tenant_key(tenant_id,quota_key),
 CONSTRAINT fk_quota_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
