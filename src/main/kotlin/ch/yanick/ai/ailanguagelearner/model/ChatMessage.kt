package ch.yanick.ai.ailanguagelearner.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "chat_messages")
data class ChatMessage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(name = "session_id", nullable = false)
    val sessionId: String = "",
    
    @Column(name = "sender", nullable = false)
    @Enumerated(EnumType.STRING)
    val sender: MessageSender = MessageSender.USER,
    
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    val content: String = "",
    
    @Column(name = "timestamp", nullable = false)
    val timestamp: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "language")
    val language: String? = null
)

enum class MessageSender {
    USER, ASSISTANT
}
