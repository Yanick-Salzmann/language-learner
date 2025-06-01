package ch.yanick.ai.ailanguagelearner

import ch.yanick.ai.ailanguagelearner.controller.LanguageLearnerController
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(LanguageLearnerController::class)
class LanguageLearnerControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun `should return hello message`() {
        webTestClient.get()
            .uri("/api/hello")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.message").isEqualTo("Welcome to AI Language Learner!")
            .jsonPath("$.status").isEqualTo("active")
    }

    @Test
    fun `should return lessons list`() {
        webTestClient.get()
            .uri("/api/lessons")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(Any::class.java)
            .hasSize(5)
    }

    @Test
    fun `should return specific lesson`() {
        webTestClient.get()
            .uri("/api/lessons/1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(1)
            .jsonPath("$.title").isEqualTo("Basic Greetings")
    }

    @Test
    fun `should return 404 for non-existent lesson`() {
        webTestClient.get()
            .uri("/api/lessons/999")
            .exchange()
            .expectStatus().is5xxServerError
    }
}
