package fryct999

import java.io.File

const val DB_NAME = "data.db"
const val MIN_CORRECT_ANSWER = 3

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
    private val wordsFileName: String = "words.txt",
    private val countOfQuestionWords: Int = 4
) {
    init {
        val wordsFile = File(wordsFileName)
        if (!wordsFile.exists()) {
            File("words.txt").copyTo(wordsFile)
        }
    }

    val userDictionary = DictionaryWithDatabase(DB_NAME, wordsFileName.removeSuffix(".txt"), MIN_CORRECT_ANSWER)
    //val userDictionary = DictionaryWithFile(wordsFileName, MIN_CORRECT_ANSWER)
    var question: Question? = null
        private set

    private val imageId = createImageIdList()

    fun getStatistics(): Statistics {
        val totalCount = userDictionary.getSize()
        val learnedCount = userDictionary.getNumOfLearnedWords()
        val learnedPercent = if (totalCount != 0) ((learnedCount.toDouble() / totalCount) * 100).toInt() else 100

        return Statistics(totalCount, learnedCount, learnedPercent)
    }

    fun getNextQuestion(): Question? {
        val notLearnedList = userDictionary.getUnlearnedWords()
        if (notLearnedList.isEmpty()) return null

        val questionWords = if (notLearnedList.size < countOfQuestionWords) {
            val learnedList = userDictionary.getLearnedWords()
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
                userDictionary.setCorrectAnswersCount(it.correctAnswer.original, it.correctAnswer.correctAnswersCount)
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

    fun addNewWord(fileName: String) {
        val file = File(fileName)
        if (!file.exists())
            return

        userDictionary.addNewWord(fileName)
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

    fun resetProgress() {
        userDictionary.resetUserProgress()
    }

    private fun saveImageIdFile() {
        val imageIdFile = File("imageId.txt")
        imageIdFile.writeText(imageId.joinToString(separator = "") { "${it.word}|${it.fileId}\n" })
    }
}