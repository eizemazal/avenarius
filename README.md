# Авенариус (Avenarius)

Минималистичный неофициальный клиент мессенджера **Max**, написанный на
**Kotlin Multiplatform + Compose Multiplatform**. Android-приложение и
десктоп-приложение используют один и тот же код: и сетевой протокол, и интерфейс.

> 🤖 **Этот проект (включая весь код и эту документацию) полностью написан
> нейросетью** в учебных целях — чтобы автор репозитория осваивал разработку под
> Android.

> ⚠️ Сервер Max может распознать сторонний клиент. Используйте на свой страх и риск.

## Что умеет

- Вход по номеру телефона + SMS-код (и облачный пароль, если включена 2FA)
- Список чатов
- Открыть чат, прочитать недавнюю историю, отправлять и получать сообщения в реальном времени

Намеренно ничего больше: ни файлов, ни звонков, ни шифрования, ни управления группами.

## Как устроен код (и почему его легко переиспользовать)

Всё платформонезависимое лежит в **`composeApp/src/commonMain`** и компилируется
без изменений и в Android-APK, и в десктоп-приложение:

```
composeApp/src/
├── commonMain/        ← общий код для Android И десктопа
│   └── kotlin/com/avenarius/app/
│       ├── net/MaxClient.kt        ← высокоуровневый протокол (вход, sync, сообщения)
│       ├── net/MobileTransport.kt  ← бинарный кадр поверх TLS, нумерация seq, ping
│       ├── net/MsgPack.kt          ← самописный кодек MessagePack
│       ├── net/Lz4.kt              ← самописный распаковщик LZ4 (block-формат)
│       ├── net/TlsSocket.kt        ← expect: «сырой» TLS-сокет
│       ├── model/Models.kt         ← Account / Chat / Message
│       ├── data/AppStorage.kt      ← хранение токена и идентификаторов устройства
│       └── ui/                     ← AppViewModel + интерфейс на Compose (App.kt)
├── androidMain/       ← только Android
│   └── kotlin/.../   MainActivity, AndroidStorage, TlsSocket (SSLSocket), манифест, иконка
└── desktopMain/       ← только десктоп («будущий десктоп-клиент»)
    └── kotlin/.../   Main.kt, DesktopStorage, TlsSocket (SSLSocket), Probe.kt (диагностика)
```

Платформенных кусков мало: TLS-сокет, хранилище «ключ-значение», часы и точка
входа. Чтобы развивать десктоп-клиент дальше, в основном дописывают `commonMain`,
и фичу получают оба приложения.

### Краткое описание протокола

Вход по телефону и SMS доступен только в **мобильном** протоколе Max (веб-точка
входа предлагает только QR-код), поэтому Авенариус говорит на мобильном протоколе:

- «Сырое» **TLS**-соединение с `api.oneme.ru:443` (не HTTP/WebSocket) — `TlsSocket`.
- Каждый кадр = **10-байтный заголовок** (big-endian) + полезная нагрузка:
  `[ver:u8][cmd:u16][seq:u8][opcode:u16][packedLen:u32]`, где старший байт
  `packedLen` — флаг сжатия LZ4, а младшие 24 бита — длина.
- Нагрузка кодируется в **MessagePack** (`MsgPack.kt`); ответы сервера могут быть
  сжаты **LZ4** (`Lz4.kt`). Оба кодека написаны вручную в `commonMain` — без
  внешних зависимостей.
- У запросов растущий `seq`; сервер возвращает его в ответе. Пинг (opcode 1)
  каждые 30 с держит соединение живым.

Используемые опкоды:

| Опкод | Назначение         |
|------:|--------------------|
| 6     | рукопожатие (user-agent как у ANDROID) |
| 17    | начать вход (запросить SMS) |
| 18    | проверить SMS-код  |
| 115   | проверить облачный пароль (2FA) |
| 19    | синхронизация (чаты/профиль) |
| 49    | история сообщений  |
| 64    | отправить сообщение |
| 128   | входящее сообщение (push от сервера) |

## Сборка

### Что нужно
- JDK 17
- Android SDK (укажите `sdk.dir` в `local.properties` или переменную `ANDROID_HOME`)

### Android APK (то, что ставится на телефон)
```sh
./gradlew :composeApp:assembleDebug
# → composeApp/build/outputs/apk/debug/composeApp-debug.apk
```
Скопируйте `.apk` на телефон и установите (разрешив установку из неизвестных
источников). Debug-APK автоматически подписан отладочным ключом, поэтому ставится
без дополнительной настройки.

### Десктоп-приложение
```sh
./gradlew :composeApp:run                                  # запустить
./gradlew :composeApp:packageDistributionForCurrentOS      # собрать нативный пакет
```

## CI/CD (GitHub Actions)

`.github/workflows/build.yml` собирает debug-APK на каждый push/PR и выкладывает
его артефактом (**Actions → запуск → Artifacts → `avenarius-debug-apk`**).
Можно запустить вручную через **workflow_dispatch**.

### Подписанные release-сборки (опционально)
Добавьте секреты репозитория, и workflow дополнительно соберёт подписанный
release-APK:

- `KEYSTORE_BASE64` — keystore в base64 (`base64 -i release.jks`)
- `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

Создать keystore:
```sh
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias avenarius
```

## Источники

Протокол восстановлен по клиентам [maxplus](https://github.com/me0wkie/maxplus),
[rumax](https://github.com/me0wkie/rumax) и [PyMax](https://github.com/noxzion/PyMax).
