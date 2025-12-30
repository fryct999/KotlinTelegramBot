package fryct999

import java.io.File

data class Word(
    val original: String,
    val translate: String,
    var correctAnswersCount: Int = 0,
)

fun main() {
    val dictionary = loadDictionary()

    while (true) {
        println("Меню:\n1 – Учить слова\n2 – Статистика\n0 – Выход\n Введите номер пункта меню:")
        val userAnswer = readln()

        when (userAnswer) {
            "0" -> return
            "1" -> println("Выбран пункт \"Учить слова\"")
            "2" -> println("Выбран пункт \"Статистика\"")
            else -> println("Выбран не правильный пункт. Введите число 1, 2 или 0.")
        }
    }
}

fun loadDictionary(): MutableList<Word> {
    val dictionaryFile = File("words.txt")
    val dictionary = mutableListOf<Word>()

    val dictionaryLines = dictionaryFile.readLines()
    dictionaryLines.forEach {
        val line = it.split("|")
        val word = Word(original = line[0], translate = line[1], correctAnswersCount = line.getOrNull(2)?.toIntOrNull() ?: 0)
        dictionary.add(word)
    }

    return dictionary
}