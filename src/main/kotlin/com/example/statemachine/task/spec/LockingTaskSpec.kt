package com.example.statemachine.task.spec

import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import org.slf4j.LoggerFactory
import java.io.Serializable
import java.time.Duration
import java.time.Instant

abstract class LockingTaskSpec<P : Serializable>(
    private val lockProvider: LockProvider,
    private val lockKeyProvider: (TaskContext<P>) -> String,
    private val lockDurationMs: Long = 30000L,
) : TaskSpec<P> {
    final override fun execute(context: TaskContext<P>): TaskResult {
        val lockKey = lockKeyProvider(context)
        val lockConfig =
            LockConfiguration(
                Instant.now(),
                lockKey,
                Duration.ofMillis(lockDurationMs),
                Duration.ZERO,
            )
        val lock = lockProvider.lock(lockConfig)

        if (lock.isEmpty) {
            log.debug("Lock unavailable for {}, returning retry", lockKey)
            return TaskResult.retry("Lock unavailable: $lockKey")
        }

        return try {
            executeWithLock(context)
        } finally {
            lock.get().unlock()
        }
    }

    protected abstract fun executeWithLock(context: TaskContext<P>): TaskResult

    companion object {
        private val log = LoggerFactory.getLogger(LockingTaskSpec::class.java)
    }
}
