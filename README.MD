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