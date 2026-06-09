CREATE TABLE IF NOT EXISTS ai_conversations (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    role        VARCHAR(16) NOT NULL,
    title       VARCHAR(160) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_conversation_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ai_conversation_user_role_updated
    ON ai_conversations(user_id, role, updated_at DESC);

CREATE TABLE IF NOT EXISTS ai_messages (
    id              UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    sender          VARCHAR(16) NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_message_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ai_message_conversation_created
    ON ai_messages(conversation_id, created_at ASC);
