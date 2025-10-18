# Requires: GraalVM JDK 17 on PATH, gu install native-image, Maven installed
$ErrorActionPreference = "Stop"
Push-Location ..\sidecar-src
mvn -q -DskipTests -Pnative native:compile
Pop-Location

# copy binary
New-Item -Force -ItemType Directory ..\sidecar\windows | Out-Null
Copy-Item ..\sidecar-src\target\rocketmq-sidecar.exe ..\sidecar\windows\rocketmq-sidecar.exe -Force
Set-Content ..\sidecar\VERSION.txt (Get-Date).ToString("yyyy-MM-dd HH:mm:ss") + " windows"
Write-Host "Done: sidecar/windows/rocketmq-sidecar.exe"