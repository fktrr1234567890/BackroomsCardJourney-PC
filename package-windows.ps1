# 把游戏打包成 Windows 可执行程序（jpackage）。
# 用法：
#   powershell -ExecutionPolicy Bypass -File package-windows.ps1            # 免安装 exe 目录
#   powershell -ExecutionPolicy Bypass -File package-windows.ps1 -Installer # 生成 MSI 安装包（需 WiX）

param(
    [switch]$Installer
)

$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

$Version = (Get-Content -Path "release\VERSION" -Raw).Trim()
$ProjectName = "BackroomsCardJourney-pc"
$AppName = "BackroomsCardJourney"
$Dest = "dist"

Write-Host "版本：$Version" -ForegroundColor Cyan

Write-Host "==> 编译并生成运行目录" -ForegroundColor Cyan
gradle installDist
if ($LASTEXITCODE -ne 0) { throw "gradle installDist 失败" }

if (Test-Path $Dest) { Remove-Item -Recurse -Force $Dest }
New-Item -ItemType Directory -Path $Dest | Out-Null

$jarName = "$ProjectName-$Version.jar"
$libDir = "build\install\$ProjectName\lib"
if (-not (Test-Path (Join-Path $libDir $jarName))) {
    throw "找不到主 jar：$libDir\$jarName"
}

$type = if ($Installer) { "msi" } else { "app-image" }
Write-Host "==> jpackage（type=$type）" -ForegroundColor Cyan

$args = @(
    "--type", $type,
    "--name", $AppName,
    "--app-version", $Version,
    "--input", $libDir,
    "--main-jar", $jarName,
    "--main-class", "com.backrooms.cardjourney.MainKt",
    "--dest", $Dest,
    "--icon", "assets\icon.ico"
)
if ($Installer) {
    $args += @("--win-shortcut", "--win-menu")
}

& jpackage @args
if ($LASTEXITCODE -ne 0) {
    Write-Host "[失败] jpackage 返回 $LASTEXITCODE。免安装模式不需要额外依赖；MSI 需要先安装 WiX Toolset。" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "==> 完成：$Dest" -ForegroundColor Green
Get-ChildItem $Dest | Format-Table Name, Length
