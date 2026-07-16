package com.example.statemachine.task.scheduler

import com.example.statemachine.task.spec.TaskSpec
import com.github.kagkarlsson.scheduler.task.Task
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.Serializable

@Configuration
class TaskSpecAutoConfiguration(
    private val taskSpecs: List<TaskSpec<*>>,
    private val adapterFactory: TaskSpecAdapterFactory,
) {
    @Bean
    fun dbSchedulerTasks(): List<Task<*>> =
        taskSpecs.map { spec ->
            log.info("Creating db-scheduler task for: {}", spec.taskName)
            @Suppress("UNCHECKED_CAST")
            adapterFactory.createOneTimeTask(spec as TaskSpec<Serializable>)
        }

    companion object {
        private val log = LoggerFactory.getLogger(TaskSpecAutoConfiguration::class.java)
    }
}
