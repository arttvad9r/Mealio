# 0009 — Cook Mode: доставка таймера через setAlarmClock

Статус: Accepted, 2026-10-09 (заменяет 0008 в части политики планирования alarm)

## Контекст

Кухонный таймер обязан срабатывать в назначенный момент: пользователь ставит его
на конкретное время и ждёт звук/вибрацию именно тогда, а не «примерно». Это
требование к продукту, а не пожелание.

0008 зафиксировал: inexact-fallback удалён, доставка — через
`AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, …)`, запуск только при выданном
`SCHEDULE_EXACT_ALARM`. На эмуляторе и в instrumentation этот путь выглядел
корректным. Но на реальном OnePlus/OxygenOS он проблему не решил.

## Измерения на реальном устройстве

Предыдущий кандидат, `setExactAndAllowWhileIdle`, на физическом OnePlus/OxygenOS:

- `exactAllowReason=permission`, `origWhen` правильный — то есть special access
  реально выдан, а время рассчитано верно;
- но в `dumpsys alarm`: `window=+45s`, `maxWhenElapsed=trigger+45s`.

То есть OEM регистрировал точный alarm с **45-секундным окном доставки**. Фактически
сигнал приходил примерно через 21–22 с после запрошенного времени (визуально
наблюдались и более длинные задержки). Проблема не в канале, не в
`POST_NOTIFICATIONS`, не в countdown UI и не в расчёте trigger — окно навешивал
сам OEM.

Тот же физический OnePlus с `setAlarmClock(AlarmClockInfo(triggerAt, showIntent),
operation)`:

- `type=RTC_WAKEUP`, `origWhen=triggerTime` — правильные;
- `windowLength=0`, `whenElapsed == maxWhenElapsed` — окна нет;
- alarm присутствует как alarm clock, `showIntent` присутствует;
- фактическое срабатывание — практически в trigger time (проверка через
  `dumpsys` в 00:59:54 показала wakeup ~в 00:59:46 при trigger 00:59:46.574).

`setAlarmClock` — самый сильный контракт доставки в стандартном AlarmManager:
система выходит из Doze незадолго до такого alarm и не трактует его как
откладываемый фоновый alarm.

## Решение

- **Доставка Cook Timer — `AlarmManager.setAlarmClock(AlarmClockInfo(triggerAt,
  showIntent), operation)`.** `triggerAt` — тот же абсолютный wall-clock
  (`wallTriggerAt(deadlineMillis, …)`), что и раньше.
- Сохраняются: тот же timer deadline, уникальный `PendingIntent` на каждый timer
  id (request code + data URI), тот же `CookTimerReceiver` и notification-путь,
  канал `cook-timers-v2` (`IMPORTANCE_HIGH`, alarm-звук `TYPE_ALARM`, непустой
  vibration-паттерн), несколько независимых таймеров, `cancel`, restart/replace
  таймера того же id, персистентность через `SavedStateHandle`.
- `SCHEDULE_EXACT_ALARM` по-прежнему обязателен и запрашивается только при первой
  попытке запуска таймера. `USE_EXACT_ALARM` намеренно НЕ добавлен — он для
  реальных clock/alarm приложений и не оправдан для кухонного таймера.
- `showIntent` минимален: shared `PendingIntent.getActivity` на `MainActivity`
  (открыть Mealio по тапу на «следующий будильник»). Навигация не усложняется.

## Отклонённые альтернативы

- **Оставить `setExactAndAllowWhileIdle`** — на OxygenOS он получал OEM-окно 45 с и
  опаздывал; измерено на устройстве.
- **Foreground service + wake lock** — сознательно отвергнуто как ненужная
  сложность: пока `setAlarmClock` даёт требуемое поведение, сервис и wakelock не
  нужны. Понадобятся только если на каком-то OEM и `setAlarmClock` начнёт
  опаздывать (тогда это отдельное решение, а не сейчас).
- **`USE_EXACT_ALARM`** — обход политики Google Play, не для этого класса
  приложения.
- **Full-screen Activity / MediaPlayer / WorkManager** — ничего из этого не
  требуется: сигнал полностью обеспечивают alarm clock + канал уведомления.

## Следствия

- Точность таймера подтверждена на реальном OxygenOS: `windowLength=0`,
  `whenElapsed == maxWhenElapsed`, сигнал в назначенный момент.
- **Известный компромисс:** Android (и OEM) может показывать активный кухонный
  таймер как «следующий системный будильник» (статусбар/локскрин) и вести к Mealio
  по тапу. Для кухонного таймера это приемлемо.
- Alarm-clock путь проверяется instrumentation-тестом через публичный
  `getNextAlarmClock()` (alarm, поставленный `setAlarmClock`, только он туда и
  попадает), плюс сохранены тесты на уникальность PendingIntent, cancel,
  reschedule и на канал/Receiver.
