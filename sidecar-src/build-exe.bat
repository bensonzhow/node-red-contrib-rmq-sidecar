@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ===== 基本参数 =====
set "APP_NAME=rocketmq-sidecar"
set "APP_VERSION=1.0.0"
set "MAIN_JAR=target\%APP_NAME%-%APP_VERSION%.jar"
set "MAIN_CLASS=org.springframework.boot.loader.launch.JarLauncher"
set "DIST_DIR=dist"
set "NODE_RED_TARGET=..\sidecar\windows"
REM 如有图标：取消下一行注释并把路径改成你的 .ico
REM set "ICON_OPT=--icon ""%CD%\app.ico"""

echo ============================================================
echo  自动构建并部署 %APP_NAME%（使用完整 JRE，不使用 jlink）
echo ============================================================

REM ===== 1) 解析 JDK 路径（优先 JAVA_HOME，否则 where 检测） =====
set "JDK_HOME="
if not "%JAVA_HOME%"=="" if exist "%JAVA_HOME%\bin\jpackage.exe" set "JDK_HOME=%JAVA_HOME%"

if "%JDK_HOME%"=="" (
  for /f "delims=" %%I in ('where jpackage 2^>nul') do (
    REM 取 jpackage.exe 所在的 JDK 根目录
    for %%J in ("%%~dpI..") do set "JDK_HOME=%%~fJ"
    goto :foundJdk
  )
)

if "%JDK_HOME%"=="" (
  for /f "delims=" %%I in ('where java 2^>nul') do (
    REM 从 java.exe 反推 JDK 根目录，再检查 jpackage.exe 是否存在
    for %%J in ("%%~dpI..") do set "CANDIDATE=%%~fJ"
    if exist "!CANDIDATE!\bin\jpackage.exe" set "JDK_HOME=!CANDIDATE!"
    if not "%JDK_HOME%"=="" goto :foundJdk
  )
)

:foundJdk
if "%JDK_HOME%"=="" (
  echo [ERROR] 未找到可用的 jpackage：请安装 JDK 17+（含 jpackage），或设置 JAVA_HOME。
  echo [TIPS ] 例如（当前窗口临时生效）：  set "JAVA_HOME=D:\Java\jdk-17"
  echo         永久：                       setx JAVA_HOME "D:\Java\jdk-17"
  pause
  exit /b 1
)

echo [OK] 使用的 JDK: %JDK_HOME%
if not exist "%JDK_HOME%\bin\java.exe" (
  echo [ERROR] 路径异常：未找到 %JDK_HOME%\bin\java.exe
  pause & exit /b 1
)
if not exist "%JDK_HOME%\bin\jpackage.exe" (
  echo [ERROR] 路径异常：未找到 %JDK_HOME%\bin\jpackage.exe
  pause & exit /b 1
)

REM ===== 2) 如 JAR 不存在则构建 =====
if not exist "%MAIN_JAR%" (
  echo [INFO] 未找到 JAR：%MAIN_JAR%
  echo [INFO] 正在执行 mvn package -DskipTests ...
  mvn -q -DskipTests package
  if errorlevel 1 (
    echo [ERROR] Maven 构建失败。
    pause & exit /b 1
  )
) else (
  echo [OK] 已找到 JAR：%MAIN_JAR%
)

REM ===== 3) 使用 jpackage 打包（输入只含主 JAR） =====
echo [STEP] 创建临时 input 目录，仅包含主 JAR...
if exist tmp-input rmdir /s /q tmp-input
mkdir tmp-input
copy /Y "%MAIN_JAR%" tmp-input\

echo [STEP] 打包 EXE ...
"%JDK_HOME%\bin\jpackage.exe" ^
  --type app-image ^
  --name "%APP_NAME%" ^
  --input "tmp-input" ^
  --main-jar "%APP_NAME%-%APP_VERSION%.jar" ^
  --main-class "%MAIN_CLASS%" ^
  --app-version "%APP_VERSION%" ^
  --runtime-image "%JDK_HOME%" ^
  --win-console ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --java-options "-Drocketmq.remoting.serialize.type=ROCKETMQ" ^
  %ICON_OPT% ^
  --dest "%DIST_DIR%"
rmdir /s /q tmp-input

REM ===== 4) 复制成品目录到 Node-RED 插件 =====
echo [STEP] 复制成品目录到 Node-RED 插件：%NODE_RED_TARGET%
if not exist "%NODE_RED_TARGET%" mkdir "%NODE_RED_TARGET%"
xcopy /E /I /Y "%DIST_DIR%\%APP_NAME%" "%NODE_RED_TARGET%\%APP_NAME%" >nul
if errorlevel 1 (
  echo [WARN] 复制失败，请检查路径：%NODE_RED_TARGET%
) else (
  echo [OK] 已完整复制至 %NODE_RED_TARGET%\%APP_NAME%\
)

echo ============================================================
echo [OK] 构建完成！
echo 可执行文件位置：%DIST_DIR%\%APP_NAME%\%APP_NAME%.exe
echo 运行示例：
echo   %DIST_DIR%\%APP_NAME%\%APP_NAME%.exe --server.port=18080 --server.address=127.0.0.1
echo ============================================================
pause