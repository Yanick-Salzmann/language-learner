package ch.yanick.ai.ailanguagelearner.service

import ch.yanick.ai.ailanguagelearner.utils.ProcessExecutor
import ch.yanick.ai.ailanguagelearner.utils.calculateSha256AsHexString
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.io.File
import java.math.BigInteger
import java.net.ServerSocket
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class TextToSpeechService {
    private val log = LoggerFactory.getLogger(TextToSpeechService::class.java)

    private val acceptorThread = Thread(this::serverThread)
    private var acceptorPort: Int = -1
    private lateinit var serverSocket: ServerSocket

    private val startupLock = ReentrantLock()
    private val startupVariable = startupLock.newCondition()
    private var startupComplete = false
    private var startupError: Throwable? = null

    private var isShutdownRequest = false

    @PostConstruct
    fun initialize() {
        val targetFolder = this.setupVenv()
        this.setupXttsModel(targetFolder)
        this.startXttsServerThread()
    }

    @PreDestroy
    fun shutdown() {
        isShutdownRequest = true
        if (acceptorThread.isAlive) {
            serverSocket.close()
            acceptorThread.interrupt()
            acceptorThread.join()
        }
    }

    private fun startXttsServerThread() {
        log.info("Starting XTTS server thread...")
        acceptorThread.start()
        startupLock.withLock {
            while (!startupComplete) {
                try {
                    startupVariable.await()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    log.error("XTTS server thread interrupted during startup", e)
                    return
                }
            }
        }

        if (startupError != null) {
            log.error("XTTS server failed to start", startupError)
            throw IllegalStateException("Failed to start XTTS server", startupError)
        } else {
            log.info("XTTS server started successfully on port $acceptorPort")
        }
    }

    private fun serverThread() {

        try {
            serverSocket = ServerSocket(0)
            acceptorPort = serverSocket.localPort
            log.info("XTTS acceptor server started on port $acceptorPort")

            startupError = null
            startupComplete = true
            startupLock.withLock {
                startupVariable.signalAll()
            }
        } catch (e: Throwable) {
            startupError = e
            startupComplete = true
            startupLock.withLock {
                startupVariable.signalAll()
            }
        }

        try {
            while (!Thread.currentThread().isInterrupted) {
                val client = serverSocket.accept()
                client.inputStream.use {
                    do {
                        val length = BigInteger(it.readNBytes(4)).toInt()
                        if (length > 0) {
                            onData(it.readNBytes(length))
                        }
                    } while (length > 0)
                }
            }
        } catch (e: Exception) {
            if (isShutdownRequest) {
                log.info("Server thread interrupted, shutting down gracefully")
            } else {
                log.error("Error in XTTS server thread", e)
            }
        }
    }

    fun generateSpeech(text: String, language: String = "en"): Flux<DataBuffer> {
        return Flux.empty()
    }

    private fun onData(data: ByteArray) {

    }

    private fun setupXttsModel(folder: File) {
        val modelDir = File(folder, "XTTS-v2")
        if (File(modelDir, "config.json").exists()) {
            log.info("XTTS model already exists in $modelDir")
            return
        }

        if (modelDir.exists()) {
            log.info("XTTS model directory already exists, removing it: $modelDir")
            modelDir.deleteRecursively()
        }

        log.info("Setting up XTTS model in $modelDir")
        if (!modelDir.mkdirs()) {
            throw IllegalStateException("Unable to create directory: $modelDir")
        }

        if (ProcessExecutor.executeCommand(
                modelDir,
                "git",
                "clone",
                "https://huggingface.co/coqui/XTTS-v2",
                "."
            ) != 0
        ) {
            throw IllegalStateException("Failed to clone XTTS model repository")
        }
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
