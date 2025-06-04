package ch.yanick.ai.ailanguagelearner.service

import ch.yanick.ai.ailanguagelearner.utils.ProcessExecutor
import ch.yanick.ai.ailanguagelearner.utils.executeGetDownload
import ch.yanick.ai.ailanguagelearner.utils.executeJsonGet
import ch.yanick.ai.ailanguagelearner.utils.logger
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.scheduler.Schedulers
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.time.Instant
import java.util.zip.ZipInputStream
import kotlin.io.path.absolutePathString

data class GithubReleaseAsset(
    val url: String?,
    val browserDownloadUrl: String?,
    val id: Int,
    val nodeId: String?,
    val name: String?,
    val label: String?,
    val state: String?,
    val contentType: String?,
    val size: Int,
    val digest: String?,
    val downloadCount: Int,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val uploader: Map<String, Any>? // irrelevant
)

data class GithubRelease(
    val url: String?,
    val htmlUrl: String?,
    val assetsUrl: String?,
    val uploadUrl: String?,
    val tarballUrl: String?,
    val zipballUrl: String?,
    val id: Int = 0,
    val nodeId: String?,
    val tagName: String?,
    val targetCommitish: String?,
    val name: String?,
    val body: String?,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val createdAt: Instant = Instant.ofEpochMilli(0),
    val publishedAt: Instant = Instant.ofEpochMilli(0),
    val author: Map<String, Any> = emptyMap(), // irrelevant
    val assets: List<GithubReleaseAsset> = emptyList()
)

@Component
class FfmpegService {
    private val log by logger()

    val mapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .addInterceptor(HttpLoggingInterceptor {
            log.info(it)
        }.apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    fun createWavFlux(pcmFlux: Flux<ByteArray>, workingDir: File): Flux<DataBuffer> {
        return Flux.create { sink ->
            @Suppress("BlockingMethodInNonBlockingContext") // This is for ProcessBuilder.start, but it is a local executable that should launch instantly
            val proc = ProcessExecutor.buildCommand(
                File(workingDir, "ffmpeg/bin"),
                Paths.get(workingDir.absolutePath, "ffmpeg", "bin", "ffmpeg.exe").absolutePathString(),
                "-f",
                "f32le",
                "-ar",
                "22050",
                "-ac",
                "1",
                "-i",
                "-",
                "-f",
                "wav",
                "-"
            ).redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()

            val inputDisposable = pcmFlux.doOnComplete {
                proc.outputStream.close()
            }.doOnError { error ->
                log.error("Error in PCM flux", error)
                proc.outputStream.close()
            }.subscribeOn(Schedulers.boundedElastic())
                .subscribe { data ->
                    proc.outputStream.write(data)
                    proc.outputStream.flush()
                }

            val conversionThread = runFfmpegConversionThread(proc, sink)

            sink.onCancel {
                inputDisposable.dispose()
                try {
                    proc.destroyForcibly()
                } catch (e: Exception) {
                    log.warn("Error destroying FFmpeg process", e)
                }

                conversionThread.interrupt()
            }
        }
    }

    private fun runFfmpegConversionThread(proc: Process, sink: FluxSink<DataBuffer>): Thread =
        Thread {
            val bufferFactory = DefaultDataBufferFactory()
            try {
                proc.inputStream.buffered().use { outputStream ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (outputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (sink.isCancelled) break
                        sink.next(bufferFactory.wrap(ByteBuffer.wrap(buffer.copyOf(bytesRead))))
                    }
                }
                sink.complete()
            } catch (e: Exception) {
                if (!sink.isCancelled) {
                    log.error("Error reading FFmpeg output", e)
                    sink.error(e)
                }
            } finally {
                try {
                    proc.inputStream.close()
                    proc.outputStream.close()
                    proc.waitFor()
                } catch (e: Exception) {
                    log.warn("Error cleaning up FFmpeg process", e)
                }
            }
        }.also { it.start() }


    fun downloadFfmpeg(targetFolder: File) {
        if (File(targetFolder, if (isWindows) "ffmpeg/bin/ffmpeg.exe" else "ffmpeg/bin/ffmpeg").exists()) {
            log.info("FFmpeg already exists in $targetFolder, skipping download")
            return
        }

        val lastRelease =
            executeJsonGet<List<GithubRelease>>(
                httpClient,
                "https://api.github.com/repos/BtbN/FFmpeg-Builds/releases",
                mapper
            ).maxByOrNull {
                it.publishedAt
            } ?: throw IllegalStateException("No FFmpeg releases found in repository BtbN/FFmpeg-Builds")

        val osString = osString()
        val asset = lastRelease.assets.firstOrNull {
            it.browserDownloadUrl?.contains(osString()) ?: false
        } ?: throw IllegalStateException(
            "No FFmpeg asset found for OS $osString in release ${lastRelease.assets}"
        )

        val ffmpegTar = executeGetDownload(
            httpClient,
            asset.browserDownloadUrl
                ?: throw IllegalStateException("Asset URL is null"), // cannot happen here as it would have been filtered out
        )

        ZipInputStream(ffmpegTar.inputStream()).use {
            log.info("Extracting FFmpeg to $targetFolder")
            var entry = it.nextEntry
            var firstEntry = true
            var firstDir = ""
            while (entry != null) {
                if (firstEntry) {
                    firstEntry = false
                    firstDir = entry.name.substringBefore('/')
                }

                val path = if (firstDir.isNotBlank()) {
                    "ffmpeg/" + entry.name.removePrefix("$firstDir/")
                } else {
                    entry.name
                }

                if (!entry.isDirectory) {
                    val file = File(targetFolder, path)
                    if (!file.parentFile.exists() && !file.parentFile.mkdirs()) {
                        throw IllegalStateException("Unable to create directory: ${file.parentFile}")
                    }
                    it.copyTo(file.outputStream())
                    log.info("Extracted ${file.absolutePath}")
                }
                entry = it.nextEntry
            }
        }
    }

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    private fun osString(): String {
        return with(System.getProperty("os.name").lowercase()) {
            when {
                contains("win") -> "win64-gpl-shared"
                contains("mac") -> throw IllegalStateException("Unfortunately, macOS is not supported yet")
                contains("nux") -> "linx64-lgpl-shared"
                else -> throw IllegalStateException("Unsupported OS: $this")
            }
        }
    }
}