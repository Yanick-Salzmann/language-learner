package ch.yanick.ai.ailanguagelearner.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

val httpObjectMapper: ObjectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

fun Response.asString() = this.body?.use {
    it.string()
} ?: ""

inline fun <reified T> executeJsonGet(client: OkHttpClient, url: String) =
    executeJsonGet<T>(client, url, httpObjectMapper)

inline fun <reified T> executeJsonGet(client: OkHttpClient, url: String, mapper: ObjectMapper): T = client.newCall(
    Request.Builder()
        .get()
        .url(url)
        .build()
).execute().also {
    if (!it.isSuccessful) {
        throw IllegalStateException("Error executing GET: ${it.code} ${it.message} - ${it.asString()}")
    }

    it
}.let {
    mapper.readValue<T>(it.asString())
}

fun executeGetDownload(client: OkHttpClient, url: String): ByteArray {
    return client.newCall(
        Request.Builder()
            .get()
            .url(url)
            .build()
    ).execute().also {
        if (!it.isSuccessful) {
            throw IllegalStateException("Error executing GET: ${it.code} ${it.message} - ${it.asString()}")
        }
    }.use { response ->
        response.body?.bytes() ?: throw IllegalStateException("Response body is null")
    }
}