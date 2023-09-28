import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.security.SecureRandom


fun main(args: Array<String>) {
    Main().main(args)
}
class Main : ListenerAdapter() {
    lateinit var FILE: File
    lateinit var NODE: ObjectNode
    fun main(args: Array<String>) {
        FILE = if (File("data.json").exists()) File("data.json")
        else File("build/resources/main/data.json")
        NODE = ObjectMapper().readTree(FILE).deepCopy()
        JDABuilder.createDefault(args[0])
            .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
            .setMemberCachePolicy(MemberCachePolicy.ALL)
            .addEventListeners(this)
            .build()
            .updateCommands()
            .addCommands(
                Commands.slash("message", "ようこそメッセージを変更します。@userをユーザーのメンションに変換します。")
                    .addOption(OptionType.STRING, "メッセージ", "@userをユーザーのメンションに変換します。", true),
                Commands.slash("role", "ようこそロールを変更します。")
                    .addOption(OptionType.ROLE, "ロール", "ロール", true),
                Commands.slash("channel", "チャンネルの名前を変更します。")
                    .addOption(OptionType.STRING, "名前", "チャンネルの名前", true),
                Commands.slash("auto-message", "自動応答メッセージを管理します")
                    .addSubcommands(
                        SubcommandData("add", "自動応答メッセージを追加します"),
                        SubcommandData("remove", "自動応答メッセージを削除します")
                            .addOption(OptionType.STRING, "キーワード", "削除対象キーワード", true)
                    )
            )
            .queue()
    }
    override fun onGuildMemberJoin(e: GuildMemberJoinEvent) {
        e.guild.createTextChannel(NODE.get("welcome_channel").asText(e.user.effectiveName).replace("@user", e.user.effectiveName))
            .addPermissionOverride(e.guild.publicRole, 0, 1024)
            .addPermissionOverride(e.member, 1024, 0)
            .queue { c ->
            c.sendMessage(
                NODE.get("welcome_message").asText(e.user.asMention).replace("@user", e.user.asMention)
            ).queue()
        }
        e.guild.addRoleToMember(e.user, e.guild.getRoleById(NODE.get("welcome_role").asText())!!).queue()
    }

    override fun onSlashCommandInteraction(e: SlashCommandInteractionEvent) {
        val code = when(e.name) {
            "message" -> let {
                NODE.put("welcome_message", e.getOption("メッセージ")?.asString)
                return@let 200
            }
            "role" -> let {
                if(!e.guild!!.selfMember.canInteract(e.getOption("ロール")!!.asRole))
                    return@let 403
                NODE.put("welcome_role", e.getOption("ロール")?.asRole?.id)
                return@let 200
            }
            "channel" -> let {
                NODE.put("welcome_channel", e.getOption("名前")?.asString)
                return@let 200
            }
            "auto-message" -> let {
                when(e.subcommandName) {
                    "add" -> {
                        e.replyModal(
                            Modal.create("auto-message-modal", "キーワード応答")
                                .addActionRow(TextInput.create("keyword", "キーワード(部分一致と全文一致の両方)", TextInputStyle.SHORT).setRequired(true).build())
                                .addActionRow(TextInput.create("reply", "応答メッセージ(ランダムにするには1メッセージごとに改行してください)", TextInputStyle.PARAGRAPH).setRequired(true).build())
                                .build()
                        ).queue()
                        return@let 0
                    }
                    "remove" -> {
                        (NODE.get("keywords") as ObjectNode).remove(e.getOption("キーワード")!!.asString)
                        json()
                        return@let 200
                    }
                    else -> 404
                }
            }
            else -> 404
        }
        when (code) {
            200 -> e.reply("> 設定を変更しました。").queue()
            403 -> e.reply("> 権限がありません。").queue()
            404 -> e.reply("> 不明なコマンドです。").queue()
            0 -> {}
        }
        json()
    }

    override fun onModalInteraction(e: ModalInteractionEvent) {
        val code = when(e.modalId) {
            "auto-message-modal" -> let {
                val array = (NODE.get("keywords") as ObjectNode).putArray(e.getValue("keyword")?.asString)
                e.getValue("reply")!!.asString.split("\n").forEach { s ->
                    array.add(s)
                }
                json()
                return@let 200
            }
            else -> {404}
        }
        when (code) {
            200 -> e.reply("> 設定を変更しました。").queue()
            403 -> e.reply("> 権限がありません。").queue()
            404 -> e.reply("> 不明なコマンドです。").queue()
        }
        json()
    }

    override fun onMessageReceived(e: MessageReceivedEvent) {
        NODE.get("keywords").fieldNames().forEach {
            if (e.message.contentDisplay.contains(it) && !e.member!!.user.isBot) {
                val sr = SecureRandom()
                e.message.reply(NODE.get("keywords").get(it).get(sr.nextInt(NODE.get("keywords").get(it).size())).textValue()).queue()
                return
            }
        }

    }
    private fun json() {
        val bw = BufferedWriter(FileWriter(FILE))
        bw.write(ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(NODE))
        bw.flush()
        bw.close()
    }
}



