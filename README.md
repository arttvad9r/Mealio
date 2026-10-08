Русский | [English](README.en.md)

<div align="center">

# Mealio

**Нативный Android-клиент для self-hosted [Mealie](https://mealie.io).**

Быстрый, минималистичный — для повседневного планирования питания, рецептов и
списка покупок.

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![CI](https://github.com/arttvad9r/Mealio/actions/workflows/android-ci.yml/badge.svg)](https://github.com/arttvad9r/Mealio/actions/workflows/android-ci.yml)
[![Release](https://img.shields.io/github/v/release/arttvad9r/Mealio?display_name=tag&sort=semver)](https://github.com/arttvad9r/Mealio/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**[Скачать последнюю версию](https://github.com/arttvad9r/Mealio/releases/latest)** ·
[Все релизы](https://github.com/arttvad9r/Mealio/releases)

</div>

Mealio работает напрямую с **вашим сервером Mealie** по HTTP/JSON — без облака,
без аккаунта, без промежуточного слоя. Источник истины — ваш сервер Mealie;
приложение — лишь быстрый нативный интерфейс к нему.

Коротко: рецепты, список покупок и планирование питания на день в простом
Android-интерфейсе.

> **Mealio требует собственный запущенный сервер Mealie.** Если его ещё нет,
> начните с [mealie.io](https://mealie.io) — там рассказано, как поднять его
> самостоятельно.

> **Язык интерфейса.** Сейчас UI переведён только на русский. Основная
> документация проекта — на русском (эта страница), английская версия — в
> [README.en.md](README.en.md). Английский интерфейс приложения планируется
> для V1.3.

## Screenshots

<div align="center">
<table>
  <tr>
    <td align="center"><img src="docs/screenshots/today.jpg" width="220" alt="Сегодня"></td>
    <td align="center"><img src="docs/screenshots/recipes.jpg" width="220" alt="Рецепты"></td>
    <td align="center"><img src="docs/screenshots/recipe-detail.jpg" width="220" alt="Рецепт"></td>
    <td align="center"><img src="docs/screenshots/shopping.jpg" width="220" alt="Покупки"></td>
  </tr>
  <tr>
    <td align="center"><sub>Сегодня</sub></td>
    <td align="center"><sub>Рецепты</sub></td>
    <td align="center"><sub>Рецепт</sub></td>
    <td align="center"><sub>Покупки</sub></td>
  </tr>
</table>
</div>

## Возможности

- Нативный Android-интерфейс на Jetpack Compose и Material 3
- Подключение к своему серверу Mealie (URL + long-lived API-токен)
- Просмотр и поиск рецептов
- Фильтр рецептов по категориям
- Карточка рецепта: ингредиенты, шаги и пищевая ценность
- Масштабирование порций с пересчётом пищевой ценности
- Списки покупок — просмотр, отметка и снятие отметки
- Добавление ингредиентов рецепта в список покупок
- Раздел **Сегодня**: соберите свой день из собственных рецептов
- Итог по калориям за день и разбивка по Б/Ж/У
- Слоты приёмов пищи (завтрак, основное, гарнир, овощи, перекус, дополнение) с настройкой порций
- Светлая / тёмная / системная тема
- Безопасное хранение API-токена в Android Keystore (AES/GCM)
- Расчёт на использование self-hosted дома: LAN и Tailscale

Mealio намеренно **не** является трекером калорий и не клоном FatSecret: здесь
нет отдельной базы продуктов, сканера штрихкодов, ручного ввода еды и
рекомендаций. Это тонкий клиент к Mealie.

## Сегодня

**Сегодня** — то, что выходит чуть за рамки обычного просмотрщика Mealie: это
конструктор питания на день, который переиспользует ваши собственные рецепты:

- Выбор рецептов для **завтрака, основного блюда, гарнира, овощей, перекуса и
  дополнительного блюда**.
- Настройка **количества порций** для каждой позиции.
- **Калории и Б/Ж/У** за день считаются из пищевой ценности, уже хранящейся в
  ваших рецептах Mealie.
- Текущий день **хранится локально** на устройстве и переживает перезапуск.
- День **логически сбрасывается при смене локальной даты** — новый день
  начинается с чистого листа.

## Требования

- **Android 8.0+** (API 26)
- Запущенный **сервер Mealie**, API которого доступен с телефона
- **Long-lived API-токен** Mealie

## Установка

1. Скачайте APK из [последнего релиза](https://github.com/arttvad9r/Mealio/releases/latest).
2. Установите его на устройство (возможно, понадобится разрешить установку из
   неизвестных источников).
3. Откройте Mealio.
4. Введите URL вашего сервера Mealie.
5. Введите ваш long-lived API-токен.

### Как получить API-токен

В Mealie: **профиль → API tokens → создать long-lived токен**. Вставьте его на
экране подключения Mealio.

### Сеть

Mealio хорошо подходит для доступа к **домашнему серверу Mealie через приватную
сеть Tailscale** — наружу порты не открываются, а приложение просто обращается к
серверу по его Tailscale-адресу.

> **Про безопасность.** Обычный `http://` допустим **только внутри доверенной
> приватной сети, где трафик уже шифруется** — домашняя LAN или сеть
> **Tailscale**, где всё идёт внутри шифрованного туннеля. Для сервера,
> доступного из публичной или недоверенной сети, используйте **`https://`**.

## Статус проекта

Mealio — **независимый community-проект**, он **не связан официально с проектом
Mealie и не одобрен** его командой.

- **Текущее состояние:** рабочий персональный Android-клиент, в активной
  разработке.
- **Основной язык интерфейса:** русский (английский запланирован для V1.3).

Это не production-grade и не enterprise-стабильное ПО — это небольшое
сфокусированное приложение, которое хорошо работает для собственного
self-hosted сценария автора.

## Разработка

Построено на:

- Kotlin
- Jetpack Compose
- Material 3
- Retrofit / OkHttp
- Kotlin Serialization
- Coil
- Coroutines / Flow
- Android Keystore

Основные команды (Gradle wrapper):

```bash
./gradlew assembleDebug        # собрать debug APK
./gradlew testDebugUnitTest    # юнит-тесты
./gradlew lintDebug            # lint
```

Нужны **JDK 17** и **Android SDK** (platform 36, build-tools 36). Заметки для
контрибьюторов — об особенностях API и архитектуре — в [`AGENTS.md`](AGENTS.md);
технические решения зафиксированы как ADR в [`docs/adr/`](docs/adr/).

## Roadmap

Ближайшее направление — **V1.3**:

- локализация интерфейса приложения на английский;
- настраиваемая дневная цель по калориям вместо фиксированных 2300 ккал.

Возможные дальнейшие направления — без обещаний по срокам, и только то, что
действительно подходит проекту:

- улучшение сценария работы с разделом «Сегодня»;
- улучшение подачи и изображений рецептов;
- дополнительный UX для списка покупок;
- более широкое покрытие API Mealie.

## Участие

Issues и pull requests приветствуются — см. [CONTRIBUTING.md](CONTRIBUTING.md)
(на английском). Для сообщений о безопасности — [SECURITY.md](SECURITY.md).

## Лицензия

MIT — см. [LICENSE](LICENSE).
