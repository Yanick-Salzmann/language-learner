package ch.yanick.ai.ailanguagelearner.service

import ch.yanick.ai.ailanguagelearner.utils.ProcessExecutor
import ch.yanick.ai.ailanguagelearner.utils.ResourceUtils
import ch.yanick.ai.ailanguagelearner.utils.calculateSha256AsHexString
import ch.yanick.ai.ailanguagelearner.utils.logger
import org.springframework.stereotype.Component
import java.io.File

@Component
class XttsService {
    private val log by logger()

    fun setupXttsModel(folder: File) {
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
}