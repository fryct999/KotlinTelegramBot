package fryct999

import java.io.File

data class Word(
    val original: String,
    val translate: String,
    var correctAnswersCount: Int = 0,
)

fun main() {
    val dictionaryFile = File("words.txt")
    val dictionary = mutableListOf<Word>()

    val dictionaryLines = dictionaryFile.readLines()
    dictionaryLines.forEach {
        val line = it.split("|")
        val word = Word(original = line[0], translate = line[1], correctAnswersCount = line.getOrNull(2)?.toIntOrNull() ?: 0)
        dictionary.add(word)
    }

    dictionary.forEach { println(it) }
}