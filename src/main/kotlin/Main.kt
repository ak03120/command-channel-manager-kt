import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.requests.restaction.ChannelAction
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.TimeUnit


fun main(args: Array<String>) {
    Main().main(args)
}

@Suppress("unused")
class Main : ListenerAdapter() {
    lateinit var FILE: File
    lateinit var NODE: ObjectNode
    lateinit var dailyTask: Runnable
    val timer: Timer = Timer()
    lateinit var timerTask: Array<TimerTask>
    var ignoreParents: Array<String> = arrayOf("1179650547335299072")
    fun main(args: Array<String>) {
        // データ読み込み
        FILE = if (File("data.json").exists()) File("data.json")
        else File("build/resources/main/data.json")
        NODE = ObjectMapper().readTree(FILE).deepCopy()
        JDABuilder.createDefault(args[0])
            .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
            .setChunkingFilter(ChunkingFilter.ALL)
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
                Commands.slash("staff-user", "スタッフユーザーを管理します")
                    .addSubcommands(
                        SubcommandData("add", "スタッフユーザーを追加します")
                            .addOption(OptionType.USER, "ユーザー", "スタッフユーザー", true),
                        SubcommandData("remove", "スタッフユーザーを削除します")
                            .addOption(OptionType.USER, "ユーザー", "スタッフユーザー", true)
                    ),
                Commands.slash("staff-role", "スタッフロールを管理します")
                    .addSubcommands(
                        SubcommandData("add", "スタッフロールを追加します")
                            .addOption(OptionType.ROLE, "ロール", "スタッフロール", true),
                        SubcommandData("remove", "スタッフロールを削除します")
                            .addOption(OptionType.ROLE, "ロール", "スタッフロール", true)
                    ),
                Commands.slash("remind", "強制的に未返信のメッセージ一覧を表示します。")
                    .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
            )
            .queue()
    }

    override fun onReady(e: ReadyEvent) {
        val guild = e.jda.getGuildById("1146405548422598778")!!
        val cl: Calendar = Calendar.getInstance()
        //cl.add(Calendar.DATE, 1)
        cl.set(Calendar.HOUR_OF_DAY, 17)
        cl.set(Calendar.MINUTE, 0)
        cl.set(Calendar.SECOND, 0)
        cl.set(Calendar.MILLISECOND, 0)
        if (cl.before(Calendar.getInstance())) {
            cl.add(Calendar.DAY_OF_MONTH, 1)
        }
        // 結果を表示
        System.out.println("Adjusted Calendar: " + cl.getTime())

        val task: Array<TimerTask> = arrayOf<TimerTask>(object : TimerTask(
        ) {
            override fun run() {
                guild.getTextChannelById("1197012382204039188")!!
                    .sendMessage("**未返信チャンネルのリマインダーです。**").queue()
                guild.textChannels.filter { channel ->
                    channel.parentCategory == null || !ignoreParents.contains(
                        channel.parentCategoryId
                    )
                }.forEach {
                    val mes: Message = it.retrieveMessageById(it.latestMessageId).complete()
                    val em: EmbedBuilder =
                        EmbedBuilder().setAuthor(mes.author.effectiveName, null, mes.author.avatarUrl)
                            .setDescription(mes.contentRaw)
                            .setTimestamp(mes.timeCreated)
                    if (mes.attachments.size > 0)
                        em.setImage(mes.attachments[0].url)
                    if (!mes.author.isBot && mes.member != null && !mes.member!!.roles.contains(
                            guild.getRoleById(
                                "1196067979113267290"
                            )
                        )
                    )
                        guild.getTextChannelById("1197012382204039188")!!.sendMessage(mes.jumpUrl)
                            .setEmbeds(em.build()).queue()
                }
            }
        }
        )
        timerTask = task
        timer.scheduleAtFixedRate(task[0], cl.time, TimeUnit.DAYS.toMillis(1L))

//            val now = LocalDateTime.now()
//            var nextExecutionTime = LocalDateTime.of(now.toLocalDate(), LocalTime.of(18, 17, 0))
//            // すでに17:00:00を過ぎていた場合は次の日の同時刻に設定
//            if (now.compareTo(nextExecutionTime) > 0) {
//                nextExecutionTime = nextExecutionTime.plusDays(1)
//            }
//            // ZonedDateTimeに変換（タイムゾーンはシステムのデフォルトを使用）
//            val zonedDateTime = nextExecutionTime.atZone(ZoneId.systemDefault())
//            // ScheduledExecutorServiceを生成
//            val scheduler = Executors.newScheduledThreadPool(1)
//            // 定期的な処理を実行するRunnableを生成
//            dailyTask = Runnable {
//                guild.getTextChannelById("1197012382204039188")!!
//                    .sendMessage("**未返信チャンネルのリマインダーです。**").queue()
//                guild.textChannels.filter { channel ->
//                    channel.parentCategory == null || !ignoreParents.contains(
//                        channel.parentCategoryId
//                    )
//                }.forEach {
//                    val mes: Message = it.retrieveMessageById(it.latestMessageId).complete()
//                    val em: EmbedBuilder =
//                        EmbedBuilder().setAuthor(mes.author.effectiveName, null, mes.author.avatarUrl)
//                            .setDescription(mes.contentRaw)
//                            .setTimestamp(mes.timeCreated)
//                    if (mes.attachments.size > 0)
//                        em.setImage(mes.attachments[0].url)
//                    if (!mes.author.isBot && mes.member != null && !mes.member!!.roles.contains(
//                            guild.getRoleById(
//                                "1196067979113267290"
//                            )
//                        )
//                    )
//                        guild.getTextChannelById("1197012382204039188")!!.sendMessage(mes.jumpUrl)
//                            .setEmbeds(em.build()).queue()
//                }
//            }
//            // 最初の実行を設定
//            val initialDelay = zonedDateTime.toInstant().toEpochMilli() - System.currentTimeMillis()
//            scheduler.scheduleAtFixedRate(dailyTask, initialDelay, 1, TimeUnit.DAYS)
        println("Connected to Discord.")
    }

    override fun onGuildMemberJoin(e: GuildMemberJoinEvent) {
        if (e.guild.getRoleById(NODE.get("welcome_role").asText()) != null)
        // welcome_roleに設定されているIDからギルドのロールを取得して、付与
            e.guild.addRoleToMember(e.user, e.guild.getRoleById(NODE.get("welcome_role").asText())!!).queue()
        // welcome_channelの設定値のチャンネルを作成
        val newChannel: ChannelAction<TextChannel> = e.guild.createTextChannel(NODE.get("welcome_channel").asText(e.user.effectiveName).replace("@user", e.user.effectiveName))
            .addPermissionOverride(e.guild.publicRole, 0, 1024)
            .addPermissionOverride(e.member, 1024, 0)
        for (i in 0..<NODE.get("staff_user").size()) {
            newChannel.addMemberPermissionOverride(NODE.get("staff_user").get(i).asLong(), 1024, 0)
        }
        for (i in 0..<NODE.get("staff_role").size()) {
            newChannel.addRolePermissionOverride(NODE.get("staff_role").get(i).asLong(), 1024, 0)
        }
        newChannel.queue { c ->
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
                        return@let 200
                    }
                    else -> 404
                }
            }
            "staff-user" -> let {
                when(e.subcommandName) {
                    "add" -> {
                        (NODE.get("staff_user") as ArrayNode).add(e.getOption("ユーザー")!!.asLong)
                        return@let 200
                    }
                    "remove" -> {
                        for(i in 0..<(NODE.get("staff_user") as ArrayNode).size()) {
                            if((NODE.get("staff_user") as ArrayNode).get(i).asLong() == e.getOption("ユーザー")!!.asLong)
                                (NODE.get("staff_user") as ArrayNode).remove(i)
                        }
                        return@let 200
                    }
                    else -> 404
                }
            }
            "staff-role" -> let {
                when(e.subcommandName) {
                    "add" -> {
                        (NODE.get("staff_role") as ArrayNode).add(e.getOption("ロール")!!.asLong)
                        return@let 200
                    }
                    "remove" -> {
                        for(i in 0..<(NODE.get("staff_role") as ArrayNode).size()) {
                            if((NODE.get("staff_role") as ArrayNode).get(i).asLong() == e.getOption("ロール")!!.asLong)
                                (NODE.get("staff_role") as ArrayNode).remove(i)
                        }
                        return@let 200
                    }
                    else -> 404
                }
            }
            "remind" -> let {
                e.reply("未返信のメッセージを取得中です。").setEphemeral(true)
                timerTask[0].run()
                return@let 0
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
        if (e.channel.asTextChannel().parentCategory == null || !ignoreParents.contains(e.channel.asTextChannel().parentCategoryId)) {
            if (!e.message.author.isBot && !e.message.member!!.roles.contains(e.guild.getRoleById("1196067979113267290"))) {
                val em: EmbedBuilder =
                    EmbedBuilder().setAuthor(e.author.effectiveName, null, e.author.avatarUrl)
                        .setDescription(e.message.contentRaw)
                        .setTimestamp(e.message.timeCreated)
                if (e.message.attachments.size > 0)
                    em.setImage(e.message.attachments[0].url)
                e.guild.getTextChannelById("1197012382204039188")!!.sendMessage(e.message.jumpUrl).setEmbeds(em.build())
                    .queue()
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



