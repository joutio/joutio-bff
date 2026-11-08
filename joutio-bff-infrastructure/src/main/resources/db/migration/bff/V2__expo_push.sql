CREATE TABLE expo_push_subscription (
    installation_id varchar(64) PRIMARY KEY,
    user_id varchar(64) NOT NULL,
    token varchar(255) NOT NULL UNIQUE,
    platform varchar(10) NOT NULL,
    rematches boolean NOT NULL,
    registered_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_expo_push_user ON expo_push_subscription(user_id);
CREATE TABLE expo_push_delivery (
    id varchar(36) PRIMARY KEY,
    notification_id varchar(64) NOT NULL,
    installation_id varchar(64) NOT NULL,
    user_id varchar(64) NOT NULL,
    token varchar(255) NOT NULL,
    type varchar(40) NOT NULL,
    source_id varchar(64),
    game_id varchar(64),
    expires_at timestamptz NOT NULL,
    ticket varchar(255),
    attempts integer NOT NULL DEFAULT 0,
    next_at timestamptz NOT NULL DEFAULT now(),
    done boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(notification_id, installation_id)
);
CREATE INDEX idx_expo_push_due ON expo_push_delivery(next_at) WHERE done = false;
