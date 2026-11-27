@echo off
setlocal
cd /d "%~dp0"

set "APP=build\install\BackroomsCardJourney-pc\bin\BackroomsCardJourney-pc.bat"

if exist "%APP%" (
    call "%APP%"
    goto :end
)

where gradle >nul 2>nul
if errorlevel 1 (
    echo [错误] 没有找到 gradle 命令。
    echo 请先安装 JDK 17 和 Gradle 8.2+，或者直接解压 Release 里的分发包运行。
    pause
    exit /b 1
)

echo 首次运行，正在编译（gradle installDist）……
call gradle installDist
if errorlevel 1 (
    echo [错误] 编译失败，请看上面的日志。
    pause
    exit /b 1
)

call "%APP%"

:end
endlocal
