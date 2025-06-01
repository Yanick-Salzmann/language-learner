package ch.yanick.ai.ailanguagelearner.utils

import java.security.MessageDigest

fun ByteArray.calculateSha256AsHexString(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(this)
    return hash.joinToString("") { "%02x".format(it) }
}