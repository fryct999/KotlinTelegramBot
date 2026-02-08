import fryct999.Question
import fryct999.Word
import fryct999.asConsoleString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class QuestionExtensionTest {
    @Test
    fun `asConsoleString with 4 variants should format correctly`() {
        val question = Question(
            variants = listOf(
                Word("hello", "привет"),
                Word("world", "мир"),
                Word("cat", "кот"),
                Word("dog", "собака")
            ),
            correctAnswer = Word("hello", "привет")
        )

        val expected = """
            hello
            1 - привет
            2 - мир
            3 - кот
            4 - собака
             ---------- 
            0 - Меню""".trimIndent()

        assertEquals(expected, question.asConsoleString())
    }

    @Test
    fun `asConsoleString with different order should maintain indices`() {
        val variants = listOf(
            Word("hello", "привет"),
            Word("world", "мир"),
            Word("cat", "кот"),
            Word("dog", "собака")
        )

        val question = Question(
            variants = variants.shuffled(),
            correctAnswer = Word("hello", "привет")
        )

        val result = question.asConsoleString()
        val lines = result.lines()

        for (i in 1..variants.size) {
            assertTrue(lines[i].startsWith("$i - "))
        }
    }

    @Test
    fun `asConsoleString with empty list should show only original and menu`() {
        val question = Question(
            variants = emptyList(),
            correctAnswer = Word("test", "тест")
        )

        val expected = "test\n" + "\n ---------- \n0 - Меню"

        assertEquals(expected, question.asConsoleString())
    }

    @Test
    fun `asConsoleString with 10 variants should show all indices`() {
        val variants = (1..10).map {
            Word("word$it", "перевод$it")
        }

        val question = Question(
            variants = variants,
            correctAnswer = Word("original", "оригинал")
        )

        val result = question.asConsoleString()

        (1..10).forEach { index ->
            assert(result.contains("$index - перевод$index"))
        }

        assert(!result.contains("11 - "))
    }

    @Test
    fun `asConsoleString with 200 variants should display 10 options`() {
        val variants = (1..200).map {
            Word("word$it", "translate$it")
        }

        val question = Question(
            variants = variants,
            correctAnswer = Word("correct", "верный")
        )

        val result = question.asConsoleString()
        val expected = """
            correct
            1 - translate1
            2 - translate2
            3 - translate3
            4 - translate4
            5 - translate5
            6 - translate6
            7 - translate7
            8 - translate8
            9 - translate9
            10 - translate10
             ---------- 
            0 - Меню""".trimIndent()

        assertEquals(expected, result)
    }

    @Test
    fun `asConsoleString with special characters should handle them correctly`() {
        val question = Question(
            variants = listOf(
                Word("test", "перевод (со скобками)"),
                Word("another", "перевод, с запятыми"),
                Word("third", "перевод|с|палочками"),
                Word("fourth", "перевод.с.точками"),
                Word("fifth", "перевод\\с\\обратными\\слешами"),
                Word("sixth", "перевод\"в\"кавычках"),
            ),
            correctAnswer = Word("test", "перевод")
        )

        val result = question.asConsoleString()
        assert(result.contains("1 - перевод (со скобками)"))
        assert(result.contains("2 - перевод, с запятыми"))
        assert(result.contains("3 - перевод|с|палочками"))
        assert(result.contains("4 - перевод.с.точками"))
    }

    @Test
    fun `asConsoleString with whitespace words should display them`() {
        val question = Question(
            variants = listOf(
                Word("   ", "пробелы"),
                Word("\t\t", "табуляция"),
                Word("\n\n", "переносы"),
                Word("  test  ", "  тест  ")
            ),
            correctAnswer = Word("   ", "пробелы")
        )

        val result = question.asConsoleString()
        assert(result.contains("   "))
        assert(result.contains("1 - пробелы"))
        assert(result.contains("2 - табуляция"))
        assert(result.contains("3 - переносы"))
        assert(result.contains("4 -   тест  "))
    }
}