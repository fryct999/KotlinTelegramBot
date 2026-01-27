package fryct999

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

class TelegramBotService(private val token: String) {
    private val telegramBaseUrl = "https://api.telegram.org"
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

            else -> return ""
        }
    }

    fun sendMessage(chatId: String, text: String) {
        if (text.isEmpty() || text.length >= 4096) return

        val encodedMessage = URLEncoder.encode(text, StandardCharsets.UTF_8)
        val urlSendMsg = "$telegramBaseUrl/bot$token/sendMessage?chat_id=$chatId&text=$encodedMessage"
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlSendMsg)).build()
        client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    fun sendMenu(chatId: String) {
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

        val urlSendMenu = "$telegramBaseUrl/bot$token/sendMessage"
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlSendMenu))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(sendMenuBody))
            .build()

        client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}

fun main(args: Array<String>) {
    val telegramBotService = TelegramBotService(token = args[0])
    val trainer = LearnWordsTrainer()
    var updateId = 0

    while (true) {
        Thread.sleep(2000)

        val updates = telegramBotService.getUpdates(updateId)
        println(updates)

        val updateIdString = telegramBotService.getUpdateValue("update_id", updates)
        if (updateIdString.isEmpty()) continue

        updateId = updateIdString.toInt() + 1
        println("Old id - ${updateId - 1}, new id - $updateId")

        val chatId = telegramBotService.getUpdateValue("chat_id", updates)
        if (chatId.isEmpty()) continue

        val msg = telegramBotService.getUpdateValue("text", updates)
        println("Text - $msg")

        if (msg.lowercase() == "/start") {
            telegramBotService.sendMenu(chatId)
            continue
        }

        val data = telegramBotService.getUpdateValue("data", updates)

        if (data.lowercase() == "statistics_click") {
            telegramBotService.sendMessage(chatId, "Выучено 10 из 10 слов | 100%!")
            continue
        }
    }
}