# Quick Start: Spike Monitoring

Быстрый старт для мониторинга всплесков метрик в NATS JetStream.

## Требования

- Docker (для NATS контейнера)
- bash >= 4.0
- jq (опционально, для красивого вывода JSON)

## Установка

### 1. Проверить, что NATS запущен

```bash
docker-compose up -d mephi-nats
```

### 2. Создать JetStream stream (если его еще нет)

```bash
# Проверить существующие потоки
docker exec mephi-nats nats stream list

# Если SPIKES не существует, создать
docker exec -it mephi-nats nats stream add SPIKES \
  --subjects "metrics.spikes" \
  --storage file
```

## Использование

### Базовый мониторинг

```bash
# Смотреть все новые всплески в реальном времени
bash scripts/watch-spikes.sh
```

### Мониторинг с фильтрацией

```bash
# Только критические всплески
bash scripts/watch-spikes-advanced.sh --severity critical

# Только CPU всплески
bash scripts/watch-spikes-advanced.sh --metric CPU

# Критические CPU всплески на конкретном хосте
bash scripts/watch-spikes-advanced.sh --severity critical --metric CPU --hostname web-server

# С живой статистикой
bash scripts/watch-spikes-advanced.sh --live-stats

# Все сообщения (включая старые) с статистикой
bash scripts/watch-spikes-advanced.sh --all --live-stats
```

### Тестирование

```bash
# Создать и отправить тестовое сообщение о всплеске
bash scripts/test-spike-message.sh

# Создать несколько тестовых сообщений подряд
for i in {1..5}; do
  bash scripts/test-spike-message.sh
  sleep 1
done
```

### Анализ статистики

```bash
# Статистика за последний час
bash scripts/spikes-stats.sh

# Статистика за последние 24 часа
bash scripts/spikes-stats.sh --hours 24

# Статистика в JSON формате
bash scripts/spikes-stats.sh --json

# Статистика только CPU всплесков
bash scripts/spikes-stats.sh --metric CPU
```

## Типичный рабочий процесс

### Терминал 1: Запустить мониторинг

```bash
bash scripts/watch-spikes.sh
```

### Терминал 2: Отправить тестовые сообщения

```bash
for i in {1..10}; do
  bash scripts/test-spike-message.sh
  sleep 2
done
```

### Терминал 3: Просмотреть статистику

```bash
bash scripts/spikes-stats.sh
```

## Структура сообщения о всплеске

```json
{
  "timestamp": "2025-12-27T14:35:22Z",
  "hostname": "web-server-01",
  "type": "spike",
  "metric": "CPU|MEMORY|DISK",
  "value": 87,
  "unit": "%",
  "severity": "critical|high|medium|low",
  "threshold": 50,
  "spike_duration_ms": 3421,
  "message": "Detected CPU usage spike on web-server-01"
}
```

## Примеры вывода

### watch-spikes.sh

```
╔════════════════════════════════════════════════════════╗
║    NATS JetStream Spikes Monitor - Real-time Watch    ║
╚════════════════════════════════════════════════════════╝
Subject: metrics.spikes
Stream:  SPIKES
Container: mephi-nats
✓ JSON formatting: Enabled (jq)
────────────────────────────────────────────────────────
Waiting for spikes... Press Ctrl+C to stop

[2025-12-27 14:35:22] Spike Detected:
  Type: spike
  Metric: CPU
  Value: 87
  Severity: high
  Full data:
    {
      "timestamp": "2025-12-27T14:35:22Z",
      "hostname": "web-server-01",
      "type": "spike",
      "metric": "CPU",
      "value": 87,
      "unit": "%",
      "severity": "high",
      "threshold": 50,
      "spike_duration_ms": 3421,
      "message": "Detected CPU usage spike on web-server-01"
    }
```

### watch-spikes-advanced.sh

```
╔════════════════════════════════════════════════════════╗
║   NATS JetStream Spikes Monitor - Advanced Mode      ║
╚════════════════════════════════════════════════════════╝

Configuration:
  Subject:  metrics.spikes
  Stream:   SPIKES
  Container: mephi-nats

Active Filters:
  Severity: critical

Status: Monitoring spikes... (Ctrl+C to stop)
────────────────────────────────────────────────────────

[14:35:22] [critical] CPU spike on web-server-01: 95%
[STATS] Total: 1 | CPU: 1 | MEMORY: 0 | DISK: 0

[14:35:45] [critical] MEMORY spike on web-server-02: 92%
[STATS] Total: 2 | CPU: 1 | MEMORY: 1 | DISK: 0
```

## Полезные команды NATS

```bash
# Список всех потоков
docker exec mephi-nats nats stream list

# Информация о потоке SPIKES
docker exec mephi-nats nats stream info SPIKES

# Подсчет всех сообщений в потоке
docker exec mephi-nats nats stream info SPIKES --json | jq '.state.messages'

# Удалить все сообщения из потока
docker exec mephi-nats nats stream purge SPIKES --force

# Просмотреть последние 10 сообщений
docker exec mephi-nats nats sub metrics.spikes --stream SPIKES --last 10
```

## Решение проблем

### Контейнер NATS не отвечает

```bash
# Проверить статус
docker ps | grep mephi-nats

# Перезагрузить
docker restart mephi-nats

# Посмотреть логи
docker logs mephi-nats
```

### Нет сообщений

```bash
# Отправить тестовое сообщение
bash scripts/test-spike-message.sh

# Проверить поток
docker exec mephi-nats nats stream info SPIKES

# Убедиться, что сообщения есть
docker exec mephi-nats nats sub metrics.spikes --stream SPIKES --all --timeout 5s
```

### jq не установлен

```bash
# macOS
brew install jq

# Ubuntu/Debian
sudo apt-get install jq

# CentOS/RHEL
sudo yum install jq

# Скрипты работают и без jq, но с меньшей красотой
```

## Интеграция с мониторингом

### Отправить алерт на email при критических всплесках

```bash
bash scripts/watch-spikes-advanced.sh --severity critical | \
  while read line; do
    if [[ "$line" == *"critical"* ]]; then
      echo "$line" | mail -s "КРИТИЧЕСКИЙ ВСПЛЕСК" ops@example.com
    fi
  done
```

### Сохранять логи в файл

```bash
bash scripts/watch-spikes.sh >> /var/log/spikes/$(date +%Y-%m-%d).log 2>&1 &
```

### Периодический отчет о статистике

```bash
# Добавить в crontab
0 */4 * * * bash /path/to/scripts/spikes-stats.sh >> /var/log/spikes-report.log
```

## Полная документация

Для более подробной информации см. `SPIKES_MONITORING_README.md`

## Скрипты

- `watch-spikes.sh` - Базовый мониторинг в реальном времени
- `watch-spikes-advanced.sh` - Мониторинг с фильтрацией и статистикой
- `test-spike-message.sh` - Генерация тестовых сообщений
- `spikes-stats.sh` - Анализ статистики всплесков
