package fryct999

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

const val MIN_CORRECT_ANSWER = 3
const val WORDS_PER_QUESTION = 4
const val STATISTIC_BUTTON_DATA = "statistics_click"
const val LEARN_WORD_BUTTON_DATA = "learn_words_click"
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
    val text: String,
    @SerialName("chat")
    val chat: Chat,
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

    fun sendMessage(json: Json, chatId: Long, text: String) {
        if (text.isEmpty() || text.length >= 4096) return

        val requestBody = SendMessageRequest(
            chatId = chatId,
            text = text,
        )

        val requestBodyString = json.encodeToString(requestBody)
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlSendMessage))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
            .build()

        client.send(request, HttpResponse.BodyHandlers.ofString())
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
                    )
                )
            )
        )

        val requestBodyString = json.encodeToString(requestBody)
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlSendMessage))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
            .build()

        client.send(request, HttpResponse.BodyHandlers.ofString())
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

        val requestBodyString = json.encodeToString(requestBody)
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlSendMessage))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
            .build()

        client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}

fun main(args: Array<String>) {
    val telegramBotService = TelegramBotService(token = args[0])
    val trainer = LearnWordsTrainer(MIN_CORRECT_ANSWER, WORDS_PER_QUESTION)
    var lastUpdateId = 0L

    val json = Json {
        ignoreUnknownKeys = true
    }

    while (true) {
        Thread.sleep(2000)

        val responseString = telegramBotService.getUpdates(lastUpdateId)
        println(responseString)

        val response: Response = json.decodeFromString<Response>(responseString)
        val updates = response.result
        val firstUpdate = updates.firstOrNull() ?: continue
        val updateId = firstUpdate.updateId
        lastUpdateId = updateId + 1

        val chatId = firstUpdate.message?.chat?.id ?: firstUpdate.callBackQuery?.message?.chat?.id
        if (chatId == null) continue

        val msg = firstUpdate.message?.text
        println("Text - $msg")

        if (msg?.lowercase() == "/start") {
            telegramBotService.sendMenu(json, chatId)
            continue
        }

        val data = firstUpdate.callBackQuery?.data?.lowercase()

        if (data == STATISTIC_BUTTON_DATA) {
            val statistic = trainer.getStatistics()
            telegramBotService.sendMessage(
                json,
                chatId,
                "Выучено ${statistic.learnedCount} из ${statistic.totalCount} слов | ${statistic.learnedPercent}%!"
            )
            continue
        }

        if (data == LEARN_WORD_BUTTON_DATA) {
            checkNextQuestionAndSend(json, trainer, telegramBotService, chatId)
            continue
        }

        if (data?.startsWith(CALLBACK_DATA_ANSWER_PREFIX) == true) {
            val userAnswerIndex = data.substringAfter(CALLBACK_DATA_ANSWER_PREFIX).toIntOrNull() ?: continue
            val question = trainer.question ?: continue

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