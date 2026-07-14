-- Order table
CREATE TABLE IF NOT EXISTS orders (
    id                      BIGSERIAL PRIMARY KEY,
    product                 VARCHAR(255) NOT NULL,
    quantity                INT NOT NULL,
    amount                  DECIMAL(19,2) NOT NULL,
    status                  VARCHAR(50) NOT NULL,
    inventory_status        VARCHAR(50),
    inventory_reference     VARCHAR(255),
    inventory_check_status  VARCHAR(50),
    pricing_check_status    VARCHAR(50),
    inventory_checked_at    TIMESTAMP WITH TIME ZONE,
    pricing_checked_at      TIMESTAMP WITH TIME ZONE,
    validation_started_at   TIMESTAMP WITH TIME ZONE,
    validation_retry_count  INT DEFAULT 0,
    pricing_reference       VARCHAR(255),
    unit_price              DECIMAL(19,2),
    confirmed_price         DECIMAL(19,2),
    modification_reason     VARCHAR(500),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0
);

-- State Machine persistence
CREATE TABLE IF NOT EXISTS state_machine (
    machine_id          VARCHAR(255) PRIMARY KEY,
    state               VARCHAR(255) NOT NULL,
    state_machine_context BYTEA,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

-- db-scheduler table
CREATE TABLE IF NOT EXISTS scheduled_tasks (
    task_name           VARCHAR(100) NOT NULL,
    task_instance       VARCHAR(100) NOT NULL,
    task_data           BYTEA,
    execution_time      TIMESTAMP WITH TIME ZONE NOT NULL,
    picked              BOOLEAN NOT NULL DEFAULT FALSE,
    picked_by           VARCHAR(50),
    last_success        TIMESTAMP WITH TIME ZONE,
    last_failure        TIMESTAMP WITH TIME ZONE,
    consecutive_failures INT,
    last_heartbeat      TIMESTAMP WITH TIME ZONE,
    version             BIGINT NOT NULL,
    PRIMARY KEY (task_name, task_instance)
);

-- ShedLock table
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
