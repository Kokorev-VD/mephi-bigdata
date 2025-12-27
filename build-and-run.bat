@echo off

echo Building Spark app...
cd spark-app
call gradlew.bat clean shadowJar
cd ..

echo Building Storm app...
cd storm-app
call gradlew.bat clean shadowJar
cd ..

echo Starting Docker Compose...
docker-compose up -d

echo Waiting for services to be ready...
timeout /t 20 /nobreak

echo Checking Storm topology status...
docker exec mephi-storm-nimbus storm list

echo.
echo Services started!
echo - Storm UI: http://localhost:8081
echo - Spark Master UI: http://localhost:8080
echo - NATS Monitoring: http://localhost:8222
echo - HBase Master UI: http://localhost:16010
echo.
echo To view logs:
echo   Spark app: docker logs -f mephi-spark-app
echo   Storm app: docker logs -f mephi-storm-app
