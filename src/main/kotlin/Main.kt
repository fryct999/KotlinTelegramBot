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
            "1" -> {
                var notLearnedList = dictionary.filter { it.correctAnswersCount < 3 }

                if (notLearnedList.isEmpty()) {
                    println("Все слова в словаре выучены.")
                    continue
                }

                while (notLearnedList.isNotEmpty()) {
                    val questionWords = notLearnedList.shuffled().take(4)
                    val correctAnswer = questionWords.first()
                    var correctAnswerId = 0

                    println("\n ${correctAnswer.original}:")
                    questionWords.shuffled().forEachIndexed { id, it ->
                        val index = id + 1
                        println("$index: ${it.translate}")
                        correctAnswerId = if (it.original == correctAnswer.original) index else correctAnswerId
                    }
                    println("---------- \n0 - Меню")

                    val userAnswerInput = readln().toIntOrNull()

                    when (userAnswerInput) {
                        correctAnswerId -> {
                            println("Правильно!")
                            correctAnswer.correctAnswersCount++
                            saveDictionary(dictionary)
                        }

                        0 -> break
                        else -> println("Неправильно! ${correctAnswer.original} – это ${correctAnswer.translate}")
                    }

                    notLearnedList = dictionary.filter { it.correctAnswersCount < 3 }
                }
            }

            "2" -> {
                val totalCount = dictionary.size
                val learnedCount = dictionary.filter { it.correctAnswersCount >= 3 }.size
                val learnedPercent = ((learnedCount.toDouble() / totalCount) * 100).toInt()

                println("Выучено $learnedCount из $totalCount слов | $learnedPercent%\n")
            }

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
        val word =
            Word(original = line[0], translate = line[1], correctAnswersCount = line.getOrNull(2)?.toIntOrNull() ?: 0)
        dictionary.add(word)
    }

    return dictionary
}

fun saveDictionary(dictionary: MutableList<Word>) {
    val dictionaryFile = File("words.txt")
    dictionaryFile.writeText(dictionary.joinToString(separator = "") { "${it.original}|${it.translate}|${it.correctAnswersCount}\n" })
}