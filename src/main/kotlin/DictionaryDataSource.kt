package fryct999

import kotlinx.serialization.Serializable
import java.io.File
import java.sql.Connection
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

class DatabaseUserDictionary(
    private val dbFileName: String,
    private val chatId: Long,
    private val username: String,
    private val learnedAnswerCount: Int = 3,
) : IUserDictionary {
    init {
        initTables()
        val wordCount = getWordCount()

        if (wordCount == 0)
            addNewWord("words.txt")

        addUserIfNotExists()
    }

    override fun getNumOfLearnedWords(): Int {
        val userId = getUserId()

        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val statement = connection.createStatement()
                val resultStatement = statement.executeQuery(
                    "SELECT COUNT(*) FROM 'user_answers' WHERE correct_answer_count >= $learnedAnswerCount AND user_id = $userId"
                )
                resultStatement.next()
                return resultStatement.getInt(1)
            }
    }

    override fun getSize(): Int {
        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val statement = connection.createStatement()
                val resultStatement = statement.executeQuery(
                    "SELECT COUNT(*) FROM 'words'"
                )
                resultStatement.next()
                return resultStatement.getInt(1)
            }
    }

    override fun getLearnedWords(): List<Word> {
        val userId = getUserId()

        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val statement = connection.createStatement()
                val resultStatement = statement.executeQuery(
                    """
                        SELECT words.*, user_answers.correct_answer_count FROM 'words'
                        JOIN 'user_answers' ON words.id = user_answers.word_id
                        WHERE user_answers.user_id = $userId
                        AND user_answers.correct_answer_count >= $learnedAnswerCount
                    """.trimIndent()
                )

                val list = mutableListOf<Word>()
                while (resultStatement.next()) {
                    list.add(
                        Word(
                            original = resultStatement.getString("text"),
                            translate = resultStatement.getString("translate"),
                            imagePath = resultStatement.getString("image_path"),
                            correctAnswersCount = resultStatement.getInt("correct_answer_count")
                        )
                    )
                }

                return list
            }
    }

    override fun getUnlearnedWords(): List<Word> {
        val userId = getUserId()

        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val statement = connection.createStatement()
                val resultStatement = statement.executeQuery(
                    """
                        SELECT words.*, user_answers.correct_answer_count FROM 'words'
                        JOIN 'user_answers' ON words.id = user_answers.word_id
                        WHERE user_answers.user_id = $userId
                        AND user_answers.correct_answer_count < $learnedAnswerCount
                    """.trimIndent()
                )

                val list = mutableListOf<Word>()
                while (resultStatement.next()) {
                    list.add(
                        Word(
                            original = resultStatement.getString("text"),
                            translate = resultStatement.getString("translate"),
                            imagePath = resultStatement.getString("image_path"),
                            correctAnswersCount = resultStatement.getInt("correct_answer_count")
                        )
                    )
                }

                return list
            }
    }

    override fun setCorrectAnswersCount(word: String, correctAnswersCount: Int) {
        val userId = getUserId()

        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val statement = connection.createStatement()
                val wordIdStatement = statement.executeQuery(
                    "SELECT id FROM 'words' WHERE text = '$word'"
                )
                wordIdStatement.next()
                val wordId = wordIdStatement.getInt(1)

                statement.executeUpdate(
                    "UPDATE 'user_answers' SET correct_answer_count = $correctAnswersCount, update_at = CURRENT_TIMESTAMP WHERE user_id = $userId AND word_id = $wordId"
                )
            }
    }

    override fun resetUserProgress() {
        val userId = getUserId()

        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val statement = connection.createStatement()
                statement.executeUpdate(
                    "UPDATE 'user_answers' SET correct_answer_count = 0, update_at = CURRENT_TIMESTAMP WHERE user_id = $userId"
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

                        statement.executeUpdate("INSERT INTO words VALUES(null, '$original', '$translate', '$imagePath')")

                        val wordIdRs = statement.executeQuery("SELECT last_insert_rowid()")
                        wordIdRs.next()
                        val newWordId = wordIdRs.getInt(1)

                        statement.executeUpdate("INSERT INTO user_answers (user_id, word_id, correct_answer_count) SELECT id, $newWordId, 0 FROM users")
                    }
                }
        } catch (e: IndexOutOfBoundsException) {
            throw IllegalStateException("Некорректный файл")
        }
    }

    private fun initTables() {
        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val statement = connection.createStatement()
                statement.executeUpdate(
                    """
                      CREATE TABLE IF NOT EXISTS 'users' (
                          "id" integer PRIMARY KEY,
                          "username" varchar,
                          "created_at" timestamp,
                          "chat_id" integer
                      );
              """.trimIndent()
                )

                statement.executeUpdate(
                    """
                      CREATE TABLE IF NOT EXISTS 'words' (
                          "id" integer PRIMARY KEY,
                          "text" varchar,
                          "translate" varchar,
                          "image_path" varchar
                      );
              """.trimIndent()
                )

                statement.executeUpdate(
                    """
                      CREATE TABLE IF NOT EXISTS 'user_answers' (
                          "user_id" integer,
                          "word_id" integer,
                          "correct_answer_count" integer,
                          "update_at" timestamp,
                          FOREIGN KEY ("user_id") REFERENCES "users" ("id"),
                          FOREIGN KEY ("word_id") REFERENCES "words" ("id")
                      );
              """.trimIndent()
                )
            }
    }

    private fun getWordCount(): Int {
        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val statement = connection.createStatement()
                val wordCountStatement = statement.executeQuery("SELECT COUNT(*) FROM 'words'")
                wordCountStatement.next()

                return wordCountStatement.getInt(1)
            }
    }

    private fun addUserIfNotExists() {
        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val statement = connection.createStatement()

                val userExistStatement = statement.executeQuery(
                    "SELECT id FROM 'users' WHERE chat_id = $chatId"
                )

                if (!userExistStatement.next()) {
                    statement.executeUpdate(
                        "INSERT INTO 'users' (username, chat_id, created_at) VALUES ('$username', $chatId, CURRENT_TIMESTAMP)"
                    )

                    val userIdStatement = statement.executeQuery(
                        "SELECT id FROM 'users' WHERE chat_id = $chatId"
                    )
                    userIdStatement.next()
                    val userId = userIdStatement.getInt(1)

                    statement.executeUpdate(
                        "INSERT INTO 'user_answers' (user_id, word_id, correct_answer_count) SELECT $userId, id, 0 FROM 'words'"
                    )
                }
            }
    }

    private fun getUserId(): Long {
        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val statement = connection.createStatement()
                val userIdStatemen = statement.executeQuery(
                    "SELECT id FROM 'users' WHERE chat_id = $chatId"
                )
                userIdStatemen.next()
                return userIdStatemen.getLong("id")
            }
    }
}

class FileUserDictionary(
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