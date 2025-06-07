package ch.yanick.ai.ailanguagelearner.service

import ch.yanick.ai.ailanguagelearner.model.ChatMessage
import ch.yanick.ai.ailanguagelearner.model.ChatSession
import ch.yanick.ai.ailanguagelearner.model.ChatSessionState
import ch.yanick.ai.ailanguagelearner.model.MessageSender
import ch.yanick.ai.ailanguagelearner.repository.ChatMessageRepository
import ch.yanick.ai.ailanguagelearner.repository.ChatSessionRepository
import ch.yanick.ai.ailanguagelearner.utils.logger
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.service.TokenStream
import kotlin.text.append
import kotlin.text.buildString
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

data class ChatMessageChunk(
    val id: Long,
    val isEnd: Boolean,
    val content: String
)

@Service
class LanguageLearningService(
    private val chatLanguageModel: StreamingChatModel,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatSessionRepository: ChatSessionRepository,
    private val assistantService: AssistantService,
    service: AssistantService
) {
    private val log by logger()

    fun createChatSession(): ChatSession {
        var session = ChatSession(
            id = UUID.randomUUID().toString(),
            title = "New conversation",
            language = "",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        session = chatSessionRepository.save(session)

        val rootMessage = ChatMessage(
            sessionId = session.id,
            sender = MessageSender.ASSISTANT,
            content = "Welcome to your language learning session! Please select a language to start.",
            language = "",
            timestamp = LocalDateTime.now()
        )

        chatMessageRepository.save(rootMessage)
        return session
    }

    fun getAllChatSessions(): List<ChatSession> {
        return chatSessionRepository.findAllByOrderByUpdatedAtDesc()
    }

    fun getChatHistory(sessionId: String): List<ChatMessage> {
        return chatMessageRepository.findBySessionIdOrderByTimestampAsc(sessionId)
    }

    suspend fun processUserMessage(
        sessionId: String,
        messageId: Long,
        userMessage: String,
        language: String,
        onMessage: suspend (ChatMessageChunk) -> Unit
    ) {
        val session = chatSessionRepository.findById(sessionId)
            .orElseThrow { IllegalArgumentException("Session with ID $sessionId not found") }

        // Save user message
        val userChatMessage = ChatMessage(
            id = messageId,
            sessionId = sessionId,
            sender = MessageSender.USER,
            content = userMessage,
            language = language,
            timestamp = LocalDateTime.now()
        )

        chatMessageRepository.save(userChatMessage)

        if (session.state == ChatSessionState.SELECT_LANGUAGE) {
            return handleSelectLanguage(userMessage, session, onMessage)
        }

        val service = assistantService.conversationAssistantForLanguage(language, sessionId)

        val userMessage = buildString {
            append("Student: $userMessage\n")
            append("Teacher:")
        }

        val aiResponse = service.ask(userMessage)

        var aiChatMessage = ChatMessage(
            sessionId = sessionId,
            sender = MessageSender.ASSISTANT,
            content = "",
            language = language,
            timestamp = LocalDateTime.now()
        )

        aiChatMessage = chatMessageRepository.save(aiChatMessage)
        processTokenStream(aiChatMessage, sessionId, aiResponse, onMessage)
    }

    private fun processTokenStream(
        aiChatMessage: ChatMessage,
        sessionId: String,
        aiResponse: TokenStream,
        onMessage: suspend (ChatMessageChunk) -> Unit
    ) {
        aiResponse.onPartialResponse {
            runBlocking {
                onMessage(
                    ChatMessageChunk(
                        id = aiChatMessage.id,
                        isEnd = false,
                        content = it
                    )
                )
            }

            chatSessionRepository.findById(sessionId).ifPresent { session ->
                chatSessionRepository.save(session.copy(updatedAt = LocalDateTime.now()))
            }
        }.onCompleteResponse {
            chatMessageRepository.save(aiChatMessage.copy(content = it.aiMessage().text()))
            runBlocking {
                onMessage(
                    ChatMessageChunk(
                        id = aiChatMessage.id,
                        isEnd = true,
                        content = ""
                    )
                )
            }
        }.onError {
            runBlocking {
                onMessage(
                    ChatMessageChunk(
                        id = aiChatMessage.id,
                        isEnd = true,
                        content = "An error occurred: ${it.message ?: "Unknown error"}"
                    )
                )
            }
        }.start()
    }

    private suspend fun handleSelectLanguage(
        message: String,
        session: ChatSession,
        onMessage: suspend (ChatMessageChunk) -> Unit
    ) {
        val aiChatMessage = chatMessageRepository.save(
            ChatMessage(
                sessionId = session.id,
                sender = MessageSender.ASSISTANT,
                content = "",
                language = "",
                timestamp = LocalDateTime.now()
            )
        )

        val language = assistantService.languageDetectionAssistant.detectLanguage(message).lowercase().trim()
        log.info("Determined language: $language for message: $message")

        val isValidLanguage = Locale.getISOLanguages().any { it.equals(language, ignoreCase = true) }
        if (!isValidLanguage) {
            val errorMsg = "Invalid language selected. Please try again."
            respondWithSingleMessage(errorMsg, aiChatMessage, onMessage)
        } else {
            chatSessionRepository.save(
                session.copy(
                    language = language,
                    state = ChatSessionState.SELECT_TOPIC,
                    updatedAt = LocalDateTime.now()
                )
            )
            val languageName = Locale.forLanguageTag(language).run { getDisplayLanguage(this) }
            val welcomeMsg =
                "You have selected $languageName. Let's start learning! What topic would you like to focus on? We can do general conversion, vocabulary building, or grammar exercises."
            respondWithSingleMessage(welcomeMsg, aiChatMessage, onMessage)
        }
    }

    private fun respondWithSingleMessage(
        message: String,
        aiChatMessage: ChatMessage,
        onMessage: suspend (ChatMessageChunk) -> Unit
    ) {
        chatMessageRepository.save(aiChatMessage.copy(content = message))
        runBlocking {
            onMessage(
                ChatMessageChunk(
                    id = aiChatMessage.id,
                    isEnd = false,
                    content = message
                )
            )
            onMessage(
                ChatMessageChunk(
                    id = aiChatMessage.id,
                    isEnd = true,
                    content = ""
                )
            )
        }
    }
}
