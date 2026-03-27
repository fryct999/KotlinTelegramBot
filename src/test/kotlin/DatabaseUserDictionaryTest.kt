import fryct999.DatabaseUserDictionary
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DatabaseUserDictionaryTest {
    private lateinit var databaseUserDictionary: DatabaseUserDictionary

    @BeforeTest
    fun setUp() {
        databaseUserDictionary = DatabaseUserDictionary(
            dbFileName = "TestDB.db",
            chatId = 0,
            username = "TestUser",
        )
    }

    @Test
    fun setCorrectAnswersNegativeCount() {
        assertFailsWith<IllegalArgumentException> {
            databaseUserDictionary.setCorrectAnswersCount("testWord", -1)
        }
    }

    @Test
    fun setCorrectAnswersExcessCount() {
        assertFailsWith<IllegalArgumentException> {
            databaseUserDictionary.setCorrectAnswersCount("testWord", 10)
        }
    }

    @Test
    fun setCorrectAnswersCorrectZeroCount() {
        databaseUserDictionary.setCorrectAnswersCount("word", 0)
    }

    @Test
    fun setCorrectAnswersCorrectCount() {
        databaseUserDictionary.setCorrectAnswersCount("word", 1)
    }

    @Test
    fun setCorrectAnswersCountSqlInjection() {
        assertFailsWith<IllegalArgumentException> { databaseUserDictionary.setCorrectAnswersCount("UNION SELECT * FROM users", 0) }
    }

    @Test
    fun addNewWordEmpty() {
        assertFailsWith<IllegalArgumentException> {
            databaseUserDictionary.addNewWord("")
        }
    }

    @Test
    fun addNewWordWrongType() {
        assertFailsWith<IllegalArgumentException> {
            databaseUserDictionary.addNewWord("Test2.exe")
        }
    }

    @Test
    fun addNewWordNotExist() {
        assertFailsWith<IllegalArgumentException> {
            databaseUserDictionary.addNewWord("Test2.txt")
        }
    }

    @Test
    fun addNewWordCorrectFile() {
        databaseUserDictionary.addNewWord("words.txt")
    }

    @Test
    fun addNewWordSqlInjection() {
        assertFailsWith<IllegalArgumentException> {
            databaseUserDictionary.addNewWord("'; DROP TABLE words; --")
        }
    }
}