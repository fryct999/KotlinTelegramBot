package fryct999

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    @SerialName("message_id")
    val messageId: Long? = null,
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
data class EditMessageRequest(
    @SerialName("chat_id")
    val chatId: Long,
    @SerialName("text")
    val text: String,
    @SerialName("reply_markup")
    val replyMarkup: ReplyMarkup? = null,
    @SerialName("message_id")
    val messageId: Long? = null,
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
    @SerialName("message_id")
    val messageId: Long,
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

@Serializable
data class InputMedia(
    @SerialName("type")
    val type: String,
    @SerialName("media")
    val media: String,
    @SerialName("has_spoiler")
    val hasSpoiler: Boolean = false,
)

@Serializable
data class EditImageFileIdRequest(
    @SerialName("chat_id")
    val chatId: Long,
    @SerialName("message_id")
    val messageId: Long,
    @SerialName("media")
    val media: InputMedia,
)

@Serializable
data class SendMessageResponse(
    @SerialName("chat")
    val chat: Chat,
    @SerialName("text")
    val text: String,
    @SerialName("message_id")
    val messageId: Long,
    @SerialName("reply_markup")
    val replyMarkup: ReplyMarkup? = null,
)

@Serializable
data class SendResponse(
    @SerialName("result")
    val result: SendMessageResponse,
)

@Serializable
data class EditMessageResponse(
    @SerialName("ok")
    val status: Boolean,
    @SerialName("description")
    val description: String? = null,
)