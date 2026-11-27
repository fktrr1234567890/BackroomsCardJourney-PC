@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

rem Windows 7 用这个脚本：只需要系统里有 Java 8（JRE 8）即可，不需要 jpackage。
rem jar 名里带版本号，这里用通配符自动匹配，换版本也不用改脚本。
set "JAR="
for %%f in ("BackroomsCardJourney-pc-*-win7.jar") do set "JAR=%%~ff"

if not defined JAR (
    echo [错误] 同目录下找不到 BackroomsCardJourney-pc-*-win7.jar
    echo 请确认 jar 和本脚本解压在同一个文件夹里。
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

echo 使用 jar：!JAR!
echo 当前 Java 版本：
java -version 2>&1

java -jar "!JAR!"
if errorlevel 1 (
    echo.
    echo 启动失败。如果是 "UnsupportedClassVersionError" 说明 Java 版本低于 8；
    echo 如果是黑屏无窗口，请确认系统为 Windows 7 及以上并且没有禁用图形加速。
    pause
)
endlocal
