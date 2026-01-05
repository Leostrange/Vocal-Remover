# Vocal Remover - Android App

Профессиональное приложение для разделения аудио дорожек на вокал и инструментальную часть непосредственно на устройстве Android.

## Возможности

- **Разделение вокала и инструментов**: Использует алгоритмы центрального канала для разделения аудио
- **Разделение на 4 дорожки**: Барабаны, бас, вокал и остальные инструменты
- **Аудио фильтры**: Эквалайзер, компрессор, нормализация и другие
- **Поддержка форматов**: MP3, WAV, FLAC, OGG, M4A, AAC
- **Локальная обработка**: Все вычисления происходят на устройстве без сервера
- **Material Design 3**: Современный дизайн с поддержкой темной темы

## Технологии

- **Язык**: Kotlin
- **Архитектура**: Clean Architecture + MVVM
- **UI**: View Binding
- **Dependency Injection**: Hilt
- **Аудио обработка**: FFmpegKit, TensorFlow Lite (опционально)
- **Минимальная версия Android**: API 24 (Android 7.0)
- **Целевая версия Android**: API 34 (Android 14)

## Исправления и улучшения

### Версия 1.1 (2026-01-05)

#### Исправления ошибок:

1. **Разрешения для Android 10+**:
   - Добавлена поддержка `READ_MEDIA_AUDIO` для Android 13+
   - Добавлена динамическая проверка разрешений
   - Использован ActivityResult API для современной обработки

2. **Работа с файлами**:
   - Исправлено копирование файлов из URI во внутреннее хранилище
   - Добавлен FileProvider для безопасного обмена файлами
   - Исправлено определение имен файлов из URI

3. **Gradle конфигурация**:
   - Обновлены зависимости до актуальных версий
   - Добавлен Hilt для dependency injection
   - Добавлен FFmpegKit для аудио обработки
   - Добавлен TensorFlow Lite для ML моделей
   - Оптимизирована конфигурация сборки

4. **UI/UX улучшения**:
   - Обновлена тема до Material Design 3
   - Добавлены цвета и типографика
   - Улучшена обработка ошибок

5. **ProGuard правила**:
   - Добавлены правила для FFmpegKit
   - Добавлены правила для TensorFlow Lite
   - Добавлены правила для Hilt

## Установка

### Требования

- Android Studio Hedgehog (2023.1.1) или новее
- JDK 17
- Android SDK 34
- Gradle 8.2+

### Сборка

```bash
# Клонирование репозитория
git clone <repository-url>
cd android_project

# Сборка debug APK
./gradlew assembleDebug

# Сборка release APK
./gradlew assembleRelease

# Запуск тестов
./gradlew test

# Очистка
./gradlew clean
```

### Ручная сборка

```bash
chmod +x build_apk.sh
./build_apk.sh
```

## Структура проекта

```
app/
├── src/main/
│   ├── java/com/example/vocalremover/
│   │   ├── MainActivity.kt          # Основная активность
│   │   ├── VocalProcessor.kt        # Обработка аудио
│   │   ├── AudioAnalyzer.kt         # Анализ аудио
│   │   ├── AudioFilter.kt           # Аудио фильтры
│   │   ├── MLModelManager.kt        # ML модели
│   │   ├── PerformanceOptimizer.kt  # Оптимизация
│   │   ├── VocalRemoverApp.kt       # Application класс
│   │   └── di/
│   │       └── AppModule.kt         # Hilt модули
│   ├── res/
│   │   ├── layout/activity_main.xml # Макет UI
│   │   ├── values/
│   │   │   ├── strings.xml          # Строки
│   │   │   ├── colors.xml           # Цвета
│   │   │   └── themes.xml           # Темы
│   │   ├── drawable/                # Графика
│   │   └── xml/                     # Конфигурация
│   └── AndroidManifest.xml
└── build.gradle.kts                 # Конфигурация сборки
```

## API

### VocalProcessor

```kotlin
// Разделение на вокал и инструменты
fun separateVocals(inputFile: File, outputDir: File, callback: ProgressCallback)

// Разделение на 4 дорожки
fun separateStems(inputFile: File, outputDir: File, stems: Int = 4, callback: ProgressCallback)

// Обработка с фильтрами
fun processAudioWithCustomFilters(inputFile: File, filters: List<AudioFilter>, callback: ProgressCallback)
```

### AudioFilter

```kotlin
// Доступные фильтры
val highPass = HighPass(frequency = 80.0)
val lowPass = LowPass(frequency = 8000.0)
val bandPass = BandPass(frequency = 1000.0, width = 200.0)
val equalizer = Equalizer(frequency = 1000.0, width = 200.0, gain = 3.0)
val compressor = Compressor()
val normalize = Normalize(targetLevel = -23.0)
```

## Разрешения

- `READ_EXTERNAL_STORAGE`: Доступ к аудио файлам (до Android 10)
- `WRITE_EXTERNAL_STORAGE`: Сохранение результатов (до Android 10)
- `READ_MEDIA_AUDIO`: Доступ к аудио файлам (Android 13+)
- `FOREGROUND_SERVICE`: Фоновая обработка (опционально)

## Лицензия

MIT License

## Автор

MiniMax Agent

## Благодарности

- [FFmpegKit](https://github.com/arthenica/ffmpeg-kit) - Аудио/видео обработка
- [TensorFlow Lite](https://www.tensorflow.org/lite) - Машинное обучение на устройстве
- [Hilt](https://dagger.dev/hilt/) - Dependency Injection
- [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) - Архивация
