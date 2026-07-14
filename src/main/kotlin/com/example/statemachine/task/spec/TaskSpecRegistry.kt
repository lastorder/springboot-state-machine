package com.example.statemachine.task.spec

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class TaskSpecRegistry(
    private val taskSpecs: List<TaskSpec<*>>,
) {
    private val registry: MutableMap<String, TaskSpec<*>> = mutableMapOf()

    @PostConstruct
    fun init() {
        taskSpecs.forEach { spec ->
            registry[spec.taskName] = spec
            log.info("Registered TaskSpec: {} -> {}", spec.taskName, spec.javaClass.simpleName)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <P : java.io.Serializable> getSpec(taskName: String): TaskSpec<P>? = registry[taskName] as? TaskSpec<P>

    fun getAllSpecs(): Map<String, TaskSpec<*>> = registry.toMap()

    companion object {
        private val log = LoggerFactory.getLogger(TaskSpecRegistry::class.java)
    }
}
