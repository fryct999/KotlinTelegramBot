package fryct999

import kotlinx.serialization.json.Json
import java.io.File

const val WORDS_PER_QUESTION = 4
const val STATISTIC_BUTTON_DATA = "statistics_click"
const val LEARN_WORD_BUTTON_DATA = "learn_words_click"
const val RESET_BUTTON_DATA = "reset_click"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"

class DynamicMessage(
    var messageId: Long? = null,
    var statisticMessageId: Long? = null,
    var inlineMessageId: Long? = null,
    var imageMessageId: Long? = null,
) {
    private val messageHistory = mutableListOf<String>()

    fun updateMessage(newText: String) {
        messageHistory.add(newText)
    }

    fun rollbackToPrevious(): String {
        if (messageHistory.size > 1) {
            messageHistory.removeLastOrNull()
            return messageHistory.lastOrNull() ?: "Нет предыдущих сообщений"
        }

        return "Нет предыдущих сообщений"
    }
}

fun main(args: Array<String>) {
    val telegramBotService = TelegramBotService(token = args[0])
    val trainers = HashMap<Long, LearnWordsTrainer>()
    val usersDynamicMessageList = mutableMapOf<Long, DynamicMessage>()

    val json = Json { ignoreUnknownKeys = true }
    var lastUpdateId = 0L

    while (true) {
        Thread.sleep(2000)

        val responseString = telegramBotService.getUpdates(lastUpdateId)
        println(responseString)

        val response: Response = json.decodeFromString<Response>(responseString)
        if (response.result.isEmpty()) continue

        val sortedUpdates = response.result.sortedBy { it.updateId }
        sortedUpdates.forEach { handleUpdate(it, json, telegramBotService, trainers, usersDynamicMessageList) }
        lastUpdateId = sortedUpdates.last().updateId + 1
    }
}

fun handleUpdate(
    update: Update,
    json: Json,
    telegramBotService: TelegramBotService,
    trainers: HashMap<Long, LearnWordsTrainer>,
    usersDynamicMessageList: MutableMap<Long, DynamicMessage>,
) {
    val chatId = update.message?.chat?.id ?: update.callBackQuery?.message?.chat?.id ?: return
    val trainer = trainers.getOrPut(chatId) { LearnWordsTrainer("$chatId.txt", WORDS_PER_QUESTION) }
    val dynamicMessage: DynamicMessage = usersDynamicMessageList.getOrPut(chatId) { DynamicMessage() }

    if (update.message?.document != null) {
        val jsonResponse = telegramBotService.getFile(json, update.message.document.fileId)
        val response: GetFileResponse = json.decodeFromString(jsonResponse)
        response.result?.let {
            val newWordsFile = File(it.fileUniqueId)
            if (!newWordsFile.exists()) {
                telegramBotService.downloadFile(it.filePath, it.fileUniqueId)
                trainer.addNewWord(it.fileUniqueId)
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

    if (msg?.lowercase() == "/undo") {
        val text = dynamicMessage.rollbackToPrevious()
        sendTextMessage(dynamicMessage, text, telegramBotService, json, chatId)
    }

    val data = update.callBackQuery?.data?.lowercase()
    when {
        data == STATISTIC_BUTTON_DATA -> sendUserStatistic(
            trainer = trainer,
            telegramBotService = telegramBotService,
            dynamicMessage = dynamicMessage,
            chatId = chatId,
            json = json
        )

        data == LEARN_WORD_BUTTON_DATA -> {
            checkNextQuestionAndSend(json, trainer, telegramBotService, chatId, dynamicMessage)
        }

        data == RESET_BUTTON_DATA -> {
            trainer.resetProgress()
            val text = "Прогресс сброшен."
            sendTextMessage(dynamicMessage, text, telegramBotService, json, chatId)
            sendUserStatistic(
                trainer = trainer,
                telegramBotService = telegramBotService,
                dynamicMessage = dynamicMessage,
                chatId = chatId,
                json = json
            )
        }

        data?.startsWith(CALLBACK_DATA_ANSWER_PREFIX) == true -> {
            val userAnswerIndex = data.substringAfter(CALLBACK_DATA_ANSWER_PREFIX).toIntOrNull() ?: return
            val question = trainer.question ?: return

            val text = if (trainer.checkAnswer(userAnswerIndex)) {
                sendUserStatistic(
                    trainer = trainer,
                    telegramBotService = telegramBotService,
                    dynamicMessage = dynamicMessage,
                    chatId = chatId,
                    json = json
                )

                "Правильно!"
            } else
                "Не правильно! ${question.correctAnswer.original} - это ${question.correctAnswer.translate}."

            sendTextMessage(dynamicMessage, text, telegramBotService, json, chatId)
            checkNextQuestionAndSend(json, trainer, telegramBotService, chatId, dynamicMessage)
        }
    }
}

fun sendTextMessage(
    dynamicMessage: DynamicMessage,
    text: String,
    telegramBotService: TelegramBotService,
    json: Json,
    chatId: Long,
) {
    dynamicMessage.updateMessage(text)

    val messageId = dynamicMessage.messageId
    if (messageId == null) {
        dynamicMessage.messageId = telegramBotService.sendMessage(
            json = json,
            chatId = chatId,
            text = text,
        )
    } else {
        val result = telegramBotService.editMessage(
            json = json,
            chatId = chatId,
            text = text,
            messageId = messageId,
        )

        if (!result) {
            dynamicMessage.messageId = telegramBotService.sendMessage(
                json = json,
                chatId = chatId,
                text = text,
            )
        }
    }
}

fun sendUserStatistic(
    trainer: LearnWordsTrainer,
    dynamicMessage: DynamicMessage,
    telegramBotService: TelegramBotService,
    json: Json,
    chatId: Long,
) {
    val statistic = trainer.getStatistics()
    val percent = statistic.learnedPercent
    val progressBar = "█".repeat(percent / 10) + "▒".repeat(10 - percent / 10)
    val text =
        "Выучено ${statistic.learnedCount} из ${statistic.totalCount} слов.\nПроцесс изучения: $percent%\n[$progressBar]"

    val messageId = dynamicMessage.statisticMessageId
    if (messageId == null) {
        dynamicMessage.statisticMessageId = telegramBotService.sendMessage(
            json = json,
            chatId = chatId,
            text = text,
        )
    } else {
        val result = telegramBotService.editMessage(
            json = json,
            chatId = chatId,
            text = text,
            messageId = messageId,
        )

        if (!result) {
            dynamicMessage.statisticMessageId = telegramBotService.sendMessage(
                json = json,
                chatId = chatId,
                text = text,
            )
        }
    }
}

fun checkNextQuestionAndSend(
    json: Json,
    trainer: LearnWordsTrainer,
    telegramBotService: TelegramBotService,
    chatId: Long,
    dynamicMessage: DynamicMessage,
) {
    val question = trainer.getNextQuestion()
    if (question == null) {
        val text = "Вы выучили все слова в базе!"
        sendTextMessage(dynamicMessage, text, telegramBotService, json, chatId)
        return
    }

    val imageMessageId = dynamicMessage.imageMessageId
    val imageId = trainer.getImageFileId(question.correctAnswer.original)
    if (imageMessageId == null) {
        val answer = if (imageId != null) {
            telegramBotService.sendImage(
                json = json,
                chatId = chatId,
                fileId = imageId,
                hasSpoiler = true,
            )
        } else if (question.correctAnswer.imagePath != "") {
            telegramBotService.sendImage(
                json = json,
                chatId = chatId,
                file = File("imgWords/${question.correctAnswer.imagePath}"),
                hasSpoiler = true,
            )
        } else null

        if (answer != null) {
            val images = json.decodeFromString<ImageResponse>(answer)
            val originalImage = images.result.image.maxBy { it.fileSize }
            dynamicMessage.imageMessageId = images.result.messageId

            trainer.addImgFileId(question.correctAnswer, originalImage.fileId)
        }
    } else {
        if (imageId != null) {
            telegramBotService.editImageMessage(
                json = json,
                chatId = chatId,
                messageId = imageMessageId,
                fileId = imageId,
            )
        } else if (question.correctAnswer.imagePath != "") {
            val answer = telegramBotService.editImageMessage(
                json = json,
                chatId = chatId,
                messageId = imageMessageId,
                file = File("imgWords/${question.correctAnswer.imagePath}"),
            )

            if (answer != null) {
                val images = json.decodeFromString<ImageResponse>(answer)
                val originalImage = images.result.image.maxBy { it.fileSize }

                trainer.addImgFileId(question.correctAnswer, originalImage.fileId)
            }
        }
    }

    val questionMessageId = dynamicMessage.inlineMessageId
    if (questionMessageId == null)
        dynamicMessage.inlineMessageId = telegramBotService.sendQuestion(json, chatId, question)
    else
        telegramBotService.editQuestion(json, chatId, questionMessageId, question)
}