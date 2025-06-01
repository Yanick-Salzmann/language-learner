package ch.yanick.ai.ailanguagelearner.repository

import ch.yanick.ai.ailanguagelearner.model.ChatSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ChatSessionRepository : JpaRepository<ChatSession, String> {
    fun findAllByOrderByUpdatedAtDesc(): List<ChatSession>
}
