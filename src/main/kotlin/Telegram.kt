package fryct999

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

const val MIN_CORRECT_ANSWER = 3
const val WORDS_PER_QUESTION = 4
const val STATISTIC_BUTTON_DATA = "statistics_click"
const val LEARN_WORD_BUTTON_DATA = "learn_words_click"
const val RESET_BUTTON_DATA = "reset_click"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"

@Serializable
data class Update(
    @SerialName("update_id")
    val updateId: Long,
    @SerialName("message")
    val message: Message? = null,
    @SerialName("callback_query")
    val callBackQuery: CallBackQuery? = null,
)

@Serializable
data class Response(
    @SerialName("result")
    val result: List<Update>,
)

@Serializable
data class Message(
    @SerialName("text")
    val text: String? = null,
    @SerialName("chat")
    val chat: Chat,
    @SerialName("document")
    val document: Document? = null,
)

@Serializable
data class CallBackQuery(
    @SerialName("data")
    val data: String,
    @SerialName("message")
    val message: Message? = null,
)

@Serializable
data class Chat(
    @SerialName("id")
    val id: Long,
)

@Serializable
data class InLineKeyBoard(
    @SerialName("callback_data")
    val callbackData: String,
    @SerialName("text")
    val text: String,
)

@Serializable
data class ReplyMarkup(
    @SerialName("inline_keyboard")
    val inlineKeyboard: List<List<InLineKeyBoard>>,
)

@Serializable
data class SendMessageRequest(
    @SerialName("chat_id")
    val chatId: Long,
    @SerialName("text")
    val text: String,
    @SerialName("reply_markup")
    val replyMarkup: ReplyMarkup? = null,
)

@Serializable
data class Document(
    @SerialName("file_name")
    val fileName: String,
    @SerialName("mime_type")
    val mimeType: String,
    @SerialName("file_id")
    val fileId: String,
    @SerialName("file_unique_id")
    val fileUniqueId: String,
    @SerialName("file_size")
    val fileSize: Long,
)

@Serializable
data class GetFileRequest(
    @SerialName("file_id")
    val fileId: String
)

@Serializable
data class GetFileResponse(
    @SerialName("ok")
    val status: Boolean,
    @SerialName("result")
    val result: TelegramFile? = null,
)

@Serializable
data class TelegramFile(
    @SerialName("file_id")
    val fileId: String,
    @SerialName("file_unique_id")
    val fileUniqueId: String,
    @SerialName("file_size")
    val fileSize: Long,
    @SerialName("file_path")
    val filePath: String,
)

@Serializable
data class ImageResponse(
    @SerialName("result")
    val result: ImageList,
)

@Serializable
data class ImageList(
    @SerialName("photo")
    val image: List<ImageData>,
)

@Serializable
data class ImageData(
    @SerialName("file_id")
    val fileId: String,
    @SerialName("file_size")
    val fileSize: Int,
)

@Serializable
data class SendImageFileIdRequest(
    @SerialName("chat_id")
    val chatId: Long,
    @SerialName("has_spoiler")
    val hasSpoiler: Boolean,
    @SerialName("photo")
    val fileId: String,
)

private fun HttpRequest.Builder.postMultipartFormData(boundary: String, data: Map<String, Any>): HttpRequest.Builder {
    val byteArrays = ArrayList<ByteArray>()
    val separator = "--$boundary\r\nContent-Disposition: form-data; name=".toByteArray(StandardCharsets.UTF_8)

    for (entry in data.entries) {
        byteArrays.add(separator)
        when (entry.value) {
            is File -> {
                val file = entry.value as File
                val path = Path.of(file.toURI())
                val mimeType = Files.probeContentType(path)
                byteArrays.add(
                    "\"${entry.key}\"; filename=\"${path.fileName}\"\r\nContent-Type: $mimeType\r\n\r\n".toByteArray(
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
    private val client = HttpClient.newBuilder().build()

    fun getUpdates(updateId: Long): String {
        val urlGetUpdates = "$telegramBaseUrl/bot$token/getUpdates?offset=$updateId"
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
        val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

        return response.body()
    }

    fun getFile(json: Json, fileId: String): String {
        val url = "$telegramBaseUrl/bot$token/getFile"
        val responseBody = sendToBot(url, json.encodeToString(GetFileRequest(fileId = fileId)))

        return responseBody
    }

    fun sendMessage(json: Json, chatId: Long, text: String) {
        if (text.isEmpty() || text.length >= 4096) return

        val requestBody = SendMessageRequest(
            chatId = chatId,
            text = text,
        )

        sendToBot(urlSendMessage, json.encodeToString(requestBody))
    }

    fun sendPhoto(
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

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            return response.body()
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

    fun sendMenu(json: Json, chatId: Long) {
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

        sendToBot(urlSendMessage, json.encodeToString(requestBody))
    }

    fun sendQuestion(json: Json, chatId: Long, question: Question) {
        val requestBody = SendMessageRequest(
            chatId = chatId,
            text = question.correctAnswer.original,
            replyMarkup = ReplyMarkup(
                listOf(question.variants.mapIndexed { index, word ->
                    InLineKeyBoard(text = word.translate, callbackData = "${CALLBACK_DATA_ANSWER_PREFIX}$index")
                })
            )
        )

        sendToBot(urlSendMessage, json.encodeToString(requestBody))
    }

    private fun sendToBot(url: String, body: String): String {
        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body()
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

fun main(args: Array<String>) {
    val telegramBotService = TelegramBotService(token = args[0])
    val trainers = HashMap<Long, LearnWordsTrainer>()
    val json = Json { ignoreUnknownKeys = true }
    var lastUpdateId = 0L

    while (true) {
        Thread.sleep(2000)

        val responseString = telegramBotService.getUpdates(lastUpdateId)
        println(responseString)

        val response: Response = json.decodeFromString<Response>(responseString)
        if (response.result.isEmpty()) continue

        val sortedUpdates = response.result.sortedBy { it.updateId }
        sortedUpdates.forEach { handleUpdate(it, json, telegramBotService, trainers) }
        lastUpdateId = sortedUpdates.last().updateId + 1
    }
}

fun handleUpdate(
    update: Update,
    json: Json,
    telegramBotService: TelegramBotService,
    trainers: HashMap<Long, LearnWordsTrainer>
) {
    val chatId = update.message?.chat?.id ?: update.callBackQuery?.message?.chat?.id ?: return
    val trainer = trainers.getOrPut(chatId) { LearnWordsTrainer("$chatId.txt", MIN_CORRECT_ANSWER, WORDS_PER_QUESTION) }

    if (update.message?.document != null) {
        val jsonResponse = telegramBotService.getFile(json, update.message.document.fileId)
        val response: GetFileResponse = json.decodeFromString(jsonResponse)
        response.result?.let {
            val newWordsFile = File(it.fileUniqueId)
            if (!newWordsFile.exists()) {
                telegramBotService.downloadFile(it.filePath, it.fileUniqueId)
                trainer.addNewWords(it.fileUniqueId)
            }
        }

        return
    }

    val msg = update.message?.text
    println("Text - $msg")

    if (msg?.lowercase() == "/start") {
        telegramBotService.sendMenu(json, chatId)
        return
    }

    val data = update.callBackQuery?.data?.lowercase()

    when {
        data == STATISTIC_BUTTON_DATA -> {
            val statistic = trainer.getStatistics()
            telegramBotService.sendMessage(
                json,
                chatId,
                "Выучено ${statistic.learnedCount} из ${statistic.totalCount} слов | ${statistic.learnedPercent}%!"
            )
        }

        data == LEARN_WORD_BUTTON_DATA -> {
            checkNextQuestionAndSend(json, trainer, telegramBotService, chatId)
        }

        data == RESET_BUTTON_DATA -> {
            trainer.resetProgress()
            telegramBotService.sendMessage(json, chatId, "Прогресс сброшен.")
        }

        data?.startsWith(CALLBACK_DATA_ANSWER_PREFIX) == true -> {
            val userAnswerIndex = data.substringAfter(CALLBACK_DATA_ANSWER_PREFIX).toIntOrNull() ?: return
            val question = trainer.question ?: return

            if (trainer.checkAnswer(userAnswerIndex))
                telegramBotService.sendMessage(json, chatId, "Правильно!")
            else
                telegramBotService.sendMessage(
                    json,
                    chatId,
                    "Не правильно! ${question.correctAnswer.original} - это ${question.correctAnswer.translate}."
                )

            checkNextQuestionAndSend(json, trainer, telegramBotService, chatId)
        }
    }
}

fun checkNextQuestionAndSend(
    json: Json,
    trainer: LearnWordsTrainer,
    telegramBotService: TelegramBotService,
    chatId: Long
) {
    val question = trainer.getNextQuestion()
    if (question == null) {
        telegramBotService.sendMessage(json, chatId, "Вы выучили все слова в базе!")
        return
    }

    val imageId = trainer.getImageFileId(question.correctAnswer.original)
    if (imageId != null) {
        telegramBotService.sendPhoto(
            json = json,
            chatId = chatId,
            fileId = imageId,
            hasSpoiler = true,
        )
    } else if (question.correctAnswer.imagePath != "") {
        val answer = telegramBotService.sendPhoto(
            json = json,
            chatId = chatId,
            file = File("imgWords/${question.correctAnswer.imagePath}"),
            hasSpoiler = true,
        )

        if (answer != null) {
            val images = json.decodeFromString<ImageResponse>(answer)
            val originalImage = images.result.image.maxBy { it.fileSize }

            trainer.addImgFileId(question.correctAnswer, originalImage.fileId)
        }
    }

    telegramBotService.sendQuestion(json, chatId, question)
}