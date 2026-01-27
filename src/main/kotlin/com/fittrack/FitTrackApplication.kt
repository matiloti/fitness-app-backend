package com.fittrack

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FitTrackApplication

fun main(args: Array<String>) {
    runApplication<FitTrackApplication>(*args)
}
