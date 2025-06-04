package ch.yanick.ai.ailanguagelearner.utils

import java.io.File

object OsUtils {
    val isWindows: Boolean
        get() = System.getProperty("os.name").lowercase().contains("win")

    val isLinux: Boolean
        get() = System.getProperty("os.name").lowercase().contains("nux")

    val isMac: Boolean
        get() = System.getProperty("os.name").lowercase().contains("mac")

    fun binaryName(name: String): String {
        return if (isWindows) {
            "$name.exe"
        } else {
            name
        }
    }
}

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

    fun buildCommand(
        directory: File,
        vararg command: String,
        environment: Map<String, String> = emptyMap()
    ): ProcessBuilder {
        return ProcessBuilder(*command)
            .directory(directory)
            .also {
                it.environment().putAll(environment)
            }
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