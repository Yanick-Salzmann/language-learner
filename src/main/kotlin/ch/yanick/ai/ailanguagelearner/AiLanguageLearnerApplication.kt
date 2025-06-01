package ch.yanick.ai.ailanguagelearner

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import ch.yanick.ai.ailanguagelearner.config.AiConfiguration

@SpringBootApplication
@EnableJpaRepositories
@EnableConfigurationProperties(AiConfiguration::class)
class AiLanguageLearnerApplication

fun main(args: Array<String>) {
    runApplication<AiLanguageLearnerApplication>(*args)
}
