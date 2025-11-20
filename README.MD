### МИФИ Лабараторные работы. Обработка больших данных

TODO: Норм описание

Полезные команды:

```shell
# Запуск проекта
docker-compose up

# Вычитка из NATS общей информации
curl -s http://localhost:8222/jsz?streams=true

# Вычитка из NATS сообщений из консоли
nats sub metrics.system.snapshot --count=10
```