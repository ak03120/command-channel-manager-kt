import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter


fun main(args: Array<String>) {
    Main().main(args)
}

@Suppress("KotlinConstantConditions")
class Main : ListenerAdapter() {
    lateinit var FILE: File
    lateinit var NODE: ObjectNode
    fun main(args: Array<String>) {
        // データ読み込み
        FILE = if (File("data.json").exists()) File("data.json")
        else File("build/resources/main/data.json")
        NODE = ObjectMapper().readTree(FILE).deepCopy()
        JDABuilder.createDefault(args[0])
            .addEventListeners(this)
            .build()
            .updateCommands()
            .addCommands(
                Commands.slash("scrim", "管理者と実行者のテキストチャンネルを作成します。")
                    .addOption(OptionType.STRING, "コマンド", "要件")
                    .addOption(OptionType.STRING, "チャンネル", "作成されるチャンネル名"),
                Commands.slash("set-message", "チャンネル作成時のメッセージを編集します。")
                    .addOption(OptionType.STRING, "メッセージ", "チャンネル作成時に送信するメッセージ"),
                Commands.slash("delete-channel", "チャンネルを削除します。")
            )
            .queue()
    }

    override fun onSlashCommandInteraction(e: SlashCommandInteractionEvent) {
        val code = when(e.name) {
            "set-message" -> {
                NODE.put("message", e.getOption("メッセージ")?.asString) // 設定値を保存（内部的に）
                200
            }
            "scrim" -> {
                e.guild!!.createTextChannel(e.getOption("チャンネル")!!.asString)
                    .addPermissionOverride(e.guild!!.publicRole, 0, 1024)
                    .addMemberPermissionOverride(e.member!!.id.toLong(), 1024, 0)
                    .queue { c ->
                        c.sendMessage(NODE.get("message").asText("初期メッセージ").replace("@user", e.user.asMention)).queue()
                    }
                200
            }
            "delete-channel" -> {
                e.channel.delete().queue()
                200
            }
            else -> 404
        }
        when (code) {
            200 -> e.reply("> 正常に実行しました").queue()
            403 -> e.reply("> 権限がありません。").queue()
            404 -> e.reply("> 不明なコマンドです。").queue()
            0 -> {}
        }
        json()
    }

    private fun json() {
        // データ保存
        val bw = BufferedWriter(FileWriter(FILE))
        bw.write(ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(NODE))
        bw.flush()
        bw.close()
    }
}



