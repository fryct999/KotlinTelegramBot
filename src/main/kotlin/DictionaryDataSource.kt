package fryct999

import kotlinx.serialization.Serializable
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.util.logging.Logger

const val MAX_LENGTH = 12

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
    private val logger = Logger.getLogger(DatabaseUserDictionary::class.java.name)

    init {
        initTables()
        val wordCount = getWordCount()

        if (wordCount == 0)
            addNewWord("words.txt")

        addUserIfNotExists()
    }

    override fun getNumOfLearnedWords(): Int {
        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val userId = getUserId(connection)
                val sql = "SELECT COUNT(*) FROM 'user_answers' WHERE correct_answer_count >= ? AND user_id = ?"
                val preparedStatement = connection.prepareStatement(sql)
                preparedStatement.setInt(1, learnedAnswerCount)
                preparedStatement.setLong(2, userId)
                val result = preparedStatement.executeQuery()
                result.next()
                return result.getInt(1)
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
        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val userId = getUserId(connection)
                val sql = """
                        SELECT words.*, user_answers.correct_answer_count FROM 'words'
                        JOIN 'user_answers' ON words.id = user_answers.word_id
                        WHERE user_answers.user_id = ?
                        AND user_answers.correct_answer_count >= ?
                    """.trimIndent()
                val preparedStatement = connection.prepareStatement(sql)
                preparedStatement.setLong(1, userId)
                preparedStatement.setInt(2, learnedAnswerCount)
                val resultStatement = preparedStatement.executeQuery()

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
        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val userId = getUserId(connection)
                val sql = """
                        SELECT words.*, user_answers.correct_answer_count FROM 'words'
                        JOIN 'user_answers' ON words.id = user_answers.word_id
                        WHERE user_answers.user_id = ?
                        AND user_answers.correct_answer_count < ?
                    """.trimIndent()
                val preparedStatement = connection.prepareStatement(sql)
                preparedStatement.setLong(1, userId)
                preparedStatement.setInt(2, learnedAnswerCount)
                val resultStatement = preparedStatement.executeQuery()

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
        if (correctAnswersCount !in 0..learnedAnswerCount) {
            logSuspiciousActivity("Кол-во верных ответов за рамками диапозона от 0 до $learnedAnswerCount. Получено: $correctAnswersCount.")
            throw IllegalArgumentException("correctAnswersCount must be between 0 and $learnedAnswerCount")
        }

        val trim = word.trim()

        validateInput(trim)

        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val userId = getUserId(connection)
                val sqlWord = "SELECT id FROM 'words' WHERE text = ?"
                val preparedStatementWord = connection.prepareStatement(sqlWord)
                preparedStatementWord.setString(1, trim)
                val wordIdStatement = preparedStatementWord.executeQuery()

                wordIdStatement.next()
                val wordId = wordIdStatement.getInt(1)

                val sqlWordId =
                    "UPDATE 'user_answers' SET correct_answer_count = ?, update_at = CURRENT_TIMESTAMP WHERE user_id = ? AND word_id = ?"
                val preparedStatementWordId = connection.prepareStatement(sqlWordId)
                preparedStatementWordId.setInt(1, correctAnswersCount)
                preparedStatementWordId.setLong(2, userId)
                preparedStatementWordId.setInt(3, wordId)
                preparedStatementWordId.executeUpdate()
            }
    }

    override fun resetUserProgress() {
        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val userId = getUserId(connection)
                val sql =
                    "UPDATE 'user_answers' SET correct_answer_count = ?, update_at = CURRENT_TIMESTAMP WHERE user_id = ?"
                val preparedStatement = connection.prepareStatement(sql)
                preparedStatement.setInt(1, 0)
                preparedStatement.setLong(2, userId)
                preparedStatement.executeUpdate()
            }
    }

    override fun addNewWord(fileName: String) {
        val trim = fileName.trim()
        if (trim.isEmpty()) {
            logSuspiciousActivity("Пустое имя файла")
            throw IllegalArgumentException("file name cannot be empty")
        }

        validateInput(trim)

        if (!trim.endsWith(".txt")) {
            logSuspiciousActivity("Не корректный формат файла: $trim")
            throw IllegalArgumentException("wrong file format")
        }

        val wordsFile = File(trim)
        if (!wordsFile.exists()) {
            logSuspiciousActivity("Файла не существует: $trim")
            throw IllegalArgumentException("file not exists")
        }

        updateDictionary(wordsFile)
    }

    private fun updateDictionary(fileName: File) {
        try {
            DriverManager.getConnection("jdbc:sqlite:$dbFileName")
                .use { connection ->
                    val wordsLines = fileName.readLines()

                    val sqlAddWord = "INSERT INTO words VALUES(null, ?, ?, ?)"
                    val preparedStatementAddWord = connection.prepareStatement(
                        sqlAddWord,
                        Statement.RETURN_GENERATED_KEYS
                    )
                    val sqlUpdateToUsers =
                        "INSERT INTO user_answers (user_id, word_id, correct_answer_count) SELECT id, ?, 0 FROM users"
                    val preparedStatementUpdateToUsers = connection.prepareStatement(sqlUpdateToUsers)

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

                        preparedStatementAddWord.setString(1, original)
                        preparedStatementAddWord.setString(2, translate)
                        preparedStatementAddWord.setString(3, imagePath)
                        preparedStatementAddWord.executeUpdate()

                        val generatedKeys = preparedStatementAddWord.generatedKeys
                        generatedKeys.next()
                        val newWordId = generatedKeys.getInt(1)

                        preparedStatementUpdateToUsers.setInt(1, newWordId)
                        preparedStatementUpdateToUsers.executeUpdate()
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
                val sqlUserExist = "SELECT id FROM 'users' WHERE chat_id = ?"
                val preparedStatementUserExist = connection.prepareStatement(sqlUserExist)
                preparedStatementUserExist.setLong(1, chatId)
                val resultUserExistStatement = preparedStatementUserExist.executeQuery()

                if (!resultUserExistStatement.next()) {
                    val sqlUserInsert =
                        "INSERT INTO 'users' (username, chat_id, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)"
                    val preparedStatementUserInsert = connection.prepareStatement(sqlUserInsert)
                    preparedStatementUserInsert.setString(1, username)
                    preparedStatementUserInsert.setLong(2, chatId)
                    preparedStatementUserInsert.executeUpdate()

                    val sqlUserIdStatement = "SELECT id FROM 'users' WHERE chat_id = ?"
                    val preparedStatementUserId = connection.prepareStatement(sqlUserIdStatement)
                    preparedStatementUserId.setLong(1, chatId)
                    val resultUserIdStatement = preparedStatementUserId.executeQuery()

                    resultUserIdStatement.next()
                    val userId = resultUserIdStatement.getInt(1)

                    val sqlUsersAnswerInsert =
                        "INSERT INTO 'user_answers' (user_id, word_id, correct_answer_count) SELECT ?, id, ? FROM 'words'"
                    val preparedStatementUsersAnswerInsert = connection.prepareStatement(sqlUsersAnswerInsert)
                    preparedStatementUsersAnswerInsert.setInt(1, userId)
                    preparedStatementUsersAnswerInsert.setInt(2, 0)
                    preparedStatementUsersAnswerInsert.executeUpdate()
                }
            }
    }

    private fun getUserId(connection: Connection): Long {
        val sql = "SELECT id FROM 'users' WHERE chat_id = ?"
        val preparedStatement = connection.prepareStatement(sql)
        preparedStatement.setLong(1, chatId)
        val result = preparedStatement.executeQuery()
        result.next()
        return result.getLong("id")
    }

    private fun logSuspiciousActivity(message: String) {
        DriverManager.getConnection("jdbc:sqlite:$dbFileName")
            .use { connection ->
                val userId = getUserId(connection)
                logger.warning("Пользователь: $userId. $message")
            }
    }

    private fun validateInput(input: String) {
        val regex = Regex("union|select|drop|delete|--|/\\*", RegexOption.IGNORE_CASE)

        require(input.length <= MAX_LENGTH) {
            logSuspiciousActivity("Строка превышает максимум: $input")
            "Строка превышает максимум: $input"
        }
        require(!regex.containsMatchIn(input)) {
            logSuspiciousActivity("Не корретные данные: $input")
            "Не корретные данные: $input"
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