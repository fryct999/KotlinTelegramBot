package fryct999

import java.io.File

data class Word(
    val original: String,
    val translate: String,
    var correctAnswersCount: Int = 0,
)

data class Statistics(
    val totalCount: Int,
    val learnedCount: Int,
    val learnedPercent: Int,
)

data class Question(
    val variants: List<Word>,
    val correctAnswer: Word,
)

class LearnWordsTrainer(private val learnedAnswerCount: Int = 3, private val countOfQuestionWords: Int = 4) {
    private var question: Question? = null
    private val dictionary = loadDictionary()

    fun getStatistics(): Statistics {
        val totalCount = dictionary.size
        val learnedCount = dictionary.filter { it.correctAnswersCount >= learnedAnswerCount }.size
        val learnedPercent = if (totalCount != 0) ((learnedCount.toDouble() / totalCount) * 100).toInt() else 100

        return Statistics(totalCount, learnedCount, learnedPercent)
    }

    fun getNextQuestion(): Question? {
        val notLearnedList = dictionary.filter { it.correctAnswersCount < learnedAnswerCount }
        if (notLearnedList.isEmpty()) return null

        val questionWords = if (notLearnedList.size < countOfQuestionWords) {
            val learnedList = dictionary.filter { it.correctAnswersCount >= learnedAnswerCount }.shuffled()
            notLearnedList.shuffled().take(countOfQuestionWords) +
                    learnedList.take(countOfQuestionWords - notLearnedList.size)
        } else {
            notLearnedList.shuffled().take(countOfQuestionWords)
        }.shuffled()

        val correctAnswer = questionWords.random()

        question = Question(
            variants = questionWords,
            correctAnswer = correctAnswer,
        )

        return question
    }

    fun checkAnswer(userAnswerIndex: Int?): Boolean {
        return question?.let {
            val correctAnswerId = it.variants.indexOf(it.correctAnswer)
            if (correctAnswerId == userAnswerIndex) {
                it.correctAnswer.correctAnswersCount++
                saveDictionary(dictionary)
                true
            } else false
        } ?: false
    }

    private fun loadDictionary(): MutableList<Word> {
        val dictionaryFile = File("words.txt")
        val dictionary = mutableListOf<Word>()

        val dictionaryLines = dictionaryFile.readLines()
        dictionaryLines.forEach {
            val line = it.split("|")
            val word =
                Word(
                    original = line[0],
                    translate = line[1],
                    correctAnswersCount = line.getOrNull(2)?.toIntOrNull() ?: 0
                )
            dictionary.add(word)
        }

        return dictionary
    }

    private fun saveDictionary(dictionary: MutableList<Word>) {
        val dictionaryFile = File("words.txt")
        dictionaryFile.writeText(dictionary.joinToString(separator = "") { "${it.original}|${it.translate}|${it.correctAnswersCount}\n" })
    }
}