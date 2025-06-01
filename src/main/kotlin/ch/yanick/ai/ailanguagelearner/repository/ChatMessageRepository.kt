package ch.yanick.ai.ailanguagelearner.repository

import ch.yanick.ai.ailanguagelearner.model.ChatMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {
    fun findBySessionIdOrderByTimestampAsc(sessionId: String): List<ChatMessage>
    fun findTop10BySessionIdOrderByTimestampDesc(sessionId: String): List<ChatMessage>
}
