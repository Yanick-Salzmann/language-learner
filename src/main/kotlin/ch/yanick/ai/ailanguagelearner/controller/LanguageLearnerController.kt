package ch.yanick.ai.ailanguagelearner.controller

import ch.yanick.ai.ailanguagelearner.model.ChatMessage
import ch.yanick.ai.ailanguagelearner.model.ChatSession
import ch.yanick.ai.ailanguagelearner.service.LanguageLearningService
import ch.yanick.ai.ailanguagelearner.service.TextToSpeechService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.flux
import kotlinx.coroutines.reactor.mono
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = ["*"])
class LanguageLearnerController(
    private val languageLearningService: LanguageLearningService,
    private val textToSpeechService: TextToSpeechService
) {
    private val mapper = jacksonObjectMapper()

    @GetMapping("/hello")
    fun hello(): Mono<Map<String, Any>> {
        return Mono.just(
            mapOf(
                "message" to "Welcome to AI Language Learner!",
                "timestamp" to LocalDateTime.now(),
                "status" to "active"
            )
        )
    }

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
                            .event("close")
                            .build()
                    )
                    close()
                }
            }
            awaitClose()
        }.asFlux()
    }

    @GetMapping("/tts/{messageId}", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun generateSpeech(
        @PathVariable messageId: Long,
        @RequestParam(defaultValue = "en") language: String
    ): Flux<DataBuffer> {
        return Mono.fromCallable {
            // TODO: Fetch the actual message content by ID from the database
            // For now, we'll use a placeholder
            "This is a sample text for speech generation with message ID: $messageId"
        }.flatMapMany { text ->
            textToSpeechService.generateSpeech(text, language)
        }
    }

    @PostMapping("/tts")
    fun generateSpeechFromText(
        @RequestBody request: TTSRequest
    ): Flux<DataBuffer> {
        return textToSpeechService.generateSpeech(request.text, request.language)
    }

    @GetMapping("/lessons")
    fun getLessons(): Flux<Lesson> {
        return Flux.fromIterable(
            listOf(
                Lesson(1, "Basic Greetings", "Learn common greetings", "beginner"),
                Lesson(2, "Numbers 1-10", "Count from 1 to 10", "beginner"),
                Lesson(3, "Colors", "Learn basic colors", "beginner"),
                Lesson(4, "Present Tense", "Present tense conjugation", "intermediate"),
                Lesson(5, "Past Tense", "Past tense conjugation", "intermediate")
            )
        ).delayElements(Duration.ofMillis(100)) // Simulate some delay
    }

    @GetMapping("/lessons/{id}")
    fun getLesson(@PathVariable id: Long): Mono<Lesson> {
        return Flux.fromIterable(
            listOf(
                Lesson(1, "Basic Greetings", "Learn common greetings", "beginner"),
                Lesson(2, "Numbers 1-10", "Count from 1 to 10", "beginner"),
                Lesson(3, "Colors", "Learn basic colors", "beginner"),
                Lesson(4, "Present Tense", "Present tense conjugation", "intermediate"),
                Lesson(5, "Past Tense", "Past tense conjugation", "intermediate")
            )
        ).filter { it.id == id }
            .next()
            .switchIfEmpty(Mono.error(RuntimeException("Lesson not found with id: $id")))
    }

    @GetMapping("/stream")
    fun streamData(): Flux<String> {
        return Flux.interval(Duration.ofSeconds(1))
            .map { "Streaming data point: $it at ${LocalDateTime.now()}" }
            .take(10)
    }
}

data class Lesson(
    val id: Long,
    val title: String,
    val description: String,
    val level: String
)

data class CreateSessionRequest(
    val language: String
)

data class ChatMessageRequest(
    val content: String,
    val language: String
)

data class TTSRequest(
    val text: String,
    val language: String = "en"
)
