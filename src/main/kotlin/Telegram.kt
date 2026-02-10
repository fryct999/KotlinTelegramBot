package fryct999

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

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
        val urlGetFile = "$telegramBaseUrl/bot$token/getFile"
        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create(urlGetFile))
            .header("Content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(GetFileRequest(fileId = fileId))))
            .build()

        val response: HttpResponse<String> = client.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        )

        return response.body()
    }

    fun sendMessage(json: Json, chatId: Long, text: String) {
        if (text.isEmpty() || text.length >= 4096) return

        val requestBody = SendMessageRequest(
            chatId = chatId,
            text = text,
        )

        sendToBot(json.encodeToString(requestBody))
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

        sendToBot(json.encodeToString(requestBody))
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

        sendToBot(json.encodeToString(requestBody))
    }

    private fun sendToBot(body: String) {
        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create(urlSendMessage))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        client.send(request, HttpResponse.BodyHandlers.ofString())
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

        System.out.println("status code: " + response.statusCode())
        val body: InputStream = response.body()
        body.copyTo(File(fileName).outputStream(), 16 * 1024)
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

    telegramBotService.sendQuestion(json, chatId, question)
}