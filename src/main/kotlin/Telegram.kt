package fryct999

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class TelegramBotService(private val token: String) {
    private val telegramBaseUrl = "https://api.telegram.org"

    fun getUpdates(updateId: Int): String {
        val urlGetUpdates = "$telegramBaseUrl/bot$token/getUpdates?offset=$updateId"
        val client: HttpClient = HttpClient.newBuilder().build()
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
        val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

        return response.body()
    }

    fun getUpdateValue(key: String, updates: String): String {
        when (key) {
            "text" -> {
                val regex = "\"$key\":\"(.+?)\"".toRegex()
                val text = regex.find(updates)?.groups?.get(1)?.value ?: ""

                return text
            }

            "update_id" -> {
                val regex = "\"$key\":(\\d+)".toRegex()
                val id = regex.find(updates)?.groups?.get(1)?.value ?: ""

                return id
            }

            "chat" -> {
                val regex = "\"$key\":\\{\"id\":(\\d+)".toRegex()
                val id = regex.find(updates)?.groups?.get(1)?.value ?: ""

                return id
            }
        }

        return ""
    }

    fun sendMessage(chatId: String, text: String) {
        if (text.isEmpty() || text.length >= 4096) return

        val urlSendMsg = "$telegramBaseUrl/bot$token/sendMessage?chat_id=$chatId&text=$text"
        val client: HttpClient = HttpClient.newBuilder().build()
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
        telegramBotService.sendMessage(chatId, "Hello!")
    }
}