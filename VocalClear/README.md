# VocalClear - Приложение для разделения и нарезки вокала

VocalClear — это профессиональное Android-приложение для разделения аудио дорожек на вокал и инструментальную часть с возможностью последующей нарезки вокала на отдельные секции.

## Возможности

### Основные функции
- **Разделение вокала и инструментов**: Использует алгоритмы центрального канала для разделения аудио на вокальную и инструментальную части
- **Два режима обработки**: 
  - **Офлайн**: Локальная обработка на устройстве без подключения к интернету
  - **Онлайн**: Серверная обработка через API для более качественного разделения
- **Редактор вокальных секций**: После извлечения вокала вы можете нарезать его на отдельные фрагменты

### Функции редактора секций
- **Визуализация waveform**: Графическое отображение аудио дорожки
- **Создание секций**: Добавление вокальных фрагментов с указанием времени начала и конца
- **Автоопределение**: Автоматическое определение вокальных секций на основе анализа тишины
- **Редактирование**: Изменение границ и названий существующих секций
- **Экспорт**: Выгрузка отдельных секций или всех фрагментов в ZIP-архиве

### Технические характеристики
- **Поддержка форматов**: MP3, WAV, FLAC, OGG, M4A, AAC
- **Минимальная версия Android**: API 24 (Android 7.0)
- **Целевая версия Android**: API 34 (Android 14)
- **Material Design 3**: Современный дизайн с поддержкой тёмной темы

## Технологии

- **Язык**: Kotlin
- **Архитектура**: Clean Architecture + MVVM
- **UI**: Jetpack Compose
- **Dependency Injection**: Hilt
- **Асинхронное программирование**: Kotlin Coroutines + Flow
- **Сетевое взаимодействие**: Retrofit + OkHttp
- **Аудио обработка**: Android MediaCodec (нативный API)
- **Навигация**: Navigation Compose

## Установка и сборка

### Требования
- Android Studio Hedgehog (2023.1.1) или новее
- JDK 17
- Android SDK 34
- Gradle 8.2+

### Сборка проекта

```bash
# Клонирование репозитория
git clone https://github.com/Leostrange/Vocal-Remover.git
cd Vocal-Remover/VocalClear

# Сборка debug APK
./gradlew assembleDebug

# Сборка release APK
./gradlew assembleRelease

# Запуск тестов
./gradlew test

# Очистка
./gradlew clean
```

## Структура проекта

```
VocalClear/
├── app/
│   ├── src/main/
│   │   ├── java/com/vocalclear/app/
│   │   │   ├── domain/                    # Domain Layer
│   │   │   │   ├── model/                 # Модели данных
│   │   │   │   │   ├── Models.kt          # Основные модели
│   │   │   │   │   └── Enums.kt           # Перечисления
│   │   │   │   ├── repository/            # Интерфейсы репозиториев
│   │   │   │   │   └── Repositories.kt
│   │   │   │   └── usecase/               # Бизнес-логика
│   │   │   │       └── UseCases.kt
│   │   │   ├── data/                      # Data Layer
│   │   │   │   ├── datasource/            # Источники данных
│   │   │   │   │   ├── LocalAudioDataSource.kt
│   │   │   │   │   ├── OfflineAudioProcessor.kt
│   │   │   │   │   ├── RemoteAudioProcessor.kt
│   │   │   │   │   └── VocalSectionDataSource.kt
│   │   │   │   ├── local/                 # Локальные источники
│   │   │   │   │   ├── ArchiveManager.kt
│   │   │   │   │   └── LocalAudioDataSource.kt
│   │   │   │   ├── remote/                # Удалённые источники
│   │   │   │   │   ├── RemoteAudioProcessor.kt
│   │   │   │   │   └── VocalClearApi.kt
│   │   │   │   └── repository/            # Реализации репозиториев
│   │   │   │       └── RepositoryImpl.kt
│   │   │   ├── di/                        # Dependency Injection
│   │   │   │   └── AppModule.kt
│   │   │   ├── presentation/              # Presentation Layer
│   │   │   │   ├── MainActivity.kt        # Главная активность
│   │   │   │   ├── viewmodel/             # ViewModels
│   │   │   │   │   └── MainViewModel.kt
│   │   │   │   └── ui/                    # UI компоненты
│   │   │   │       ├── theme/             # Тема оформления
│   │   │   │       │   ├── Theme.kt
│   │   │   │       │   └── Color.kt
│   │   │   │       └── sections/          # Редактор секций
│   │   │   │           └── VocalSectionEditor.kt
│   │   │   └── VocalClearApp.kt           # Application класс
│   │   ├── res/
│   │   │   ├── values/
│   │   │   │   ├── strings.xml            # Строки интерфейса
│   │   │   │   ├── colors.xml             # Цвета
│   │   │   │   └── themes.xml             # Темы
│   │   │   └── AndroidManifest.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts                        # Корневая конфигурация
├── settings.gradle.kts
├── gradle.properties
└── local.properties
```

## API

### MainViewModel

```kotlin
// Выбор аудиофайла
fun selectFile(uri: Uri)

// Обновление режима обработки
fun updateMode(mode: ProcessingMode)

// Запуск обработки
fun startProcessing()

// === Работа с секциями ===

// Установка вокального файла для редактирования
fun setVocalFile(file: File)

// Активация редактора секций
fun activateSectionEditor()
fun deactivateSectionEditor()

// Автоопределение секций
fun autoDetectSections()

// Добавление секции вручную
fun addSection(name: String, startTimeMs: Long, endTimeMs: Long)

// Удаление секции
fun removeSection(sectionId: String)

// Выбор секции
fun selectSection(section: VocalSection?)

// Экспорт одной секции
fun exportSection(section: VocalSection)

// Экспорт всех секций
fun exportAllSections()

// Создание архива с секциями
fun createSectionsArchive()
```

### VocalSectionRepository

```kotlin
// Вырезка секции из аудиофайла
suspend fun cutSection(
    inputFile: File,
    outputFile: File,
    startTimeMs: Long,
    endTimeMs: Long,
    onProgress: (Int, String) -> Unit
): Result<File>

// Получение длительности аудио
suspend fun getAudioDuration(file: File): Long

// Генерация данных waveform
suspend fun generateWaveformData(file: File, sampleCount: Int): Result<List<Float>>

// Экспорт нескольких секций
suspend fun exportSections(
    inputFile: File,
    sections: List<VocalSection>,
    outputDir: File,
    onProgress: (Int, String) -> Unit
): Result<List<SectionExportResult>>

// Создание архива секций
suspend fun createSectionsArchive(
    sections: List<SectionExportResult>,
    archiveName: String
): Result<File>
```

## Модели данных

### VocalSection
Представляет вокальную секцию для вырезки.

| Параметр | Тип | Описание |
|----------|-----|----------|
| `id` | String | Уникальный идентификатор секции |
| `name` | String | Название секции |
| `startTimeMs` | Long | Время начала в миллисекундах |
| `endTimeMs` | Long | Время конца в миллисекундах |
| `color` | SectionColor | Цвет для визуального отображения |

### ProcessingMode
Режим обработки аудио.

| Режим | Описание |
|-------|----------|
| `OFFLINE` | Локальная обработка на устройстве |
| `ONLINE` | Серверная обработка через API |

## Разрешения

- `READ_EXTERNAL_STORAGE`: Доступ к аудио файлам (до Android 10)
- `READ_MEDIA_AUDIO`: Доступ к аудио файлам (Android 13+)
- `INTERNET`: Сетевое взаимодействие для онлайн-режима

## Разработка

### Добавление новой функциональности

1. Создайте модель в `domain/model/`
2. Добавьте интерфейс репозитория в `domain/repository/`
3. Реализуйте репозиторий в `data/repository/`
4. Добавьте use case в `domain/usecase/`
5. Обновите DI модуль в `di/AppModule.kt`
6. Создайте UI компоненты в `presentation/ui/`

### Тестирование

```bash
# Запуск unit тестов
./gradlew test

# Запуск lint
./gradlew lint
```

## Лицензия

MIT License

## Автор

MiniMax Agent

## Благодарности

- [Jetpack Compose](https://developer.android.com/compose) - Современный UI Toolkit
- [Hilt](https://dagger.dev/hilt/) - Dependency Injection
- [Retrofit](https://square.github.io/retrofit/) - HTTP клиент
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) - Асинхронное программирование
