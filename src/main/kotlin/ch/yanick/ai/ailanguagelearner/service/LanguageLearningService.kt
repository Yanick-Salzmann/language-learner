package ch.yanick.ai.ailanguagelearner.service

import ch.yanick.ai.ailanguagelearner.model.ChatMessage
import ch.yanick.ai.ailanguagelearner.model.ChatSession
import ch.yanick.ai.ailanguagelearner.model.MessageSender
import ch.yanick.ai.ailanguagelearner.repository.ChatMessageRepository
import ch.yanick.ai.ailanguagelearner.repository.ChatSessionRepository
import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.TokenStream
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
    private val chatSessionRepository: ChatSessionRepository
) {
    private interface Learner {
        fun ask(question: String): TokenStream
    }

    private val memoryMap = mutableMapOf<String, ChatMemory>()

    fun createChatSession(language: String): ChatSession {
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            title = "New $language conversation",
            language = language,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        return chatSessionRepository.save(session)
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
        // Save user message
        var userChatMessage = ChatMessage(
            id = messageId,
            sessionId = sessionId,
            sender = MessageSender.USER,
            content = userMessage,
            language = language,
            timestamp = LocalDateTime.now()
        )

        userChatMessage = chatMessageRepository.save(userChatMessage)

        val service = AiServices.builder(Learner::class.java)
            .streamingChatModel(chatLanguageModel)
            .chatMemory(memoryMap.getOrPut(sessionId) { MessageWindowChatMemory.builder().maxMessages(20).build() })
            .systemMessageProvider {
                buildString {
                    append("You are a helpful language teacher for $language. ")
                    append("Your role is to help the user learn $language by correcting their mistakes, ")
                    append("providing better alternatives, and guiding them through a structured learning path. ")
                    append("Always respond in $language unless the user explicitly asks for an explanation in English. ")
                    append("Be encouraging and provide constructive feedback. Have a lighthearted tone. Use some emojis, but not excessively")
                }
            }
            .build()

        val userMessage = buildString {
            append("Student: $userMessage\n")
            append("Teacher:")
        }

        val aiResponse = service.ask(userMessage)

        val aiChatMessage = ChatMessage(
            sessionId = sessionId,
            sender = MessageSender.ASSISTANT,
            content = "",
            language = language,
            timestamp = LocalDateTime.now()
        )

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
}
