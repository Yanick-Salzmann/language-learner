package ch.yanick.ai.ailanguagelearner.utils

import org.apache.commons.io.IOUtils

object ResourceUtils {
    fun resourceToByteArray(resourcePath: String): ByteArray {
        return javaClass.getResourceAsStream(resourcePath)?.use { IOUtils.toByteArray(it) }
            ?: throw IllegalStateException("Resource not found: $resourcePath")
    }
}