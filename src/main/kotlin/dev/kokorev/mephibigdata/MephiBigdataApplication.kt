package dev.kokorev.mephibigdata

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class MephiBigdataApplication

fun main(args: Array<String>) {
    runApplication<MephiBigdataApplication>(*args)
}
