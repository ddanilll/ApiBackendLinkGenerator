package com.example.linkgenerator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.time.Duration

@SpringBootApplication
class LinkGeneratorApplication

fun main(args: Array<String>) {
    runApplication<LinkGeneratorApplication>(*args)
}