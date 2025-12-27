### МИФИ Лабараторные работы. Обработка больших данных

## 📋 Описание проекта

Распределённая система сбора, обработки и хранения метрик системы с использованием современного стека технологий:

```
Spring Boot → NATS JetStream → Apache NiFi → Apache HBase
```

### 1. Запуск всех сервисов

```bash
docker-compose up -d
```

### 2. Создание таблицы HBase

```bash
chmod +x scripts/create-hbase-table.sh
./scripts/create-hbase-table.sh
```

### 3. Доступ к Web интерфейсам

| Сервис | URL | Credentials |
|--------|-----|-------------|
| **NiFi UI** | https://localhost:8443/nifi | admin / adminadminadmin |
| **HBase Master UI** | http://localhost:16010 | - |
| **NATS Monitoring** | http://localhost:8222/jsz?streams=true | - |
| **Spark Web UI** | http://localhost:8080 | - |
| **Storm Web UI** | http://localhost:8081 | - |

## 📖 Документация

TODO

## 🔍 Проверка работы

### Проверить NATS Stream
```bash
docker exec mephi-nats nats stream info METRICS
```

### Проверить HBase
```bash
docker exec -it mephi-hbase hbase shell
scan 'system_metrics', {LIMIT => 10}
```

## Lab 4: Apache Spark - Анализ трафика

**Цель:** Подсчитать AVG(bytesReceived/bytesSent) с интервалом 10 минут.

**Запуск:**

```bash
# 1. Создать таблицу результатов
./scripts/create-result-table.sh

# 2. Собрать и запустить
cd spark-app
./gradlew shadowJar
docker exec mephi-spark sh -c "/opt/spark/bin/spark-submit --class NetworkTrafficAnalyzer --master local[*] /opt/spark-apps/network-analyzer.jar"

# 3. Проверить результаты
docker exec -i mephi-hbase hbase shell
> scan 'network_traffic_analysis', {LIMIT => 5}
```

**Структура:**
- `spark-app/` - standalone Kotlin приложение
- `NetworkTrafficAnalyzer.kt` - весь код в одном файле (204 строки)

**UI:**
- Spark Web UI: http://localhost:8080

## Lab 5: Apache Storm - Spike Detection

**Цель:** Обнаружение всплесков CPU/Memory в реальном времени с использованием Apache Storm для потоковой обработки.

**Описание задачи:**

Storm топология `SpikeDetectorTopology` осуществляет:
- Прием метрик из NATS JetStream в реальном времени
- Анализ исторических данных за скользящее окно (последние 5 минут)
- Обнаружение аномалий с использованием алгоритма Z-score
- Публикацию событий спайков в поток `metrics.spikes`
- Хранение результатов в HBase таблице `spikes`

**Архитектура:**
```
NATS JetStream (METRICS)
    ↓
[NatsSpout] - читает метрики
    ↓
[SpikeDetectorBolt] - анализирует и детектирует спайки
    ↓
[NatsOutputBolt] - публикует результаты в NATS
    ↓
NATS JetStream (SPIKES)
```

**Запуск:**

### 1. Предварительная подготовка

```bash
# Убедитесь, что все сервисы запущены
docker-compose up -d

# Проверить статус сервисов
docker-compose ps
```

### 2. Инициализация NATS потоков

```bash
# Создать необходимые NATS streams
chmod +x scripts/setup-nats-streams.sh
./scripts/setup-nats-streams.sh

# Проверить созданные stream'ы
docker exec mephi-nats nats stream ls
```

### 3. Развертывание топологии (РЕКОМЕНДУЕТСЯ)

```bash
# Перейти в директорию проекта
cd storm-app

# Запустить автоматизированное развертывание
chmod +x deploy.sh
./deploy.sh
```

**Или ручное развертывание:**
```bash
cd storm-app
./gradlew shadowJar
docker exec mephi-storm-nimbus storm jar /storm-jars/spike-detector.jar SpikeDetectorTopology spike-detector
```

### 4. Проверка статуса

```bash
# Проверить, что топология развернута
cd storm-app
./status.sh

# Или напрямую через CLI
docker exec mephi-storm-nimbus storm list
```

**Примеры использования:**

### Мониторинг спайков в реальном времени

```bash
# Запустить мониторинг (выводит спайки по мере их обнаружения)
./scripts/watch-spikes.sh

# Пример вывода:
# [2024-01-15 14:23:45] Spike Detected:
#   Type: cpu_spike
#   Metric: cpu_usage
#   Value: 87.5
#   Severity: high
```

### Генерация тестовой нагрузки

```bash
# Генерировать CPU spike на 60 секунд
./scripts/generate-load.sh --cpu --duration 60

# Генерировать Memory load на 90 секунд
./scripts/generate-load.sh --memory --duration 90 --memory-size 512

# Генерировать комбинированную нагрузку
./scripts/generate-load.sh --both --duration 120 --cpu-threads 8 --memory-size 1024
```

### Проверка результатов в HBase

```bash
# Подключиться к HBase shell
docker exec -it mephi-hbase hbase shell

# Сканировать таблицу спайков
> scan 'spikes', {LIMIT => 10}

# Проверить количество спайков
> count 'spikes'

# Выход из shell
> exit
```

### Работа с NATS JetStream
e,yh
```bash
# Просмотр информации о потоке SPIKES
docker exec mephi-nats nats stream info SPIKES

# Сбор статистики по спайкам
./scripts/spikes-stats.sh

# Просмотр последних спайков (с начала потока)
docker exec mephi-nats nats sub metrics.spikes --stream SPIKES --all
```

**Структура компонентов:**

- `storm-app/` - Kotlin приложение для Storm
  - `SpikeDetectorTopology.kt` - главная топология
  - `NatsSpout.kt` - читает из NATS JetStream
  - `SpikeDetectorBolt.kt` - детектирует аномалии
  - `NatsOutputBolt.kt` - публикует результаты
- `scripts/` - вспомогательные скрипты для управления и мониторинга

**Доступные скрипты:**

| Скрипт | Назначение |
|--------|-----------|
| `storm-app/deploy.sh` | Автоматизированное развертывание топологии |
| `storm-app/undeploy.sh` | Остановка и удаление топологии |
| `storm-app/status.sh` | Проверка статуса развернутой топологии |
| `scripts/setup-nats-streams.sh` | Инициализация NATS потоков |
| `scripts/watch-spikes.sh` | Мониторинг спайков в реальном времени |
| `scripts/generate-load.sh` | Генерация тестовой нагрузки (CPU/Memory) |
| `scripts/spikes-stats.sh` | Статистика по обнаруженным спайкам |

**Web UI:**

| Интерфейс | URL |
|-----------|-----|
| **Storm UI** | http://localhost:8081 |
| **NATS Monitoring** | http://localhost:8222/jsz?streams=true |
| **HBase Master UI** | http://localhost:16010 |

**Полный цикл (быстрый старт):**

```bash
# 1. Убедиться, что все сервисы запущены
docker-compose up -d

# 2. Инициализировать NATS streams
./scripts/setup-nats-streams.sh

# 3. Развернуть Storm топологию (в одном терминале)
cd storm-app && ./deploy.sh

# 4. В другом терминале: Запустить мониторинг спайков
./scripts/watch-spikes.sh

# 5. В третьем терминале: Генерировать нагрузку
./scripts/generate-load.sh --both --duration 60

# 6. Наблюдать за спайками во втором терминале!
```

**Troubleshooting:**

### Проблема: Топология не развертывается

```bash
# Проверить, что Docker запущен
docker ps

# Убедиться, что Nimbus контейнер существует
docker ps | grep storm

# Посмотреть логи Storm
docker logs mephi-storm-nimbus

# Пересоздать контейнеры
docker-compose down
docker-compose up -d
```

### Проблема: Спайки не обнаруживаются

```bash
# 1. Проверить, что NATS stream создан
docker exec mephi-nats nats stream ls

# 2. Убедиться, что метрики поступают в NATS
docker exec mephi-nats nats sub METRICS --stream METRICS --all

# 3. Проверить логи топологии
docker exec mephi-storm-nimbus storm logs spike-detector

# 4. Проверить статус болтов
docker exec mephi-storm-nimbus storm list
docker exec mephi-storm-nimbus storm top | grep spike-detector
```

### Проблема: Логирование не работает

```bash
# Убедиться, что лог-файлы доступны
docker exec mephi-storm-nimbus ls -la /var/log/storm/

# Просмотреть логи конкретного workera
docker logs mephi-storm-supervisor-1
```

### Проблема: NATS соединение не устанавливается

```bash
# Проверить, что NATS контейнер работает
docker ps | grep nats

# Проверить доступность NATS
docker exec mephi-nats nats pub test "hello"

# Если проблема в сетевом соединении между контейнерами
docker network ls
docker network inspect mephi-bigdata_default
```

### Очистка и переустановка

```bash
# Остановить топологию
cd storm-app && ./undeploy.sh

# Удалить все контейнеры и томы
docker-compose down -v

# Пересоздать все
docker-compose up -d
./scripts/setup-nats-streams.sh
cd storm-app && ./deploy.sh
```

**Документация и ссылки:**

- `storm-app/DEPLOY_GUIDE.md` - подробная инструкция по развертыванию
- `storm-app/QUICK_REFERENCE.md` - краткий справочник команд
- `storm-app/IMPLEMENTATION_REPORT.md` - описание реализации
- `storm-app/NATS_SPOUT_README.md` - документация NatsSpout

**Полезные команды Storm:**

```bash
# Список всех топологий
docker exec mephi-storm-nimbus storm list

# Информация о конкретной топологии
docker exec mephi-storm-nimbus storm desc spike-detector

# Real-time статистика
docker exec mephi-storm-nimbus storm top

# Логи топологии
docker exec mephi-storm-nimbus storm logs spike-detector

# Остановка топологии с graceful shutdown
docker exec mephi-storm-nimbus storm kill spike-detector -w 30

# Переактивирование топологии
docker exec mephi-storm-nimbus storm activate spike-detector
docker exec mephi-storm-nimbus storm deactivate spike-detector
```

**Мониторинг производительности:**

```bash
# Проверить процент обработанных кортежей
docker exec mephi-storm-nimbus storm list

# Проверить нагрузку на воркеры
docker logs mephi-storm-supervisor-1 | grep throughput

# Получить метрики из JMX (если включен)
docker exec mephi-storm-nimbus jconsole

# Анализировать пропускную способность потока
./scripts/spikes-stats.sh
```