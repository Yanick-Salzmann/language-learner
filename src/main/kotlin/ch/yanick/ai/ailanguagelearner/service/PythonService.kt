package ch.yanick.ai.ailanguagelearner.service

import ch.yanick.ai.ailanguagelearner.utils.ProcessExecutor
import ch.yanick.ai.ailanguagelearner.utils.ResourceUtils
import ch.yanick.ai.ailanguagelearner.utils.calculateSha256AsHexString
import ch.yanick.ai.ailanguagelearner.utils.logger
import org.springframework.stereotype.Component
import java.io.File

@Component
class PythonService {
    private val log by logger()

    fun initializeVenv(
        targetFolder: File,
        cudaVersion: String,
        vararg dependencies: String
    ) {
        if (targetFolder.exists() && File(targetFolder, "pyvenv.cfg").exists()) {
            installDependenciesAndCuda(targetFolder, cudaVersion, * dependencies)
            checkAndExtractScript(targetFolder)
            return
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

            installDependenciesAndCuda(targetFolder, cudaVersion, *dependencies)
            checkAndExtractScript(targetFolder)
        } catch (e: Exception) {
            // if anything failed, let's just delete everything so that next time it will try again
            targetFolder.deleteRecursively()
            throw e
        }
    }

    private fun checkAndExtractScript(folder: File) {
        val content = ResourceUtils.resourceToByteArray("/tts/xtts.py")

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

    private fun installDependenciesAndCuda(targetFolder: File, cudaVersion: String, vararg dependencies: String) {
        installDependencies(
            targetFolder,
            *dependencies,
        )
        installDependencies(
            targetFolder, "torch",
            "torchvision",
            "torchaudio",
            "--index-url https://download.pytorch.org/whl/$cudaVersion"
        )
    }

    private fun installDependencies(targetFolder: File, vararg dependencies: String) {
        if (ProcessExecutor.executeCommand(
                targetFolder,
                "cmd",
                "/c",
                "\"Scripts\\activate.bat && pip install ${dependencies.joinToString(" ")}\""
            ) != 0
        ) {
            throw IllegalStateException("Failed to install dependencies")
        }
    }
}