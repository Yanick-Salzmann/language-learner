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

    fun executeCommandNoWait(directory: File, vararg command: String, environment: Map<String, String> = emptyMap()): Process {
        return ProcessBuilder(*command)
            .directory(directory)
            .inheritIO()
            .also {
                it.environment().putAll(environment)
            }.start()
    }

    fun executeCommandWithOutput(
        directory: File,
        vararg command: String,
        environment: Map<String, String> = emptyMap()
    ): Pair<Int, String> {
        val process = ProcessBuilder(*command)
            .directory(directory)
            .redirectErrorStream(true)
            .also {
                it.environment().putAll(environment)
            }.start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        return Pair(exitCode, output)
    }
}