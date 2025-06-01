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
import java.net.Socket
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class TextToSpeechService {
    private val log = LoggerFactory.getLogger(TextToSpeechService::class.java)

    private val acceptorThread = Thread(this::serverThread)
    private lateinit var pythonThread: Thread

    private var acceptorPort: Int = -1
    private lateinit var serverSocket: ServerSocket
    private var acceptedClient: Socket? = null

    private val startupLock = ReentrantLock()
    private val startupVariable = startupLock.newCondition()
    private var startupComplete = false
    private var startupError: Throwable? = null

    private var isShutdownRequest = false

    @PostConstruct
    fun initialize() {
        log.info("Initializing TextToSpeechService...")
        val cudaVersion = ensureCudaInstalled()
        val targetFolder = this.setupVenv(cudaVersion)
        this.setupXttsModel(targetFolder)
        this.startXttsServerThread()
        this.startPythonThread(targetFolder)
    }

    @PreDestroy
    fun shutdown() {
        isShutdownRequest = true
        if (acceptorThread.isAlive) {
            serverSocket.close()
            acceptedClient?.close()
            acceptorThread.interrupt()
            acceptorThread.join()
            log.info("XTTS acceptor server thread shut down gracefully")
        }

        if (pythonThread.isAlive) {
            pythonThread.interrupt()
            pythonThread.join()
            log.info("Python XTTS thread shut down gracefully")
        }
    }

    private fun startPythonThread(targetFolder: File) {
        log.info("Starting Python thread for XTTS...")

        // cannot really check for successful startup here, only wait for the socket to connect
        pythonThread = Thread {
            var pythonProc: Process? = null
            try {
                pythonProc = ProcessExecutor.executeCommandNoWait(
                    targetFolder,
                    "cmd.exe",
                    "/C",
                    "Scripts\\activate.bat && python xtts.py $acceptorPort \"${
                        File(
                            targetFolder,
                            "generated.wav"
                        )
                    }\" \"${File(targetFolder, "XTTS-v2")}\"",
                )

                log.info("Python XTTS process started with PID: ${pythonProc.pid()}")
                pythonProc.waitFor()
                log.info("Python XTTS process exited with code: ${pythonProc.exitValue()}")
            } catch (e: Exception) {
                pythonProc?.destroy()
                if (!isShutdownRequest) {
                    log.error("Error starting Python XTTS process", e)
                } else {
                    log.info("Python XTTS process interrupted, shutting down gracefully")
                }
            }
        }
        pythonThread.start()
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
                acceptedClient = serverSocket.accept()
                log.info("XTTS client connected from ${acceptedClient?.inetAddress?.hostAddress}:${acceptedClient?.port}")

                acceptedClient?.inputStream?.use {
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

        checkAndExtractSample(folder)
    }

    private fun setupVenv(cudaVersion: String): File {
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
                    "\"Scripts\\activate.bat && pip3 install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/$cudaVersion\""
                ) != 0
            ) {
                throw IllegalStateException("Failed to install PyTorch dependencies")
            }

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
        val content = resourceToByteArray("/tts/xtts.py")

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

    private fun checkAndExtractSample(folder: File) {
        val content = resourceToByteArray("/tts/generated.wav")
        val sampleFile = File(folder, "generated.wav")
        val hash = content.calculateSha256AsHexString()
        val hashFile = File(folder, "generated.wav.sha256")

        if (sampleFile.exists() && hashFile.exists()) {
            val existingHash = hashFile.readText().trim()
            if (existingHash == hash) {
                log.info("Voice sample generated.wav is already up to date")
                return
            }
        }

        log.info("Extracting voice sample generated.wav to $folder")
        sampleFile.writeBytes(content)
        hashFile.writeText(hash)

    }

    private fun resourceToByteArray(resourcePath: String): ByteArray {
        return javaClass.getResourceAsStream(resourcePath)?.use { IOUtils.toByteArray(it) }
            ?: throw IllegalStateException("Resource not found: $resourcePath")
    }

    private fun ensureCudaInstalled(): String {
        val (exitCode, output) = ProcessExecutor.executeCommandWithOutput(
            File("."),
            "nvcc", "--version"
        )

        if (exitCode != 0) {
            throw IllegalStateException("CUDA is not installed or not found in PATH. Please install CUDA to use XTTS.")
        }

        val versionLine = output.lines().firstOrNull { it.contains("Cuda compilation tools") }
            ?: throw IllegalStateException("Could not determine CUDA version from output: $output")
        val version = versionLine.substringAfter("release").substringBefore(",").trim()

        log.info("Detected CUDA version: $version")
        return "cu${version.replace(".", "")}"
    }
}
