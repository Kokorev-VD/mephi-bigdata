# Storm App - Spike Detector Topology

Приложение на базе Apache Storm для обнаружения всплесков системных метрик (CPU, Memory) в реальном времени.

## Архитектура топологии

```
NATS JetStream (metrics.system.snapshot)
         |
         v
   [NatsSpout] (читает метрики)
         |
         | shuffleGrouping
         v
[SpikeDetectorBolt] (анализирует и детектирует всплески)
         |
         | shuffleGrouping
         v
  [NatsOutputBolt] (публикует результаты)
         |
         v
NATS JetStream (metrics.spikes)
```

### Компоненты

1. **NatsSpout** - источник данных
   - Читает системные метрики из NATS JetStream
   - Subject: `metrics.system.snapshot`
   - Эмитирует: `(timestamp, cpuLoad, memoryUsed)`

2. **SpikeDetectorBolt** - детектор всплесков
   - Использует скользящее окно (5 минут)
   - Вычисляет среднее значение для CPU и Memory
   - Детектирует всплеск если значение > среднее * 1.5
   - Эмитирует события только при обнаружении всплеска

3. **NatsOutputBolt** - публикация результатов
   - Публикует события всплесков в NATS JetStream
   - Subject: `metrics.spikes`
   - Формат: JSON с деталями всплеска

## Структура проекта

```
storm-app/
├── build.gradle.kts          - Конфигурация Gradle
├── settings.gradle.kts       - Настройки проекта
├── deploy.sh                 - Скрипт развертывания топологии
├── undeploy.sh               - Скрипт удаления топологии
├── src/
│   └── main/
│       └── kotlin/
│           └── SpikeDetectorTopology.kt
└── README.md
```

## Технологический стек

- **Kotlin**: 1.9.22
- **Java**: 11
- **Apache Storm**: 2.6.0
- **NATS**: 2.17.2 (для коммуникации)
- **Jackson**: 2.15.0 (для JSON обработки)
- **Build**: Gradle с Shadow JAR plugin

## Сборка проекта

```bash
./gradlew build
```

Результат: `build/libs/spike-detector.jar` (fat JAR)

## Развертывание

### Автоматизированное развертывание

Скрипт `deploy.sh` автоматизирует процесс развертывания топологии:

```bash
./deploy.sh
```

Скрипт выполняет следующие шаги:
1. Проверяет, что Docker запущен
2. Проверяет, что контейнер Storm Nimbus запущен
3. Собирает Shadow JAR файл (`./gradlew shadowJar`)
4. Проверяет доступность JAR файла в контейнере
5. Подает топологию в Storm (`storm jar ...`)
6. Выводит статус топологии

### Остановка топологии

Для остановки развернутой топологии используйте:

```bash
./undeploy.sh
```

### Ручное развертывание

Если необходимо развернуть вручную:

```bash
# Собрать JAR
./gradlew shadowJar

# Подать топологию в Storm
docker exec mephi-storm-nimbus \
  storm jar /storm-jars/spike-detector.jar \
  SpikeDetectorTopology \
  spike-detector

# Проверить статус
docker exec mephi-storm-nimbus storm list
```

## Запуск

### Локальный режим (для тестирования)

```bash
java -cp build/libs/spike-detector.jar SpikeDetectorTopology local
```

В локальном режиме топология работает в текущем процессе JVM. Удобно для отладки.

### Production режим (Storm кластер)

```bash
# Автоматическое развертывание через скрипт
./deploy.sh

# Или вручную
docker exec mephi-storm-nimbus \
  storm jar /storm-jars/spike-detector.jar \
  SpikeDetectorTopology
```

### Мониторинг

Storm UI доступен по адресу: http://localhost:8081

## Конфигурация топологии

Топология поддерживает настройку через переменные окружения:

| Переменная | Описание | Значение по умолчанию |
|------------|----------|----------------------|
| `SPOUT_PARALLELISM` | Количество экземпляров NatsSpout | 1 |
| `DETECTOR_PARALLELISM` | Количество экземпляров SpikeDetectorBolt | 1 |
| `OUTPUT_PARALLELISM` | Количество экземпляров NatsOutputBolt | 1 |
| `NUM_WORKERS` | Количество worker процессов | 1 |
| `MAX_SPOUT_PENDING` | Максимальное количество необработанных tuple | 1000 |
| `DEBUG` | Включить debug режим (true/false) | false |

### Примеры конфигурации

```bash
# Увеличенный parallelism для высокой нагрузки
export SPOUT_PARALLELISM=2
export DETECTOR_PARALLELISM=4
export OUTPUT_PARALLELISM=2
export NUM_WORKERS=2

# Debug режим
export DEBUG=true

# Запуск с настройками
java -cp build/libs/spike-detector.jar SpikeDetectorTopology
```

## Зависимости

- `org.apache.storm:storm-core:2.6.0` - Apache Storm
- `io.nats:jnats:2.17.2` - NATS клиент
- `com.fasterxml.jackson.module:jackson-module-kotlin:2.15.0` - JSON сериализация
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.0` - Java Time поддержка

## Разработка

Проект следует паттернам spark-app для единообразия кодовой базы.
