# Mealio

Нативный Android-клиент (Kotlin + Jetpack Compose, Material 3) для сервера
[Mealie](https://mealie.io). Питание, рецепты и списки покупок. Визуальный язык
повторяет проект Rutina.

## Зачем

Mealie даёт backend и API; Mealio — это только UI. Собственный веб-клиент Mealie
неудобен на телефоне, а сторонние клиенты тянут за собой лишние слои.

## Архитектура (кратко)

```
ui/         Compose-экраны + ViewModel'и (ничего не знают про Retrofit DTO)
domain/     чистые модели (Recipe, ShoppingItem, ServerAccount) и форматтеры
data/       Retrofit API, DTO, мапперы DTO→domain, репозитории,
            Keystore-хранилище токена, настройки
```

- `data/AppContainer.kt` — ручной DI (без Hilt/Koin).
- Никаких `UseCase`-обёрток: экраны зовут репозитории напрямую.
- Странности Mealie API изолированы в `data/remote` и `data/mapper`.

## Сборка

Тулчейн в домашней папке (нет sudo): JDK17 `~/opt/jdk17`,
Gradle `~/opt/gradle-8.13`, Android SDK `~/Android/Sdk`.

```bash
export JAVA_HOME=~/opt/jdk17
export ANDROID_HOME=~/Android/Sdk
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # release APK (R8 + resource shrink)
./gradlew :app:testDebugUnitTest      # юнит-тесты
./gradlew :app:lintDebug              # lint
```

Артефакты: `app/build/outputs/apk/{debug,release}/`.

Release-подпись берётся из `keystore.properties` (вне git) и `keystore/*.jks`.
Без них release собирается **без подписи** (debug-ключ для release не
используется); debug APK подписывается автоматически и собирается всегда.

## Конвенции и подводные камни

- **Slug vs UUID в Mealie**: чтение/обновление рецепта и загрузка картинки — по
  `slug`; медиа, ассеты, ссылки на food — по `uuid`. Перепутаешь → 422.
- **Токен**: `/api/auth/token` принимает `form-urlencoded`, всё остальное — JSON.
- **Список рецептов**: `perPage=-1` = всё; ответное поле `per_page`.
- **Добавление рецепта в список**: тело — **массив**, даже для одного рецепта.
- Времена — ISO-8601 duration (`PT30M`), nullable; значения nutrition — строки.
- **Cleartext HTTP**: self-hosted Mealie часто на голом `http://` в LAN; Android
  9+ блокирует cleartext → `network_security_config.xml`.
- Токен хранится в Android Keystore (AES/GCM), не в открытом виде.
- Нутриенты есть только в detail рецепта, в списке их нет.

## Статус

Готово: подключение к серверу (URL + long-lived токен) с валидацией и инфо о
сервере; список рецептов с поиском и чипами категорий; карточка рецепта с
масштабированием порций; списки покупок с отметкой купленного; добавление
ингредиентов рецепта в список; настройки + disconnect; светлая/тёмная тема;
русский UI. 31 юнит-тест (зелёные), lint 0 ошибок, debug + release APK
собираются.

V1.1 (полировка): заголовки экранов вместо «Mealio»; компактное поле поиска;
локальные единицы и русский десятичный разделитель в пищевой ценности
(`NutritionFormatter`); кнопка «В список покупок» — тональная с иконкой;
компактный блок «Подключение» в настройках; выравнивание quantity в покупках.

Дальше: см. `docs/ROADMAP.md`. Не сделано: smoke test на реальном устройстве
(нет adb-устройства), публикация в GitHub (нет авторизованного SSH-ключа).
