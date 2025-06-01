package ch.yanick.ai.ailanguagelearner.controller

import ch.yanick.ai.ailanguagelearner.model.ChatMessage
import ch.yanick.ai.ailanguagelearner.model.ChatSession
import ch.yanick.ai.ailanguagelearner.service.LanguageLearningService
import ch.yanick.ai.ailanguagelearner.service.TextToSpeechService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.reactor.asFlux
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = ["*"])
class LanguageLearnerController(
    private val languageLearningService: LanguageLearningService,
    private val textToSpeechService: TextToSpeechService
) {
    private val mapper = jacksonObjectMapper()

    @PostMapping("/chat/sessions")
    fun createChatSession(@RequestBody request: CreateSessionRequest): Mono<ChatSession> {
        return Mono.fromCallable {
            languageLearningService.createChatSession(request.language)
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

    @GetMapping("/chat/sessions/{sessionId}/messages", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun sendMessage(
        @PathVariable("sessionId") sessionId: String,
        @RequestParam("language") language: String,
        @RequestParam("message") message: String
    ): Flux<ServerSentEvent<String>> {
        return callbackFlow {
            languageLearningService.processUserMessage(sessionId, message, language) {
                if (!it.isEnd) {
                    trySend(
                        ServerSentEvent
                            .builder(mapper.writeValueAsString(it))
                            .id(UUID.randomUUID().toString())
                            .event("message")
                            .build()
                    )
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

    @PostMapping("/tts")
    fun generateSpeechFromText(
        @RequestBody request: TTSRequest
    ): Flux<DataBuffer> {
        return textToSpeechService.generateSpeech(request.text, request.language)
    }
}

data class CreateSessionRequest(
    val language: String
)

data class TTSRequest(
    val text: String,
    val language: String = "en"
)
