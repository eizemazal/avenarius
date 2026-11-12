package com.avenarius.app.net

import com.avenarius.app.model.Account
import com.avenarius.app.model.BotButton
import com.avenarius.app.model.Chat
import com.avenarius.app.model.ChatFolder
import com.avenarius.app.model.ContactCard
import com.avenarius.app.model.FileAttach
import com.avenarius.app.model.GeoPoint
import com.avenarius.app.model.LinkPreview
import com.avenarius.app.model.MediaAttach
import com.avenarius.app.model.MediaType
import com.avenarius.app.model.Message
import com.avenarius.app.model.MessageStatus
import com.avenarius.app.model.Poll
import com.avenarius.app.model.PollAnswer
import com.avenarius.app.model.Reaction
import com.avenarius.app.model.ReplyInfo
import com.avenarius.app.model.ServiceEvent
import com.avenarius.app.model.Sticker
import com.avenarius.app.model.StickerSet
import com.avenarius.app.model.TextSpan
import com.avenarius.app.model.UserInfo
import com.avenarius.app.model.VoiceAttach
import com.avenarius.app.resources.Res
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The demo account's starting data: people, chats and their histories, folders, devices
 * and stickers. Every name, number and link is invented; images are bundled under
 * `composeResources/files/demo`. Times are relative to [now] so the list reads "today",
 * "yesterday" and older dates whenever the demo is opened.
 */
@OptIn(ExperimentalResourceApi::class)
internal class DemoFixtures(
    private val now: Long,
) {
    // --- people -------------------------------------------------------------

    val me = Account(userId = ME, firstName = "Демо", lastName = "Пользователь")

    val meInfo =
        UserInfo(
            id = ME,
            name = "Демо Пользователь",
            firstName = "Демо",
            lastName = "Пользователь",
            description = "Демонстрационный профиль Авенариуса. Все люди и чаты здесь вымышлены.",
            phone = "79990000000",
        )

    val users =
        listOf(
            person(ALICE, "Алиса", "Демидова", "avatar_alice.jpg", "Люблю горы, фотографию и хороший кофе."),
            person(BORIS, "Борис", "Петров", "avatar_boris.jpg", "Дизайнер интерфейсов"),
            person(VERA, "Вера", "Соколова", "photo_abstract.jpg", "Тестирую всё, что движется"),
            person(GRISHA, "Григорий", "Лебедев"),
            person(DASHA, "Дарья", "Котова"),
            person(MOM, "Мама", null),
            person(LIDA, "Лидия", "Фомина", description = "Читаю по книге в неделю"),
            person(EVGENY, "Евгений", "Орлов"),
            UserInfo(id = BOT, name = "Погодный бот", description = "Присылаю прогноз погоды по запросу."),
        )

    /** Who is on the Contacts tab (the blocked user and the bot are known but not contacts). */
    val contactIds = setOf(ALICE, BORIS, VERA, GRISHA, DASHA, MOM, LIDA)

    val blocked = setOf(EVGENY)

    val online = setOf(ALICE, VERA)

    val presence =
        mapOf(
            ALICE to Presence(ALICE, online = true),
            VERA to Presence(VERA, online = true),
            BORIS to Presence(BORIS, online = false, seen = ago(m = 15) / 1000),
            GRISHA to Presence(GRISHA, online = false, seen = ago(d = 2, h = 4) / 1000),
            MOM to Presence(MOM, online = false, seen = ago(h = 20) / 1000),
            DASHA to Presence(DASHA, online = false, seen = ago(h = 3) / 1000),
            LIDA to Presence(LIDA, online = false, status = 2),
        )

    /** Вера is typing in her dialog for as long as the demo runs. */
    val typing = mapOf(dialog(VERA) to mapOf(VERA to Long.MAX_VALUE))

    val privacy =
        Privacy(searchByPhone = Privacy.CONTACTS, incomingCall = Privacy.ALL, chatsInvite = Privacy.CONTACTS, showReadMarks = true)

    val reactionsMenu = listOf("👍", "❤️", "🔥", "😂", "👏", "😮", "😢", "🎉")

    // --- stickers -----------------------------------------------------------

    val stickers =
        listOf(
            Sticker(STICKER_SMILE, img("sticker_smile.png")),
            Sticker(STICKER_HEART, img("sticker_heart.png")),
            Sticker(STICKER_OK, img("sticker_ok.png")),
        )

    val stickerSets = listOf(StickerSet(1, "Демо-набор", stickers.first().url, stickers.map { it.id }))

    // --- devices ------------------------------------------------------------

    val sessions =
        listOf(
            SessionInfo("Авенариус для Android", "Смартфон · Android 15", "Москва, Россия", now, current = true),
            SessionInfo("Веб-версия", "Браузер на Linux", "Санкт-Петербург, Россия", ago(h = 2), current = false),
            SessionInfo("Авенариус для компьютера", "Ноутбук · Linux", "Казань, Россия", ago(d = 3, h = 5), current = false),
        )

    // --- histories ----------------------------------------------------------

    private var seq = 100_000L

    /** The chat id → its messages, oldest first. */
    val history = mutableMapOf<Long, List<Message>>()

    /** The chat id → the id of its pinned message. */
    val pinned = mutableMapOf<Long, String>()

    init {
        aliceDialog()
        borisDialog()
        veraDialog()
        grishaDialog()
        momDialog()
        savedMessages()
        workGroup()
        familyGroup()
        bookClub()
        newsChannel()
        travelChannel()
        recipesChannel()
        weatherBot()
    }

    // --- chats --------------------------------------------------------------

    val chats =
        listOf(
            Chat(id = SAVED, title = "Избранное", lastMessageText = null, lastEventTime = 0, isDialog = true),
            Chat(
                id = dialog(ALICE),
                title = "Алиса Демидова",
                lastMessageText = null,
                lastEventTime = 0,
                isDialog = true,
                favIndex = ago(d = 30),
                otherReadMark = ago(m = 1),
            ),
            Chat(
                id = dialog(BORIS),
                title = "Борис Петров",
                lastMessageText = null,
                lastEventTime = 0,
                isDialog = true,
                // Read up to before our last message: it shows a single tick.
                otherReadMark = ago(h = 1),
            ),
            Chat(id = dialog(VERA), title = "Вера Соколова", lastMessageText = null, lastEventTime = 0, isDialog = true, unreadCount = 2),
            Chat(
                id = dialog(GRISHA),
                title = "Григорий Лебедев",
                lastMessageText = null,
                lastEventTime = 0,
                isDialog = true,
                muted = true,
                otherReadMark = ago(d = 6),
            ),
            Chat(id = dialog(MOM), title = "Мама", lastMessageText = null, lastEventTime = 0, isDialog = true, unreadCount = 1),
            Chat(
                id = WORK,
                title = "Команда проекта «Маяк»",
                lastMessageText = null,
                lastEventTime = 0,
                avatarUrl = img("avatar_work.jpg"),
                unreadCount = 3,
                favIndex = ago(d = 30) + 1,
                ownerId = ME,
                adminIds = setOf(ME, ALICE),
                memberIds = setOf(ME, ALICE, BORIS, VERA, GRISHA),
                participantsCount = 5,
                canAddMembers = true,
                link = "https://max.ru/join/demo-mayak-team",
                description = "Рабочий чат команды: задачи, созвоны и релизы. Закреп обновляем каждый понедельник.",
                readMarks = mapOf(ALICE to ago(m = 50), BORIS to ago(m = 10), VERA to ago(m = 25), GRISHA to ago(h = 2)),
                otherReadMark = ago(m = 10),
            ),
            Chat(
                id = FAMILY,
                title = "Семья",
                lastMessageText = null,
                lastEventTime = 0,
                avatarUrl = img("avatar_family.jpg"),
                unreadCount = 5,
                muted = true,
                ownerId = MOM,
                adminIds = setOf(MOM),
                memberIds = setOf(ME, MOM, DASHA),
                participantsCount = 3,
                allCanPin = true,
                options = setOf("ALL_CAN_PIN_MESSAGE"),
                readMarks = mapOf(MOM to ago(h = 3), DASHA to ago(h = 4)),
                otherReadMark = ago(h = 3),
            ),
            Chat(
                id = BOOKS,
                title = "Книжный клуб",
                lastMessageText = null,
                lastEventTime = 0,
                ownerId = LIDA,
                adminIds = setOf(LIDA),
                memberIds = setOf(ME, LIDA, VERA, BORIS),
                participantsCount = 4,
                description = "Читаем по книге в месяц и встречаемся по средам.",
                readMarks = mapOf(LIDA to ago(d = 3), VERA to ago(d = 4), BORIS to ago(d = 4)),
                otherReadMark = ago(d = 3),
            ),
            Chat(
                id = NEWS,
                title = "Городской вестник",
                lastMessageText = null,
                lastEventTime = 0,
                avatarUrl = img("avatar_news.jpg"),
                unreadCount = 4,
                isChannel = true,
                official = true,
                ownerId = 500,
                participantsCount = 48_210,
                canWrite = false,
                link = "https://max.ru/gorodskoy_vestnik_demo",
                description = "Новости города, афиша выходных и полезные заметки. Выходит каждый день в 9:00.",
            ),
            Chat(
                id = TRAVEL,
                title = "Заметки путешественника",
                lastMessageText = null,
                lastEventTime = 0,
                avatarUrl = img("photo_mountain.jpg"),
                unreadCount = 12,
                muted = true,
                isChannel = true,
                ownerId = 501,
                participantsCount = 3_942,
                canWrite = false,
                link = "https://max.ru/travel_notes_demo",
                description = "Маршруты выходного дня и фотографии из поездок.",
            ),
            Chat(
                id = RECIPES,
                title = "Рецепты выходного дня",
                lastMessageText = null,
                lastEventTime = 0,
                avatarUrl = img("photo_shapes.jpg"),
                isChannel = true,
                ownerId = 502,
                participantsCount = 1_207,
                canWrite = false,
                description = "Простые блюда, которые успеешь приготовить до обеда.",
            ),
            Chat(
                id = dialog(BOT),
                title = "Погодный бот",
                lastMessageText = null,
                lastEventTime = 0,
                isDialog = true,
                isServiceChat = true,
            ),
        )

    // --- folders ------------------------------------------------------------

    val folders =
        listOf(
            ChatFolder(ChatFolder.ALL_ID, "Все"),
            ChatFolder("demo.work", "Работа", include = listOf(WORK, dialog(BORIS), dialog(VERA), BOOKS)),
            ChatFolder("demo.family", "Семья", include = listOf(FAMILY, dialog(MOM))),
            // Filter 2 = CHANNEL: every channel, no list kept by hand.
            ChatFolder("demo.news", "Новости", filters = JsonArray(listOf(JsonPrimitive(2)))),
        )

    // ------------------------------------------------------------------------
    // Conversations
    // ------------------------------------------------------------------------

    /** The showcase dialog: nearly every kind of message the chat screen can draw. */
    private fun aliceDialog() {
        val c = dialog(ALICE)
        val plan = msg(c, ALICE, ago(d = 1, h = 4), "Давай наконец сходим в поход к озеру, о котором я рассказывала?")
        val rules = "Главное: взять воду, тёплую куртку и фонарик. Код от домика у озера: 4815. Подробности маршрута по ссылке."
        val poll =
            msg(c, ALICE, ago(h = 2, m = 40)).copy(
                poll =
                    Poll(
                        pollId = 1,
                        title = "Во сколько выезжаем в субботу?",
                        settings = REVOTE,
                        answers = listOf(PollAnswer(0, "В 7:00"), PollAnswer(1, "В 8:00", votes = 1), PollAnswer(2, "В 9:00")),
                        total = 1,
                    ),
            )
        history[c] =
            listOf(
                msg(c, ALICE, ago(d = 1, h = 4, m = 5), "Привет! Ты в субботу свободен?"),
                msg(c, ME, ago(d = 1, h = 4, m = 3), "Привет! Да, вполне. Есть идея?"),
                plan,
                msg(c, ALICE, ago(d = 1, h = 4) + 1, "Вот фото с прошлого раза").copy(
                    media = listOf(photo("photo_mountain.jpg", 480, 640), photo("photo_sunset.jpg", 640, 480)),
                ),
                msg(c, ME, ago(d = 1, h = 3, m = 50), "Красота! Я за.").copy(reactions = listOf(Reaction("❤️", 1, mine = false))),
                msg(c, ME, ago(d = 1, h = 3, m = 49), "Сколько там идти от станции?").copy(replyTo = ReplyInfo(ALICE, plan.text, plan.id)),
                msg(c, ALICE, ago(d = 1, h = 3, m = 40), "Около 12 км. Я собрала маршрут в файл").copy(
                    files = listOf(FileAttach(7001, "Маршрут к озеру.pdf", 2_457_600)),
                ),
                msg(c, ALICE, ago(d = 1, h = 3, m = 38), rules).copy(
                    spans =
                        listOf(
                            rules.span("взять воду", "STRONG"),
                            rules.span("тёплую куртку", "EMPHASIZED"),
                            rules.span("4815", "MONOSPACED"),
                            rules.span("по ссылке", "LINK", "https://example.org/trail"),
                        ),
                    reactions = listOf(Reaction("👍", 1, mine = true)),
                ),
                msg(c, ME, ago(d = 1, h = 2), "Нашёл прогноз на выходные: https://example.com/weather").copy(
                    linkPreview =
                        LinkPreview(
                            url = "https://example.com/weather",
                            title = "Прогноз погоды на выходные",
                            description = "Суббота: солнечно, до +18, ветер слабый. Воскресенье: переменная облачность.",
                            imageUrl = img("photo_meadow.jpg"),
                        ),
                    reactions = listOf(Reaction("🔥", 1, mine = false)),
                ),
                msg(c, ALICE, ago(h = 3, m = 10)).copy(
                    voice = VoiceAttach(audioId = 8001, durationSeconds = 14, waveform = wave(3)),
                    reactions = listOf(Reaction("👍", 1, mine = true)),
                ),
                msg(c, ME, ago(h = 3), "Послушал, договорились. Беру палатку и горелку").copy(edited = true),
                msg(c, ALICE, ago(h = 2, m = 55), "Собираемся вот здесь в 7:30").copy(location = GeoPoint(55.83412, 37.62118)),
                msg(c, ALICE, ago(h = 2, m = 50), "И вот контакт Гриши, он поедет с нами"),
                msg(c, ALICE, ago(h = 2, m = 49)).copy(contact = ContactCard(GRISHA, "Григорий Лебедев", "70000000005")),
                poll,
                msg(c, ME, ago(h = 2, m = 30)).copy(stickerUrl = img("sticker_smile.png")),
                msg(c, ME, ago(m = 2), "До субботы! Напишу, как соберу рюкзак"),
            )
    }

    private fun borisDialog() {
        val c = dialog(BORIS)
        history[c] =
            listOf(
                msg(c, BORIS, ago(d = 2, h = 6), "Привет! Как продвигается экран настроек?"),
                msg(c, ME, ago(d = 2, h = 5), "Почти готов, осталось поправить отступы"),
                msg(c, BORIS, ago(h = 1, m = 30), "Посмотри новые иконки, пожалуйста").copy(
                    media = listOf(photo("photo_shapes.jpg", 480, 640)),
                ),
                msg(c, ME, ago(m = 50), "Скинул тебе макеты, посмотри, когда будет время").copy(
                    files = listOf(FileAttach(7002, "Макеты v3.fig", 18_350_080)),
                ),
            )
    }

    private fun veraDialog() {
        val c = dialog(VERA)
        history[c] =
            listOf(
                msg(c, ME, ago(d = 1, h = 9), "Спасибо за отчёт по тестам!"),
                msg(c, VERA, ago(m = 12), "Ты завтра в офисе?"),
                msg(c, VERA, ago(m = 11), "Хочу показать новый прототип"),
            )
    }

    private fun grishaDialog() {
        val c = dialog(GRISHA)
        history[c] =
            listOf(
                msg(c, ME, ago(d = 6, h = 3), "Отправил тебе ссылку на репозиторий"),
                msg(c, GRISHA, ago(d = 6, h = 2), "Спасибо за помощь!"),
            )
    }

    private fun momDialog() {
        val c = dialog(MOM)
        history[c] =
            listOf(
                msg(c, MOM, ago(d = 1, h = 7), "Ты поел?"),
                msg(c, ME, ago(d = 1, h = 6), "Да, всё хорошо"),
                msg(c, MOM, ago(d = 1, h = 1), "Позвони, когда освободишься"),
            )
    }

    private fun savedMessages() {
        val c = SAVED
        val list = "Список дел: купить батарейки, забрать посылку, записаться к врачу"
        history[c] =
            listOf(
                msg(c, ME, ago(d = 5), list).copy(spans = listOf(list.span("Список дел:", "STRONG"))),
                msg(c, ME, ago(d = 4), "Билеты на поезд").copy(files = listOf(FileAttach(7003, "Билеты.pdf", 184_320))),
                msg(c, ME, ago(d = 3), "Идея для обоев").copy(media = listOf(photo("photo_abstract.jpg", 640, 480))),
                msg(c, ME, ago(d = 2), "https://example.org/reading-list").copy(
                    linkPreview =
                        LinkPreview(
                            "https://example.org/reading-list",
                            "Что почитать осенью",
                            "Двенадцать книг для долгих вечеров",
                            img("photo_desk.jpg"),
                        ),
                ),
            )
    }

    /** The showcase group: named senders, a pin, service events, a poll and read marks. */
    private fun workGroup() {
        val c = WORK
        val plan = "План на неделю:\n1. Дизайн экрана настроек\n2. Тесты для папок\n3. Релиз в пятницу"
        val pinnedPlan = msg(c, ALICE, ago(h = 3), plan).copy(spans = listOf(plan.span("План на неделю:", "STRONG")))
        val build =
            msg(c, ME, ago(m = 55), "Я подготовлю сборку к четвергу").copy(
                reactions = listOf(Reaction("🔥", 2, mine = false), Reaction("👍", 1, mine = false)),
            )
        history[c] =
            listOf(
                msg(c, ME, ago(d = 6, h = 2)).copy(service = ServiceEvent("new", ME, message = "Вы создали группу")),
                msg(c, ME, ago(d = 6, h = 2) + 1).copy(
                    service = ServiceEvent("add", ME, listOf(ALICE, BORIS, VERA), message = "Вы добавили участников"),
                ),
                msg(c, ALICE, ago(d = 5), "Всем привет! Здесь обсуждаем релиз 2.0"),
                msg(c, BORIS, ago(d = 5) + 60_000, "Привет! Макеты будут к среде"),
                msg(c, ALICE, ago(d = 1, h = 5)).copy(
                    service = ServiceEvent("add", ALICE, listOf(GRISHA), message = "Алиса добавила Григория"),
                ),
                msg(c, GRISHA, ago(d = 1, h = 4), "Спасибо, что добавили! Готов помочь с тестированием"),
                pinnedPlan,
                msg(c, BORIS, ago(h = 2), "Мой рабочий стол сегодня").copy(
                    media = listOf(photo("photo_desk.jpg", 640, 480)),
                    reactions = listOf(Reaction("👍", 3, mine = true), Reaction("😂", 1, mine = false)),
                ),
                msg(c, VERA, ago(h = 1, m = 30)).copy(
                    poll =
                        Poll(
                            pollId = 2,
                            title = "Когда проводим ретро?",
                            settings = REVOTE,
                            answers =
                                listOf(
                                    PollAnswer(0, "Четверг, 16:00", votes = 2),
                                    PollAnswer(1, "Пятница, 11:00", votes = 1),
                                    PollAnswer(2, "Пятница, 15:00"),
                                ),
                            total = 3,
                        ),
                ),
                build,
                msg(c, BORIS, ago(m = 40), "Отлично, тогда в четверг покажем демо").copy(
                    replyTo = ReplyInfo(ME, build.text, build.id),
                ),
                msg(c, VERA, ago(m = 25), "Нашла баг в поиске, завела задачу").copy(
                    files = listOf(FileAttach(7004, "Отчёт об ошибке.txt", 4_096)),
                ),
                msg(c, BORIS, ago(m = 10), "Посмотрю после обеда"),
            )
        pinned[c] = pinnedPlan.id!!
    }

    private fun familyGroup() {
        val c = FAMILY
        history[c] =
            listOf(
                msg(c, MOM, ago(d = 1, h = 2), "Дети, в воскресенье обед у нас"),
                msg(c, ME, ago(d = 1, h = 1), "Буду!"),
                msg(c, DASHA, ago(h = 4), "Фото с дачи").copy(
                    media = listOf(photo("photo_meadow.jpg", 640, 480), photo("photo_sunset.jpg", 640, 480)),
                    reactions = listOf(Reaction("❤️", 2, mine = false)),
                ),
                msg(c, DASHA, ago(h = 4) + 1, "Яблоки в этом году просто огромные"),
                msg(c, MOM, ago(h = 3, m = 30), "Какая красота"),
                msg(c, MOM, ago(h = 3, m = 20), "Даша, привези, пожалуйста, банки для варенья"),
                msg(c, MOM, ago(h = 3), "И не забудьте пирог"),
            )
    }

    private fun bookClub() {
        val c = BOOKS
        history[c] =
            listOf(
                msg(c, LIDA, ago(d = 9), "В этом месяце читаем «Сад на краю света»"),
                msg(c, VERA, ago(d = 8), "Уже начала, очень атмосферно"),
                msg(c, BORIS, ago(d = 5), "А я только купил"),
                msg(c, ME, ago(d = 4), "Я дочитал, обсудим в среду"),
            )
    }

    /** The showcase channel: long formatted posts, views, reactions and a pinned post. */
    private fun newsChannel() {
        val c = NEWS
        val park =
            "Парк у реки открылся после реконструкции\n\n" +
                "В парке появились новые дорожки, освещение вдоль набережной и детская площадка с зоной для настольных игр. " +
                "Летом на открытой сцене будут проходить концерты, а зимой зальют каток.\n\n" +
                "«Мы хотели, чтобы сюда было приятно приходить в любое время года», рассказали архитекторы проекта."
        val afisha =
            "Афиша выходных\n\n" +
                "Суббота: ярмарка ремёсел на площади, лекция о звёздах в планетарии, ночной кинопоказ под открытым небом.\n" +
                "Воскресенье: забег на 5 км, мастер-класс по керамике, концерт камерного оркестра.\n\n" +
                "Полная афиша на сайте."
        val pinnedAfisha =
            post(c, ago(d = 2, h = 1), afisha, views = 9_870).copy(
                spans =
                    listOf(
                        afisha.span("Афиша выходных", "STRONG"),
                        afisha.span("Суббота:", "EMPHASIZED"),
                        afisha.span("Воскресенье:", "EMPHASIZED"),
                        afisha.span("на сайте", "LINK", "https://example.com/afisha"),
                    ),
                reactions = listOf(Reaction("👍", 214, mine = false), Reaction("🎉", 57, mine = false)),
            )
        val weather = "Погода на сегодня\n\nДнём до +16, без осадков. Вечером ветер усилится до 9 м/с, возьмите куртку."
        history[c] =
            listOf(
                post(c, ago(d = 3, h = 2), park, views = 12_430).copy(
                    media = listOf(photo("photo_meadow.jpg", 640, 480)),
                    spans =
                        listOf(
                            park.span("Парк у реки открылся после реконструкции", "STRONG"),
                            park.span("«Мы хотели, чтобы сюда было приятно приходить в любое время года»", "QUOTE"),
                        ),
                    reactions = listOf(Reaction("❤️", 320, mine = false), Reaction("👍", 211, mine = false)),
                ),
                pinnedAfisha,
                post(c, ago(d = 1, h = 6), "Выставка современного искусства продлится до конца месяца", views = 7_405).copy(
                    media =
                        listOf(
                            photo("photo_abstract.jpg", 640, 480),
                            MediaAttach(MediaType.VIDEO, img("photo_shapes.jpg"), 480, 640, videoId = 9001, durationSec = 42),
                        ),
                    reactions = listOf(Reaction("😮", 48, mine = false)),
                ),
                post(c, ago(h = 2), weather, views = 3_126).copy(
                    spans = listOf(weather.span("Погода на сегодня", "STRONG"), weather.span("до +16", "MONOSPACED")),
                    reactions = listOf(Reaction("🔥", 54, mine = true), Reaction("👍", 31, mine = false)),
                ),
                post(c, ago(m = 30), views = 1_204).copy(
                    poll =
                        Poll(
                            pollId = 3,
                            title = "На чём вы добираетесь до работы?",
                            settings = ANONYMOUS,
                            answers =
                                listOf(
                                    PollAnswer(0, "Метро", votes = 412),
                                    PollAnswer(1, "Автобус или трамвай", votes = 268),
                                    PollAnswer(2, "Велосипед или самокат", votes = 97),
                                    PollAnswer(3, "Пешком", votes = 153),
                                ),
                            total = 930,
                        ),
                ),
            )
        pinned[c] = pinnedAfisha.id!!
    }

    private fun travelChannel() {
        val c = TRAVEL
        history[c] =
            listOf(
                post(c, ago(d = 2), "Три озера за один день: короткий маршрут для начинающих", views = 2_310).copy(
                    media = listOf(photo("photo_mountain.jpg", 480, 640)),
                ),
                post(c, ago(h = 5), "Закат на перевале. Ради такого стоит встать в четыре утра", views = 1_874).copy(
                    media = listOf(photo("photo_sunset.jpg", 640, 480)),
                    reactions = listOf(Reaction("❤️", 96, mine = false)),
                ),
            )
    }

    private fun recipesChannel() {
        val c = RECIPES
        val soup = "Тыквенный суп за 30 минут\n\nТыква, морковь, лук, сливки и щепотка мускатного ореха. Подавайте с гренками."
        history[c] =
            listOf(
                post(c, ago(d = 9), soup, views = 860).copy(spans = listOf(soup.span("Тыквенный суп за 30 минут", "STRONG"))),
            )
    }

    /** A bot chat whose last message carries link buttons. */
    private fun weatherBot() {
        val c = dialog(BOT)
        history[c] =
            listOf(
                msg(c, ME, ago(d = 7), "/start"),
                msg(c, BOT, ago(d = 7) + 1_000, "Здравствуйте! Я присылаю прогноз погоды. Напишите название города."),
                msg(c, ME, ago(d = 1, h = 8), "Озёрск"),
                msg(c, BOT, ago(d = 1, h = 8) + 1_000, "Озёрск, суббота: солнечно, днём +18, ночью +7. Ветер 3 м/с.").copy(
                    buttons =
                        listOf(
                            listOf(BotButton("Подробный прогноз", "https://example.com/forecast", "LINK")),
                            listOf(
                                BotButton("Карта осадков", "https://example.com/radar", "LINK"),
                                BotButton("О боте", "https://example.com/bot", "LINK"),
                            ),
                        ),
                ),
            )
    }

    // --- builders -----------------------------------------------------------

    private fun ago(
        m: Int = 0,
        h: Int = 0,
        d: Int = 0,
    ): Long = now - ((d * 24L + h) * 60 + m) * 60_000

    private fun msg(
        chat: Long,
        from: Long,
        at: Long,
        text: String = "",
    ) = Message(
        id = (seq++).toString(),
        cid = null,
        chatId = chat,
        senderId = from,
        text = text,
        time = at,
        // Ours start as sent; the chat's read mark turns them read.
        status = if (from == ME) MessageStatus.SENT else MessageStatus.UNKNOWN,
    )

    /** A channel post: written by the channel itself (its owner). */
    private fun post(
        chat: Long,
        at: Long,
        text: String = "",
        views: Int,
    ) = msg(chat, OWNERS.getValue(chat), at, text).copy(views = views)

    private fun photo(
        name: String,
        width: Int,
        height: Int,
    ) = MediaAttach(MediaType.PHOTO, img(name), width, height)

    private fun person(
        id: Long,
        first: String,
        last: String?,
        avatar: String? = null,
        description: String? = null,
    ) = UserInfo(
        id = id,
        name = listOfNotNull(first, last).joinToString(" "),
        avatarUrl = avatar?.let(::img),
        firstName = first,
        lastName = last,
        description = description,
        phone = "7000000000$id".takeLast(11),
    )

    /** A [type] run over the first [part] of this text (so offsets never drift from the text). */
    private fun String.span(
        part: String,
        type: String,
        url: String? = null,
    ): TextSpan {
        val at = indexOf(part)
        require(at >= 0) { "«$part» is not in the text" }
        return TextSpan(type, at, part.length, url)
    }

    companion object {
        const val ME = 1L
        const val ALICE = 2L
        const val BORIS = 3L
        const val VERA = 4L
        const val GRISHA = 5L
        const val DASHA = 6L
        const val MOM = 7L
        const val LIDA = 8L
        const val EVGENY = 9L
        const val BOT = 10L

        const val SAVED = 0L
        const val WORK = -1001L
        const val FAMILY = -1002L
        const val BOOKS = -1003L
        const val NEWS = -2001L
        const val TRAVEL = -2002L
        const val RECIPES = -2003L

        const val STICKER_SMILE = 101L
        const val STICKER_HEART = 102L
        const val STICKER_OK = 103L

        // Poll settings bits (see Poll).
        private const val ANONYMOUS = 1
        private const val REVOTE = 4

        private val OWNERS = mapOf(NEWS to 500L, TRAVEL to 501L, RECIPES to 502L)

        /** A 1:1 chat's id: our id xor theirs, as the server does it. */
        fun dialog(userId: Long): Long = ME xor userId

        /**
         * A bundled demo image, as a URI Coil loads on Android (asset) and desktop (jar or file). Blank (no
         * image, initials instead) where resources can't be reached, as in Android unit tests with no context;
         * DemoImagesTest checks that every one resolves and loads on the JVM.
         */
        fun img(name: String): String = runCatching { Res.getUri("files/demo/$name") }.getOrDefault("")

        /** A voice-message waveform that looks like speech; [seed] varies it. */
        fun wave(seed: Int): List<Float> = List(48) { i -> 0.15f + 0.85f * abs(sin((i + seed) * 0.55f) * cos((i + seed) * 0.21f)) }
    }
}
