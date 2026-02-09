import fryct999.LearnWordsTrainer
import fryct999.Statistics
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LearnWordsTrainerTest {
    @Test
    fun `test statistics with 4 words of 7`() {
        val trainer = LearnWordsTrainer("src/test/4_words_of_7.txt")
        assertEquals(
            Statistics(totalCount = 7, learnedCount = 4, learnedPercent = 57),
            trainer.getStatistics()
        )
    }

    @Test
    fun `test getNextQuestion() with 1 unlearned word`() {
        val trainer = LearnWordsTrainer("src/test/1_unlearned_word.txt")
        assertEquals(
            Statistics(totalCount = 2, learnedCount = 1, learnedPercent = 50),
            trainer.getStatistics()
        )
    }

    @Test
    fun `test getNextQuestion() with 5 unlearned words`() {
        val trainer = LearnWordsTrainer("src/test/5_unlearned_word.txt")
        assertEquals(
            Statistics(totalCount = 5, learnedCount = 0, learnedPercent = 0),
            trainer.getStatistics()
        )
    }

    @Test
    fun `test getNextQuestion() with all words learned`() {
        val trainer = LearnWordsTrainer("src/test/7_words_of_7.txt")
        val question = trainer.getNextQuestion()
        assertEquals(null, question)
    }

    @Test
    fun `test checkAnswer() with true`() {
        val trainer = LearnWordsTrainer("src/test/4_words_of_7.txt")
        val question = trainer.getNextQuestion()
        val answer = trainer.checkAnswer(question?.variants?.indexOf(question.correctAnswer))
        assertEquals(true, answer)
    }

    @Test
    fun `test checkAnswer() with false`() {
        val trainer = LearnWordsTrainer("src/test/4_words_of_7.txt")
        val question = trainer.getNextQuestion()
        val correctAnswerIndex = question?.variants?.indexOf(question.correctAnswer)
        val answerIndex = if (correctAnswerIndex == 1) 2 else 1
        val answer = trainer.checkAnswer(answerIndex)
        assertEquals(false, answer)
    }

    @Test
    fun `test resetProgress() with 2 words in dictionary`() {
        val trainer = LearnWordsTrainer("src/test/1_words_of_2.txt")
        assertEquals(
            Statistics(totalCount = 2, learnedCount = 1, learnedPercent = 50),
            trainer.getStatistics()
        )

        trainer.resetProgress()
        assertEquals(
            Statistics(totalCount = 2, learnedCount = 0, learnedPercent = 0),
            trainer.getStatistics()
        )
    }

    @Test
    fun `test statistics with corrupted file`() {
        val trainer = LearnWordsTrainer("src/test/corrupted_file.txt")
        assertEquals(
            Statistics(totalCount = 5, learnedCount = 3, learnedPercent = 60),
            trainer.getStatistics()
        )
    }
}