package fryct999

import java.io.File

fun main() {
    val dictionaryFile = File("words.txt")

    val words = dictionaryFile.readLines()
    words.forEach {
        println(it)
    }
}