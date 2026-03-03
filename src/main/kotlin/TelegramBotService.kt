package fryct999

import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random

private fun HttpRequest.Builder.postMultipartFormData(
    boundary: String,
    data: Map<String, Any>,
    forEditMessage: Boolean = false
): HttpRequest.Builder {
    val byteArrays = ArrayList<ByteArray>()
    val separator = "--$boundary\r\nContent-Disposition: form-data; name=".toByteArray(StandardCharsets.UTF_8)

    for (entry in data.entries) {
        byteArrays.add(separator)
        when (entry.value) {
            is File -> {
                val file = entry.value as File
                val path = Path.of(file.toURI())
                val mimeType = Files.probeContentType(path)

                val fileNameField = if (forEditMessage && entry.key == "file") {
                    "\"file\"; filename=\"${path.fileName}\""
                } else {
                    "\"${entry.key}\"; filename=\"${path.fileName}\""
                }

                byteArrays.add(
                    "$fileNameField\r\nContent-Type: $mimeType\r\n\r\n".toByteArray(
                        StandardCharsets.UTF_8
                    )
                )

                byteArrays.add(Files.readAllBytes(path))
                byteArrays.add("\r\n".toByteArray(StandardCharsets.UTF_8))
            }

            else -> byteArrays.add("\"${entry.key}\"\r\n\r\n${entry.value}\r\n".toByteArray(StandardCharsets.UTF_8))
        }
    }
    byteArrays.add("--$boundary--".toByteArray(StandardCharsets.UTF_8))

    this.header("Content-Type", "multipart/form-data;boundary=$boundary")
        .POST(HttpRequest.BodyPublishers.ofByteArrays(byteArrays))

    return this
}

class TelegramBotService(private val token: String) {
    private val telegramBaseUrl = "https://api.telegram.org"
    private val urlSendMessage = "$telegramBaseUrl/bot$token/sendMessage"
    private val urlEditMessage = "$telegramBaseUrl/bot$token/editMessageText"
    private val client = HttpClient.newBuilder().build()

    private fun sendToBot(url: String, body: String): String {
        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body()
    }

    fun getUpdates(updateId: Long): String {
        val urlGetUpdates = "$telegramBaseUrl/bot$token/getUpdates?offset=$updateId"
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
        val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

        return response.body()
    }

    fun editMessage(json: Json, chatId: Long, messageId: Long, text: String): Boolean {
        val requestBody = EditMessageRequest(
            chatId = chatId,
            text = text,
            messageId = messageId,
        )

        val responseString = sendToBot(urlEditMessage, json.encodeToString(requestBody))
        val decode = json.decodeFromString<EditMessageResponse>(responseString)

        if (!decode.status && decode.description?.contains("message is not modified") == true)
            return true


        return decode.status
    }

    fun editQuestion(json: Json, chatId: Long, messageId: Long, question: Question) {
        val requestBody = EditMessageRequest(
            chatId = chatId,
            text = question.correctAnswer.original,
            messageId = messageId,
            replyMarkup = ReplyMarkup(
                listOf(question.variants.mapIndexed { index, word ->
                    InLineKeyBoard(text = word.translate, callbackData = "${CALLBACK_DATA_ANSWER_PREFIX}$index")
                })
            )
        )

        sendToBot(urlEditMessage, json.encodeToString(requestBody))
    }

    fun editImageMessage(
        json: Json,
        chatId: Long,
        messageId: Long,
        file: File? = null,
        fileId: String? = null,
    ): String? {
        val url = "$telegramBaseUrl/bot$token/editMessageMedia"

        if (file != null) {
            val data: MutableMap<String, Any> = LinkedHashMap()
            data["chat_id"] = chatId.toString()
            data["message_id"] = messageId.toString()
            data["media"] = json.encodeToString(InputMedia(type = "photo", media = "attach://file", hasSpoiler = true))
            data["file"] = file

            val boundary: String = BigInteger(35, Random()).toString()
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .postMultipartFormData(boundary, data, true)
                .build()

            return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
        } else if (fileId != null) {
            return sendToBot(
                url, json.encodeToString(
                    EditImageFileIdRequest(
                        chatId = chatId,
                        messageId = messageId,
                        media = InputMedia(media = fileId, type = "photo", hasSpoiler = true),
                    )
                )
            )
        } else return null
    }

    fun sendMessage(json: Json, chatId: Long, text: String): Long {
        if (text.isEmpty() || text.length >= 4096) return 0

        val requestBody = SendMessageRequest(
            chatId = chatId,
            text = text,
        )

        val responseString = sendToBot(urlSendMessage, json.encodeToString(requestBody))
        val response: SendResponse = json.decodeFromString<SendResponse>(responseString)

        return response.result.messageId
    }

    fun getFile(json: Json, fileId: String): String {
        val url = "$telegramBaseUrl/bot$token/getFile"
        val responseBody = sendToBot(url, json.encodeToString(GetFileRequest(fileId = fileId)))

        return responseBody
    }

    fun sendImage(
        json: Json,
        chatId: Long,
        file: File? = null,
        fileId: String? = null,
        hasSpoiler: Boolean = false
    ): String? {
        val url = "$telegramBaseUrl/bot$token/sendPhoto"

        if (file != null) {
            val data: MutableMap<String, Any> = LinkedHashMap()
            data["chat_id"] = chatId.toString()
            data["has_spoiler"] = hasSpoiler
            data["photo"] = file

            val boundary: String = BigInteger(35, Random()).toString()

            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .postMultipartFormData(boundary, data)
                .build()

            return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
        } else if (fileId != null) {
            return sendToBot(
                url, json.encodeToString(
                    SendImageFileIdRequest(
                        chatId = chatId,
                        hasSpoiler = hasSpoiler,
                        fileId = fileId,
                    )
                )
            )
        } else return null
    }

    fun sendMenu(json: Json, chatId: Long): Long {
        val requestBody = SendMessageRequest(
            chatId = chatId,
            text = "Основное меню",
            replyMarkup = ReplyMarkup(
                listOf(
                    listOf(
                        InLineKeyBoard(text = "Изучить слова", callbackData = LEARN_WORD_BUTTON_DATA),
                        InLineKeyBoard(text = "Статистика", callbackData = STATISTIC_BUTTON_DATA),
                    ),
                    listOf(
                        InLineKeyBoard(text = "Сбросить прогресс", callbackData = RESET_BUTTON_DATA),
                    )
                )
            )
        )

        val responseString = sendToBot(urlSendMessage, json.encodeToString(requestBody))
        val response: SendResponse = json.decodeFromString<SendResponse>(responseString)

        return response.result.messageId
    }

    fun sendQuestion(json: Json, chatId: Long, question: Question): Long {
        val requestBody = SendMessageRequest(
            chatId = chatId,
            text = question.correctAnswer.original,
            replyMarkup = ReplyMarkup(
                listOf(question.variants.mapIndexed { index, word ->
                    InLineKeyBoard(text = word.translate, callbackData = "${CALLBACK_DATA_ANSWER_PREFIX}$index")
                })
            )
        )

        val responseString = sendToBot(urlSendMessage, json.encodeToString(requestBody))
        val response: SendResponse = json.decodeFromString<SendResponse>(responseString)

        return response.result.messageId
    }

    fun downloadFile(filePath: String, fileName: String) {
        val urlGetFile = "$telegramBaseUrl/file/bot$token/$filePath"
        val request = HttpRequest
            .newBuilder()
            .uri(URI.create(urlGetFile))
            .GET()
            .build()

        val response: HttpResponse<InputStream> = HttpClient
            .newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofInputStream())

        println("Status code: " + response.statusCode())
        val body: InputStream = response.body()
        File(fileName).outputStream().use { outputStream ->
            body.use { inputStream ->
                inputStream.copyTo(outputStream, 16 * 1024)
            }
        }
    }
}