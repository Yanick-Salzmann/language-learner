package ch.yanick.ai.ailanguagelearner.controller

import ch.yanick.ai.ailanguagelearner.model.ChatMessage
import ch.yanick.ai.ailanguagelearner.model.ChatSession
import ch.yanick.ai.ailanguagelearner.repository.ChatMessageRepository
import ch.yanick.ai.ailanguagelearner.service.LanguageLearningService
import ch.yanick.ai.ailanguagelearner.service.TextToSpeechService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.runBlocking
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import kotlin.jvm.optionals.getOrNull

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = ["*"])
class LanguageLearnerController(
    private val languageLearningService: LanguageLearningService,
    private val textToSpeechService: TextToSpeechService,
    private val chatMessageRepository: ChatMessageRepository
) {
    private val mapper = jacksonObjectMapper()

    @PostMapping("/chat/sessions")
    fun createChatSession(): Mono<ChatSession> {
        return Mono.fromCallable {
            languageLearningService.createChatSession()
        }
    }

    @GetMapping("/chat/sessions")
    fun getAllChatSessions(): Mono<List<ChatSession>> {
        return Mono.fromCallable {
            languageLearningService.getAllChatSessions()
        }
    }

    @GetMapping("/chat/sessions/{sessionId}/messages")
    fun getChatHistory(@PathVariable sessionId: String): Mono<List<ChatMessage>> {
        return Mono.fromCallable {
            languageLearningService.getChatHistory(sessionId)
        }
    }

    @GetMapping("/chat/sessions/{sessionId}/messages/nextId")
    fun saveNewTemporaryMessage(@PathVariable sessionId: String): Mono<Long> {
        return Mono.fromCallable {
            runBlocking {
                chatMessageRepository.save(ChatMessage(0L, sessionId)).id
            }
        }
    }

    @GetMapping("/chat/sessions/{sessionId}/messages/new", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun sendMessage(
        @PathVariable("sessionId") sessionId: String,
        @RequestParam("id") messageId: Long,
        @RequestParam("language") language: String,
        @RequestParam("message") message: String
    ): Flux<ServerSentEvent<String>> {
        return callbackFlow {
            val fullResponse = StringBuilder()

            languageLearningService.processUserMessage(sessionId, messageId, message, language) {
                if (!it.isEnd) {
                    fullResponse.append(it.content)
                    val isThinking = fullResponse.contains("<think>") && !fullResponse.contains("</think>")
                    if (!isThinking) {
                        trySend(
                            ServerSentEvent
                                .builder(mapper.writeValueAsString(it))
                                .id(UUID.randomUUID().toString())
                                .event("message")
                                .build()
                        )
                    }
                } else {
                    trySend(
                        ServerSentEvent
                            .builder(mapper.writeValueAsString(it))
                            .id(UUID.randomUUID().toString())
                            .event("message")
                            .build()
                    )
                    close()
                }
            }
            awaitClose()
        }.asFlux()
    }

    @GetMapping("/chat/sessions/{sessionId}/messages/{messageId}/tts", produces = ["audio/wav"])
    fun generateSpeechFromText(
        @RequestParam("language") language: String,
        @PathVariable("sessionId") sessionId: String,
        @PathVariable("messageId") messageId: Long
    ): Flux<DataBuffer> {
        val message = chatMessageRepository.findById(messageId).getOrNull()
        if (message == null) {
            return Flux.empty()
        }

        return textToSpeechService.generateSpeech(message.content, language)
    }
}

data class TTSRequest(
    val text: String,
    val language: String = "en"
)
