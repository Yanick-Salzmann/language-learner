package ch.yanick.ai.ailanguagelearner.service

import ch.yanick.ai.ailanguagelearner.config.AiConfiguration
import ch.yanick.ai.ailanguagelearner.utils.ProcessExecutor
import ch.yanick.ai.ailanguagelearner.utils.ResourceUtils
import ch.yanick.ai.ailanguagelearner.utils.calculateSha256AsHexString
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.io.File
import java.math.BigInteger
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Semaphore
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class TextToSpeechService(
    private val ttsConfiguration: AiConfiguration.TtsConfig,
    private val pythonService: PythonService,
    private val ffmpegService: FfmpegService
) {
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

    private val limiter = Semaphore(1, true)

    private val mapper = jacksonObjectMapper()

    private var dataCallback: (data: ByteArray) -> Unit = {}

    private lateinit var workingDir: File

    data class TTSRequest(val text: String, val language: String)

    @PostConstruct
    fun initialize() {
        log.info("Initializing TextToSpeechService...")
        val cudaVersion = ensureCudaInstalled(ttsConfiguration.cudaVersion)
        val targetFolder = this.setupVenv(cudaVersion)
        workingDir = targetFolder
        ffmpegService.downloadFfmpeg(targetFolder)
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

    fun generateSpeech(text: String, language: String = "en"): Flux<DataBuffer> {
        // remove all emojis from text
        val sanitizedText = text.replace(Regex("[\\p{So}\\p{Cn}]+"), "")

        limiter.acquire()
        try {
            val client = acceptedClient ?: return Flux.empty()
            val factory = DefaultDataBufferFactory()
            return ffmpegService.createWavFlux(Flux.create { sink ->
                dataCallback = {
                    if (it.isEmpty()) {
                        sink.complete()
                    } else {
                        sink.next(it)
                    }
                }
                client.outputStream.write(mapper.writeValueAsBytes(TTSRequest(sanitizedText, language)).plus(1))
            }, workingDir)
        } finally {
            limiter.release()
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
                    while (!isShutdownRequest) {
                        val length = BigInteger(it.readNBytes(4)).toInt()
                        onData(it.readNBytes(length))
                    }
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

    private fun onData(data: ByteArray) {
        dataCallback(data)
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
        pythonService.initializeVenv(
            targetFolder,
            cudaVersion,
            "spacy[ja]",
            "git+https://github.com/idiap/coqui-ai-TTS"
        )
        return targetFolder
    }

    private fun checkAndExtractSample(folder: File) {
        val content = ResourceUtils.resourceToByteArray("/tts/generated.wav")
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

    private fun ensureCudaInstalled(requestedVersion: String): String {
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
        val shortVersion = "cu${version.replace(".", "")}"
        if (requestedVersion.isNotBlank() && requestedVersion != shortVersion) {
            log.warn("Requested cuda dependency version $requestedVersion but found installed version to be $shortVersion. Using $requestedVersion")
        }

        return requestedVersion.ifBlank { shortVersion }
    }
}