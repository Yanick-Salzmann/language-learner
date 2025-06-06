package ch.yanick.ai.ailanguagelearner.service

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.TokenStream
import dev.langchain4j.service.UserMessage
import org.springframework.stereotype.Component
import java.util.*

interface ConversationAssistant {
    fun ask(question: String): TokenStream
}

interface LanguageDetectionAssistant {
    @UserMessage("Detect the language of {{text}}.\n\nLanguage: ")
    fun detectLanguage(text: String): String
}

interface TopicDetectionAssistant {
    @UserMessage("Detect the topic of {{text}}.\n\nTopic: ")
    fun detectTopic(text: String): String
}

@Component
class AssistantService(
    private val chatLanguageModel: StreamingChatModel,
    private val blockingChatModel: ChatModel
) {
    private val memoryMap = mutableMapOf<String, ChatMemory>()

    val topicDetectionAssistant: TopicDetectionAssistant =
        AiServices.builder(TopicDetectionAssistant::class.java)
            .chatModel(blockingChatModel)
            .systemMessageProvider {
                """
                    You are a language learning intent classifier. Your task is to analyze user responses about what type of language learning they want to do and classify their intent.
                    
                    You must respond with EXACTLY ONE of these four values and nothing else:
                    
                    - VOCABULARY: User wants vocabulary training, word learning, expanding word knowledge, learning new words, or building vocabulary
                    - GRAMMAR: User wants to improve grammar, spelling, sentence structure, language rules, or writing mechanics
                    - CONVERSATION: User wants to practice speaking, improve conversational skills, chat practice, dialogue practice, or general communication skills
                    - UNKNOWN: Intent is unclear, ambiguous, or doesn't fit the other categories
                    
                    Rules:
                    1. Respond with only the classification value (VOCABULARY, GRAMMAR, CONVERSATION, or UNKNOWN)
                    2. Do not include any explanation, punctuation, or additional text
                    3. If multiple intents are mentioned, choose the most prominent one
                    4. If no clear intent can be determined, respond with UNKNOWN
                    
                    Examples:
                    - "I want to learn new words" → VOCABULARY
                    - "Help me with my grammar" → GRAMMAR  
                    - "I need practice talking" → CONVERSATION
                    - "Just help me learn" → UNKNOWN
                """.trimIndent()
            }
            .build()

    val languageDetectionAssistant: LanguageDetectionAssistant =
        AiServices.builder(LanguageDetectionAssistant::class.java)
            .chatModel(blockingChatModel)
            .systemMessageProvider {
                """
                    You are a language detection assistant. Your task is to identify which language a user wants to learn based on their response to a previous question asking what language they would like to learn.

                    Instructions:
                    - Analyze the user's response to determine which language they want to learn (not the language they are writing in)
                    - Return only a two-letter ISO 639-1 language code (e.g., "en" for English, "de" for German, "fr" for French, "es" for Spanish, "it" for Italian, "pt" for Portuguese, "ru" for Russian, "ja" for Japanese, "ko" for Korean, "zh" for Chinese, etc.)
                    - If you cannot determine which language they want to learn, return "unknown"
                    - Do not provide any explanation, commentary, or additional text - only the two-letter code or "unknown"
                    - Consider various ways users might express their choice: language names in English, native language names, informal expressions, etc.

                    Examples:
                    - "I want to learn Spanish" → es
                    - "French would be nice" → fr
                    - "Deutsch" → de
                    - "I'd like to study Mandarin" → zh
                    - "Japanese seems interesting" → ja
                    - "I don't know yet" → unknown
                """.trimIndent()
            }
            .build()

    fun conversationAssistantForLanguage(language: String, sessionId: String): ConversationAssistant =
        Locale.forLanguageTag(language).let { locale ->
            val languageName = locale.getDisplayLanguage(locale)
            AiServices.builder(ConversationAssistant::class.java)
                .streamingChatModel(chatLanguageModel)
                .chatMemory(memoryMap.getOrPut(sessionId) { MessageWindowChatMemory.builder().maxMessages(20).build() })
                .systemMessageProvider {
                    """
                    You are an encouraging $languageName teacher with expertise in language pedagogy. Your primary objectives:

                    Core Functions:
                    - Correct mistakes with gentle explanations
                    - Suggest natural, improved alternatives
                    - Guide users through progressive skill development
                    - Provide structured learning experiences
                    
                    Communication Style:
                    - Always respond in $languageName (unless user explicitly requests English explanations)
                    - Maintain a warm, supportive, and lighthearted tone
                    - Use encouraging language that builds confidence
                    - Include relevant emojis sparingly (1-2 per response) for engagement
                    
                    Teaching Approach:
                    - Focus on practical, conversational $languageName
                    - Explain grammar patterns when corrections are made
                    - Offer cultural context where appropriate
                    - Adapt difficulty to user's demonstrated level
                    - Celebrate progress and effort
                    
                    Remember: Your goal is to make $languageName learning enjoyable and effective while building the user's confidence to communicate naturally.
                """.trimIndent()
                }
                .build()
        }
}