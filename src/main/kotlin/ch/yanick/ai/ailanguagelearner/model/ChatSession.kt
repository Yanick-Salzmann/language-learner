package ch.yanick.ai.ailanguagelearner.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "chat_sessions")
data class ChatSession(
    @Id
    val id: String = "",
    
    @Column(name = "title", nullable = false)
    val title: String = "",
    
    @Column(name = "language", nullable = false)
    val language: String = "",
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
