package cc.wybxc

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.EventPriority
import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.NewFriendRequestEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.findIsInstance
import kotlin.random.Random

object Yiri : KotlinPlugin(JvmPluginDescription(
    id = "cc.wybxc.yiri",
    name = "Yiri",
    version = "0.1.0",
) {
    author("Wybxc")
}) {

    object Config : AutoSavePluginConfig("Yiri") {
        val talkServerHost by value("localhost")
        val talkServerPort by value(6001)
        val accept by value(-1.5)
    }

    private val client = HttpClient(CIO) {
        install(HttpTimeout)
        install(ContentNegotiation) {
            json()
        }
    }

    @Serializable
    data class TalkServerResponse(
        val result: String, val score: Double
    )

    private suspend fun getResponse(message: String): String {
        val response = client.get() {
            url {
                protocol = URLProtocol.HTTP
                host = Config.talkServerHost
                port = Config.talkServerPort
                encodedPath = "/"
                parameters.append("msg", message)
            }
            timeout { requestTimeoutMillis = 5000 }
        }.body<TalkServerResponse>()

        logger.debug("response: $response")

        if (response.score > Config.accept) {
            return response.result
        }
        return if (Random.nextFloat() > 0.5) {
            "?"
        } else {
            message
        }
    }

    private fun messageToText(message: MessageChain): String {
        return message.filterIsInstance<PlainText>().joinToString("") { it.content }.trim()
    }

    override fun onEnable() {
        Config.reload()
        logger.info("之之小姐启动成功")

        globalEventChannel().subscribeAlways<FriendMessageEvent> {
            try {
                val response = getResponse(message.contentToString().trim())
                subject.sendMessage(response)
            } catch (e: Exception) {
                logger.error("获取回复失败: $e")
            }
        }

        globalEventChannel().subscribeAlways<GroupMessageEvent>(priority = EventPriority.HIGHEST) call@{
            val atMe = message.findIsInstance<At>()?.run { target == bot.id } ?: false
            val messageText = messageToText(message)
            val reply = (atMe || Random.nextFloat() > 0.96) && messageText.isNotEmpty() && !messageText.startsWith('/')
            if (atMe && messageText.isEmpty()) {
                subject.sendMessage("你在叫我吗？")
                return@call
            }
            if (reply) {
                try {
                    val response = getResponse(messageText)
                    subject.sendMessage(response)
                    if (atMe) it.intercept()
                } catch (e: Exception) {
                    logger.error("获取回复失败: $e")
                }
            }
        }

        globalEventChannel().subscribeAlways<NewFriendRequestEvent> {
            logger.info("收到好友请求: $it")
            accept()
        }
    }
}
