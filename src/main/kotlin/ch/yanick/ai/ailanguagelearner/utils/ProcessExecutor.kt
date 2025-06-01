package ch.yanick.ai.ailanguagelearner.utils

import java.io.File

object ProcessExecutor {
    fun executeCommand(directory: File, vararg command: String, environment: Map<String, String> = emptyMap()): Int {
        val process = ProcessBuilder(*command)
            .directory(directory)
            .inheritIO()
            .also {
                it.environment().putAll(environment)
            }.start()

        return process.waitFor()
    }
}