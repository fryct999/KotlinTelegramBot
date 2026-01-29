package fryct999

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

const val MIN_CORRECT_ANSWER = 3
const val WORDS_PER_QUESTION = 4
const val STATISTIC_BUTTON_DATA = "statistics_click"
const val LEARN_WORD_BUTTON_DATA = "learn_words_click"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"

class TelegramBotService(private val token: String) {
    private val telegramBaseUrl = "https://api.telegram.org"
    private val urlSendMessage = "$telegramBaseUrl/bot$token/sendMessage"
    private val client = HttpClient.newBuilder().build()

    private val textRegex = "\"text\":\"(.+?)\"".toRegex()
    private val dataRegex = "\"data\":\"(.+?)\"".toRegex()
    private val updateIdRegex = "\"update_id\":(\\d+)".toRegex()
    private val chatIdRegex = "\"chat\":\\{\"id\":(\\d+)".toRegex()

    fun getUpdates(updateId: Int): String {
        val urlGetUpdates = "$telegramBaseUrl/bot$token/getUpdates?offset=$updateId"
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
        val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

        return response.body()
    }

    fun getUpdateValue(key: String, updates: String): String {
        when (key) {
            "text" -> return textRegex.find(updates)?.groups?.get(1)?.value ?: ""
            "data" -> return dataRegex.find(updates)?.groups?.get(1)?.value ?: ""
            "update_id" -> return updateIdRegex.find(updates)?.groups?.get(1)?.value ?: ""
            "chat_id" -> return chatIdRegex.find(updates)?.groups?.get(1)?.value ?: ""
        }

        return ""
    }

    fun sendMessage(chatId: Int, text: String) {
        if (text.isEmpty() || text.length >= 4096) return

        val encodedMessage = URLEncoder.encode(text, StandardCharsets.UTF_8)
        val urlSendMsg = "$urlSendMessage?chat_id=$chatId&text=$encodedMessage"
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlSendMsg)).build()
        client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    fun sendMenu(chatId: Int) {
        val sendMenuBody = """
            {
                "chat_id": $chatId,
                "text": "Основное меню",
                "reply_markup": {
                    "inline_keyboard": [
                        [
                            {
                                "text": "Изучить слова",
                                "callback_data": "learn_words_click"
                            },
                            {
                                "text": "Статистика",
                                "callback_data": "statistics_click"
                            }
                        ]
                    ]
                }
            }
        """.trimIndent()

        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlSendMessage))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(sendMenuBody))
            .build()

        client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    fun sendQuestion(chatId: Int, question: Question) {
        val buttons = question.variants.mapIndexed { id, it ->
            """
                {
                    "text": "${it.translate}",
                    "callback_data": "$CALLBACK_DATA_ANSWER_PREFIX$id"
                }
                """.trimIndent()
        }.joinToString(",")

        val sendQuestionBody = """
            {
                "chat_id": $chatId,
                "text": "${question.correctAnswer.original}",
                "reply_markup": {
                    "inline_keyboard": [ [ $buttons ] ]
                }
            }
        """.trimIndent()

        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlSendMessage))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(sendQuestionBody))
            .build()

        client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}

fun main(args: Array<String>) {
    val telegramBotService = TelegramBotService(token = args[0])
    val trainer = LearnWordsTrainer(MIN_CORRECT_ANSWER, WORDS_PER_QUESTION)
    var updateId = 0

    while (true) {
        Thread.sleep(2000)

        val updates = telegramBotService.getUpdates(updateId)
        println(updates)

        val updateIdString = telegramBotService.getUpdateValue("update_id", updates)
        if (updateIdString.isEmpty()) continue

        updateId = updateIdString.toInt() + 1
        println("Old id - ${updateId - 1}, new id - $updateId")

        val chatId = telegramBotService.getUpdateValue("chat_id", updates).toIntOrNull() ?: continue
        val msg = telegramBotService.getUpdateValue("text", updates)
        println("Text - $msg")

        if (msg.lowercase() == "/start") {
            telegramBotService.sendMenu(chatId)
            continue
        }

        val data = telegramBotService.getUpdateValue("data", updates).lowercase()

        if (data == STATISTIC_BUTTON_DATA) {
            val statistic = trainer.getStatistics()
            telegramBotService.sendMessage(
                chatId,
                "Выучено ${statistic.learnedCount} из ${statistic.totalCount} слов | ${statistic.learnedPercent}%!"
            )
            continue
        }

        if (data == LEARN_WORD_BUTTON_DATA) {
            checkNextQuestionAndSend(trainer, telegramBotService, chatId)
            continue
        }

        if (data.startsWith(CALLBACK_DATA_ANSWER_PREFIX)) {
            val userAnswerIndex = data.substringAfter(CALLBACK_DATA_ANSWER_PREFIX).toIntOrNull() ?: continue
            val question = trainer.question ?: continue

            if (trainer.checkAnswer(userAnswerIndex))
                telegramBotService.sendMessage(chatId, "Правильно!")
            else
                telegramBotService.sendMessage(
                    chatId,
                    "Не правильно! ${question.correctAnswer.original} - это ${question.correctAnswer.translate}."
                )

            checkNextQuestionAndSend(trainer, telegramBotService, chatId)
        }
    }
}

fun checkNextQuestionAndSend(
    trainer: LearnWordsTrainer,
    telegramBotService: TelegramBotService,
    chatId: Int
) {
    val question = trainer.getNextQuestion()
    if (question == null) {
        telegramBotService.sendMessage(chatId, "Вы выучили все слова в базе!")
        return
    }

    telegramBotService.sendQuestion(chatId, question)
}