package fryct999

fun Question.asConsoleString(): String {
    val variants = this.variants
        .mapIndexed { index: Int, word: Word -> "${index + 1} - ${word.translate}" }
        .joinToString(separator = "\n")

    return this.correctAnswer.original + "\n" + variants + "\n ---------- \n0 - Меню"
}

fun main() {
    val trainer = try {
        LearnWordsTrainer(learnedAnswerCount = 3, countOfQuestionWords = 4)
    } catch (e: Exception) {
        println("Невозможно загрузить словарь.")
        return
    }

    while (true) {
        println("Меню:\n1 – Учить слова\n2 – Статистика\n0 – Выход\n Введите номер пункта меню:")
        val userAnswer = readln()

        when (userAnswer) {
            "0" -> return
            "1" -> {
                while (true) {
                    val question = trainer.getNextQuestion()
                    if (question == null) {
                        println("Все слова в словаре выучены.")
                        break
                    }

                    println(question.asConsoleString())

                    val userAnswerInput = readln().toIntOrNull() ?: continue
                    if (userAnswerInput == 0) break

                    if (trainer.checkAnswer(userAnswerInput.minus(1)))
                        println("Правильно!")
                    else
                        println("Неправильно! ${question.correctAnswer.original} – это ${question.correctAnswer.translate}")
                }
            }

            "2" -> {
                val statistics = trainer.getStatistics()
                println("Выучено ${statistics.learnedCount} из ${statistics.totalCount} слов | ${statistics.learnedPercent}%\n")
            }

            else -> println("Выбран не правильный пункт. Введите число 1, 2 или 0.")
        }
    }
}