@echo off
setlocal
cd /d "%~dp0"

rem Windows 7 用这个脚本：只需要系统里有 Java 8（JRE 8）即可，不需要 jpackage。
if not exist "BackroomsCardJourney-pc-0.0.6-win7.jar" (
    echo [错误] 同目录下找不到 BackroomsCardJourney-pc-0.0.6-win7.jar
    pause
    exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
    echo [错误] 没有找到 java 命令。
    echo 请先安装 Java 8（JRE 8，位数要和系统一致），装完重新双击本脚本。
    echo 下载：https://adoptium.net/temurin/releases/?version=8
    pause
    exit /b 1
)

echo 当前 Java 版本：
java -version 2>&1

java -jar "BackroomsCardJourney-pc-0.0.6-win7.jar"
if errorlevel 1 (
    echo.
    echo 启动失败。如果是 "UnsupportedClassVersionError" 说明 Java 版本低于 8；
    echo 如果是黑屏无窗口，请确认系统为 Windows 7 及以上并且没有禁用图形加速。
    pause
)
endlocal
