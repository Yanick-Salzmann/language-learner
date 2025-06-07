package ch.yanick.ai.ailanguagelearner.config

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.ollama.OllamaStreamingChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.time.Duration

@Configuration
@ConfigurationProperties(prefix = "ai")
data class AiConfiguration(
    var provider: String = "ollama",
    var timeout: Long = 60000,
    var openai: OpenAiConfig = OpenAiConfig(),
    var azure: AzureConfig = AzureConfig(),
    var ollama: OllamaConfig = OllamaConfig(),
    var tts: TtsConfig = TtsConfig()
) {
    data class OpenAiConfig(
        var apiKey: String = "",
        var model: String = "gpt-3.5-turbo"
    )

    data class AzureConfig(
        var apiKey: String = "",
        var endpoint: String = "",
        var deploymentName: String = ""
    )

    data class OllamaConfig(
        var baseUrl: String = "http://localhost:11434",
        var model: String = "llama2"
    )

    data class TtsConfig(
        var cudaVersion: String = ""
    )

    @Bean
    @Primary
    fun chatLanguageModel(): StreamingChatModel {
        return when (provider.lowercase()) {
            "openai" -> OpenAiStreamingChatModel.builder()
                .apiKey(openai.apiKey)
                .modelName(openai.model)
                .timeout(Duration.ofMillis(timeout))
                .build()

            "ollama" -> OllamaStreamingChatModel.builder()
                .baseUrl(ollama.baseUrl)
                .modelName(ollama.model)
                .timeout(Duration.ofMillis(timeout))
                .build()

            else -> throw IllegalArgumentException("Unsupported AI provider: $provider")
        }
    }

    @Bean
    @Primary
    fun blockingChatModel(): ChatModel {
        return when (provider.lowercase()) {
            "openai" -> OpenAiChatModel.builder()
                .apiKey(openai.apiKey)
                .logRequests(true)
                .logResponses(true)
                .modelName(openai.model)
                .timeout(Duration.ofMillis(timeout))
                .build()

            "ollama" -> OllamaChatModel.builder()
                .baseUrl(ollama.baseUrl)
                .modelName(ollama.model)
                .timeout(Duration.ofMillis(timeout))
                .build()

            else -> throw IllegalArgumentException("Unsupported AI provider: $provider")
        }
    }

    @Bean
    fun ttsConfig(): TtsConfig = tts
}
