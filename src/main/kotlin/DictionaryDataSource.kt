package fryct999

import kotlinx.serialization.Serializable
import java.io.File
import java.sql.DriverManager

@Serializable
data class Word(
    val original: String,
    val translate: String,
    var correctAnswersCount: Int = 0,
    val imagePath: String,
)

interface IUserDictionary {
    fun getNumOfLearnedWords(): Int
    fun getSize(): Int
    fun getLearnedWords(): List<Word>
    fun getUnlearnedWords(): List<Word>
    fun setCorrectAnswersCount(word: String, correctAnswersCount: Int)
    fun resetUserProgress()
    fun addNewWord(fileName: String)
}

class DictionaryWithDatabase(
    private val dbFileName: String,
    private val tbName: String,
    private val learnedAnswerCount: Int = 3,
) : IUserDictionary {
    init {
        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val statement = connection.createStatement()
                statement.executeUpdate(
                    """
                      CREATE TABLE IF NOT EXISTS "$tbName" (
                          "id" integer PRIMARY KEY,
                          "text" varchar,
                          "translate" varchar,
                          "imagePath" varchar
                      );
              """.trimIndent()
                )

                val rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM \"$tbName\""
                )
                rs.next()
                val count = rs.getInt(1)

                if (count == 0) {
                    statement.executeUpdate(
                        "INSERT INTO \"$tbName\" SELECT * FROM \"words\""
                    )

                    statement.executeUpdate(
                        "ALTER TABLE \"$tbName\" ADD COLUMN \"correctAnswersCount\" integer DEFAULT 0"
                    )
                }
            }
    }

    override fun getNumOfLearnedWords(): Int {
        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val statement = connection.createStatement()
                val rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM \"$tbName\" WHERE correctAnswersCount >= $learnedAnswerCount"
                )
                rs.next()
                return rs.getInt(1)
            }
    }

    override fun getSize(): Int {
        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val statement = connection.createStatement()
                val rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM \"$tbName\""
                )
                rs.next()
                return rs.getInt(1)
            }
    }

    override fun getLearnedWords(): List<Word> {
        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val statement = connection.createStatement()
                val rs = statement.executeQuery(
                    "SELECT * FROM \"$tbName\" WHERE correctAnswersCount >= $learnedAnswerCount"
                )

                val list = mutableListOf<Word>()
                while (rs.next()) {
                    list.add(Word(
                        original = rs.getString("text"),
                        translate = rs.getString("translate"),
                        imagePath = rs.getString("imagePath"),
                        correctAnswersCount = rs.getInt("correctAnswersCount")
                    ))
                }

                return list
            }
    }

    override fun getUnlearnedWords(): List<Word> {
        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val statement = connection.createStatement()
                val rs = statement.executeQuery(
                    "SELECT * FROM \"$tbName\" WHERE correctAnswersCount < $learnedAnswerCount"
                )

                val list = mutableListOf<Word>()
                while (rs.next()) {
                    list.add(Word(
                        original = rs.getString("text"),
                        translate = rs.getString("translate"),
                        imagePath = rs.getString("imagePath"),
                        correctAnswersCount = rs.getInt("correctAnswersCount")
                    ))
                }

                return list
            }
    }

    override fun setCorrectAnswersCount(word: String, correctAnswersCount: Int) {
        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val statement = connection.createStatement()
                statement.executeUpdate(
                    "UPDATE \"$tbName\" SET correctAnswersCount = $correctAnswersCount WHERE text = \"$word\""
                )
            }
    }

    override fun resetUserProgress() {
        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val statement = connection.createStatement()
                statement.executeUpdate(
                    "UPDATE \"$tbName\" SET correctAnswersCount = 0"
                )
            }
    }

    override fun addNewWord(fileName: String) {
        val wordsFile = File(fileName)
        if (!wordsFile.exists())
            return

        updateDictionary(wordsFile)
    }

    private fun updateDictionary(fileName: File) {
        try {
            DriverManager.getConnection("jdbc:sqlite:$dbFileName")
                .use { connection ->
                    val statement = connection.createStatement()

                    val wordsLines = fileName.readLines()
                    for (wordLine in wordsLines) {
                        val line = wordLine.split("|")
                        if (line.size != 4) {
                            println("Не корректная строка.")
                            continue
                        }

                        val img = File("imgWords/${line[3]}")
                        val original = line[0]
                        val translate = line[1]
                        val imagePath = if (img.exists() && img.isFile) line[3] else ""

                        statement.executeUpdate("insert into words values(null, '$original', '$translate', '$imagePath')")
                    }
                }
        } catch (e: IndexOutOfBoundsException) {
            throw IllegalStateException("Некорректный файл")
        }
    }
}

class DictionaryWithFile(
    private val wordsFile: String,
    private val learnedAnswerCount: Int = 3
) : IUserDictionary {
    private val dictionary = try {
        readWordsFile(wordsFile)
    } catch (e: Exception) {
        throw IllegalArgumentException("Некорректный файл")
    }

    override fun getNumOfLearnedWords(): Int {
        val learningWord = dictionary.filter { it.correctAnswersCount >= learnedAnswerCount }
        return learningWord.size
    }

    override fun getSize(): Int {
        return dictionary.size
    }

    override fun getLearnedWords(): List<Word> {
        val learningWord = dictionary.filter { it.correctAnswersCount >= learnedAnswerCount }
        return learningWord
    }

    override fun getUnlearnedWords(): List<Word> {
        val unLearningWord = dictionary.filter { it.correctAnswersCount < learnedAnswerCount }
        return unLearningWord
    }

    override fun setCorrectAnswersCount(word: String, correctAnswersCount: Int) {
        dictionary.find { it.original == word }?.correctAnswersCount = correctAnswersCount
        saveDictionary()
    }

    override fun addNewWord(fileName: String) {
        val newWords = readWordsFile(fileName)
        if (newWords.isNotEmpty()) {
            dictionary.addAll(newWords)
            saveDictionary()
        }
    }

    override fun resetUserProgress() {
        dictionary.forEach { it.correctAnswersCount = 0 }
        saveDictionary()
    }

    private fun readWordsFile(wordsFileName: String): MutableList<Word> {
        val wordsFile = File(wordsFileName)
        val words = mutableListOf<Word>()
        val wordsLines = wordsFile.readLines()
        for (wordLine in wordsLines) {
            val line = wordLine.split("|")
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

            words.add(word)
        }

        return words
    }

    private fun saveDictionary() {
        val file = File(wordsFile)
        file.writeText(dictionary.joinToString(separator = "") { "${it.original}|${it.translate}|${it.correctAnswersCount}|${it.imagePath}\n" })
    }
}