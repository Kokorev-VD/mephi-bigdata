package dev.kokorev.mephibigdata.config

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.util.concurrent.CountDownLatch

@Component
class ApplicationRunner : CommandLineRunner {
    companion object {
        private val logger = LoggerFactory.getLogger(javaClass)
        private val latch = CountDownLatch(1)
    }

    override fun run(vararg args: String?) {
        logger.info("Application started successfully and is ready. Press Ctrl+C to stop.")

        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutdown signal received. Stopping application...")
            latch.countDown()
        })

        try {
            latch.await()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.error("Application interrupted", e)
        }
    }
}
