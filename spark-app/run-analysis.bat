@echo off
REM Ручной запуск анализа сетевого трафика

echo ====================================
echo Spark Traffic Analyzer
echo ====================================
echo Запуск: %date% %time%
echo.

docker exec mephi-spark /opt/spark/bin/spark-submit ^
  --class NetworkTrafficAnalyzer ^
  --master local[*] ^
  --driver-memory 2g ^
  --executor-memory 2g ^
  /opt/spark-apps/network-analyzer.jar

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [OK] Анализ завершен
) else (
    echo.
    echo [ERROR] Ошибка: %ERRORLEVEL%
)

echo ====================================
pause
