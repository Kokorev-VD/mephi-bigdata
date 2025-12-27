# Spark Network Traffic Analyzer

Standalone Apache Spark приложение для анализа сетевого трафика с интеграцией HBase.

## Структура проекта

```
spark-app/
├── build.gradle.kts                    # Gradle конфигурация для сборки
├── settings.gradle.kts                 # Gradle настройки проекта
└── src/main/kotlin/
    └── NetworkTrafficAnalyzer.kt       # Главный класс приложения
```

## Зависимости

- Apache Spark 3.5.0 (compileOnly)
- Apache HBase 2.5.5
- Kotlin 1.9.22
- Shadow JAR plugin для создания Fat JAR

## Сборка

```bash
./gradlew shadowJar
```

Результат: `build/libs/network-analyzer.jar`

## Запуск

```bash
spark-submit \
  --class NetworkTrafficAnalyzer \
  build/libs/network-analyzer.jar
```
