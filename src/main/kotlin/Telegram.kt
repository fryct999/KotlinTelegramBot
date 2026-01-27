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

    fun getUpdates(updateId: Int): String {
        val urlGetUpdates = "$telegramBaseUrl/bot$token/getUpdates?offset=$updateId"
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
        val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

        return response.body()
    }

    fun getUpdateValue(key: String, updates: String): String {
        when (key) {
            "text" -> {
                val regex = "\"$key\":\"(.+?)\"".toRegex()
                return regex.find(updates)?.groups?.get(1)?.value ?: ""
            }

            "update_id" -> {
                val regex = "\"$key\":(\\d+)".toRegex()
                return regex.find(updates)?.groups?.get(1)?.value ?: ""
            }

            "chat" -> {
                val regex = "\"$key\":\\{\"id\":(\\d+)".toRegex()
                return regex.find(updates)?.groups?.get(1)?.value ?: ""
            }
        }

        return ""
    }

    fun sendMessage(chatId: String, text: String) {
        if (text.isEmpty() || text.length >= 4096) return

        val encodedMessage = URLEncoder.encode(text, StandardCharsets.UTF_8)
        val urlSendMsg = "$telegramBaseUrl/bot$token/sendMessage?chat_id=$chatId&text=$encodedMessage"
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlSendMsg)).build()
        client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}

fun main(args: Array<String>) {
    val telegramBotService = TelegramBotService(token = args[0])
    var updateId = 0

    while (true) {
        Thread.sleep(2000)

        val updates = telegramBotService.getUpdates(updateId)
        println(updates)

        val updateIdString = telegramBotService.getUpdateValue("update_id", updates)
        if (updateIdString.isEmpty()) continue

        updateId = updateIdString.toInt() + 1
        println("Old id - ${updateId - 1}, new id - $updateId")

        val msg = telegramBotService.getUpdateValue("text", updates)
        println("Text - $msg")

        if (msg != "Hello") continue

        val chatId = telegramBotService.getUpdateValue("chat", updates)
        if (chatId.isEmpty()) continue

        telegramBotService.sendMessage(chatId, "Hello!")
    }
}