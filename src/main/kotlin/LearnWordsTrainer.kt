package fryct999

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Word(
    val original: String,
    val translate: String,
    var correctAnswersCount: Int = 0,
    val imagePath: String,
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

data class Image(
    val word: String,
    var fileId: String,
)

class LearnWordsTrainer(
    private val fileName: String = "words.txt",
    private val learnedAnswerCount: Int = 3,
    private val countOfQuestionWords: Int = 4
) {
    var question: Question? = null
        private set

    private val imageId = createImageIdList()
    private val dictionary = readWordsFile(fileName)

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
                saveDictionary()
                true
            } else false
        } ?: false
    }

    private fun createImageIdList(): MutableList<Image> {
        val imageIdFile = File("imageId.txt")
        val list = mutableListOf<Image>()

        if (imageIdFile.createNewFile()) return list

        val imageIdLines = imageIdFile.readLines()
        if (imageIdLines.isNotEmpty()) {
            for (imageIdLine in imageIdLines) {
                val line = imageIdLine.split("|")
                val word = line.getOrNull(0)
                val id = line.getOrNull(1)

                if (id == null || word == null) continue
                list.add(
                    Image(
                        word = word,
                        fileId = id,
                    )
                )
            }
        }

        return list
    }

    private fun readWordsFile(fileName: String): MutableList<Word> {
        try {
            val dictionaryFile = File(fileName)
            if (!dictionaryFile.exists()) {
                File("words.txt").copyTo(dictionaryFile)
            }

            val dictionary = mutableListOf<Word>()
            val dictionaryLines = dictionaryFile.readLines()
            for (dictionaryLine in dictionaryLines) {
                val line = dictionaryLine.split("|")
                if (line.size != 4) {
                    println("Не корректная строка.")
                    continue
                }

                val img = File("imgWords/${line[3]}")
                val word = Word(
                    original = line[0],
                    translate = line[1],
                    correctAnswersCount = line.getOrNull(2)?.toIntOrNull() ?: 0,
                    imagePath = if (img.exists() && img.isFile) line[3] else "",
                )

                dictionary.add(word)
            }

            return dictionary
        } catch (e: IndexOutOfBoundsException) {
            throw IllegalStateException("Некорректный файл")
        }
    }

    private fun saveDictionary() {
        val dictionaryFile = File(fileName)
        dictionaryFile.writeText(dictionary.joinToString(separator = "") { "${it.original}|${it.translate}|${it.correctAnswersCount}|${it.imagePath}\n" })
    }

    fun resetProgress() {
        dictionary.forEach { it.correctAnswersCount = 0 }
        saveDictionary()
    }

    fun addNewWords(fileName: String) {
        val newWords = readWordsFile(fileName)
        if (newWords.isNotEmpty()) {
            dictionary.addAll(newWords)
            saveDictionary()
        }
    }

    fun getImageFileId(word: String): String? {
        val image = imageId.find { it.word == word }
        return image?.fileId
    }

    fun addImgFileId(word: Word, fileId: String) {
        val image = imageId.find { it.word == word.original }
        if (image != null) {
            image.fileId = fileId
        } else {
            imageId.add(
                Image(
                    word = word.original,
                    fileId = fileId,
                )
            )
        }

        saveImageIdFile()
    }

    private fun saveImageIdFile() {
        val imageIdFile = File("imageId.txt")
        imageIdFile.writeText(imageId.joinToString(separator = "") { "${it.word}|${it.fileId}\n" })
    }
}