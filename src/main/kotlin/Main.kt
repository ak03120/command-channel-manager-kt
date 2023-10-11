import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter


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
            .enableIntents(GatewayIntent.GUILD_MEMBERS)
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
                    .addOption(OptionType.STRING, "名前", "チャンネルの名前", true)
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
            else -> {404}
        }
        when (code) {
            200 -> e.reply("> 設定を変更しました。").queue()
            403 -> e.reply("> 権限がありません。").queue()
            404 -> e.reply("> 不明なコマンドです。").queue()
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



