package ai.realteeth.imagejobserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ImageJobServerApplication

fun main(args: Array<String>) {
	runApplication<ImageJobServerApplication>(*args)
}
