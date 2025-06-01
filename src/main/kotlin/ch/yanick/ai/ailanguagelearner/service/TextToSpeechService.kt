package ch.yanick.ai.ailanguagelearner.service

import ch.yanick.ai.ailanguagelearner.utils.ProcessExecutor
import ch.yanick.ai.ailanguagelearner.utils.calculateSha256AsHexString
import jakarta.annotation.PostConstruct
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Service
class TextToSpeechService {
    private val log = LoggerFactory.getLogger(TextToSpeechService::class.java)

    @PostConstruct
    fun initialize() {
        this.setupVenv()
    }

    fun generateSpeech(text: String, language: String = "en"): Flux<DataBuffer> {
        return Flux.empty()
    }

    private fun setupVenv(): File {
        val userFolder = File(System.getProperty("user.home"))
        if (!userFolder.exists()) {
            throw IllegalStateException("User home directory does not exist: $userFolder")
        }

        val targetFolder = File(userFolder, ".ai-language-learner")
        if (targetFolder.exists() && File(targetFolder, "pyvenv.cfg").exists()) {
            checkAndExtractScript(targetFolder)
            return targetFolder
        }

        if (!targetFolder.mkdirs()) {
            throw IllegalStateException("Unable to create directory: $targetFolder")
        }

        log.info("Creating python venv in folder $targetFolder")

        try {
            if (ProcessExecutor.executeCommand(targetFolder, "python.exe", "-m", "venv", ".") != 0) {
                throw IllegalStateException("Failed to initialize python venv in $targetFolder")
            }

            log.info("Done creating python venv, installing required dependencies next...")

            if (ProcessExecutor.executeCommand(
                    targetFolder,
                    "cmd",
                    "/c",
                    "\"Scripts\\activate.bat && pip install git+https://github.com/idiap/coqui-ai-TTS\""
                ) != 0
            ) {
                throw IllegalStateException("Failed to install dependencies")
            }

            checkAndExtractScript(targetFolder)
        } catch (e: Exception) {
            // if anything failed, let's just delete everything so that next time it will try again
            targetFolder.deleteRecursively()
            throw e
        }

        return targetFolder
    }

    private fun checkAndExtractScript(folder: File) {
        val content = javaClass.getResourceAsStream("/python/xtts.py").use { res ->
            res ?: throw IllegalStateException("Python script /python/xtts.py not found in application resources")
            IOUtils.toByteArray(res)
        }

        val hash = content.calculateSha256AsHexString()
        val hashFile = File(folder, "xtts.py.sha256")
        if (hashFile.exists()) {
            val existingHash = hashFile.readText().trim()
            if (existingHash == hash) {
                log.info("Python script xtts.py is already up to date")
                return
            }
        }

        log.info("Extracting python script xtts.py to $folder")
        val scriptFile = File(folder, "xtts.py")
        scriptFile.writeBytes(content)
        hashFile.writeText(hash)
    }
}
