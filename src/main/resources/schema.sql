-- db-scheduler table
CREATE TABLE IF NOT EXISTS scheduled_tasks (
    task_name VARCHAR(100) NOT NULL,
    task_instance VARCHAR(100) NOT NULL,
    task_data BYTEA,
    execution_time TIMESTAMP WITH TIME ZONE NOT NULL,
    picked BOOLEAN NOT NULL DEFAULT FALSE,
    picked_by VARCHAR(50),
    last_success TIMESTAMP WITH TIME ZONE,
    last_failure TIMESTAMP WITH TIME ZONE,
    consecutive_failures INT,
    last_heartbeat TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL,
    PRIMARY KEY (task_name, task_instance)
);

-- shedlock table
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- barrier aggregate table
CREATE TABLE IF NOT EXISTS barrier_aggregate (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_key VARCHAR(255) NOT NULL,
    barrier_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'WAITING',
    passed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT idx_barrier_aggregate_lookup UNIQUE (aggregate_type, aggregate_key, barrier_type)
);
