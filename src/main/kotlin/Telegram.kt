package fryct999

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

const val TELEGRAM_BASE_URL = "https://api.telegram.org"

fun main(args: Array<String>) {
    val botToken = args[0]
    var updateId = 0

    while (true) {
        Thread.sleep(2000)
        val updates: String = getUpdates(botToken, updateId)
        println(updates)

        val updateIdString = getUpdateValue("update_id", updates)
        if (updateIdString.isEmpty()) continue

        updateId = updateIdString.toInt() + 1
        println("Old id - ${updateId - 1}, new id - $updateId")

        val msg = getUpdateValue("text", updates)
        println("Text - $msg")
    }
}

fun getUpdates(botToken: String, updateId: Int): String {
    val urlGetUpdates = "$TELEGRAM_BASE_URL/bot$botToken/getUpdates?offset=$updateId"
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
    }

    return ""
}