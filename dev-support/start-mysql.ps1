# Arranca MySQL 8.4 (Community) sin servicio de Windows, usando un datadir local bajo %USERPROFILE%.
# Si instalaste otra versión/ruta, cambiá $Basedir más abajo.
# Primera vez: inicializa datos, contraseña root = insecure y crea la BD pdyc2026 (igual que application.properties).
# Uso (PowerShell):  cd dev-support; .\start-mysql.ps1

$ErrorActionPreference = 'Stop'
$Basedir = 'C:\Program Files\MySQL\MySQL Server 8.4'
$DataDir = Join-Path $env:USERPROFILE 'mysql-data-pdyc2026'
$mysqld = Join-Path $Basedir 'bin\mysqld.exe'
$mysql = Join-Path $Basedir 'bin\mysql.exe'

if (-not (Test-Path $mysqld)) {
    Write-Host "No se encontró mysqld en $Basedir. Instalá MySQL con: winget install -e --id Oracle.MySQL"
    exit 1
}

$portOpen = (Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 -WarningAction SilentlyContinue).TcpTestSucceeded
if ($portOpen) {
    Write-Host 'MySQL ya está escuchando en 127.0.0.1:3306.'
    exit 0
}

if (-not (Test-Path (Join-Path $DataDir 'mysql'))) {
    Write-Host "Inicializando datadir en $DataDir ..."
    New-Item -ItemType Directory -Force -Path $DataDir | Out-Null
    & $mysqld --initialize-insecure --basedir="$Basedir" --datadir="$DataDir"
    Start-Sleep -Seconds 3
    Start-Process -FilePath $mysqld -ArgumentList @(
        '--basedir=' + $Basedir,
        '--datadir=' + $DataDir,
        '--bind-address=127.0.0.1',
        '--port=3306'
    ) -WindowStyle Minimized
    $deadline = (Get-Date).AddSeconds(45)
    while ((Get-Date) -lt $deadline) {
        if ((Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 -WarningAction SilentlyContinue).TcpTestSucceeded) { break }
        Start-Sleep -Milliseconds 500
    }
    if (-not (Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 -WarningAction SilentlyContinue).TcpTestSucceeded) {
        Write-Error 'MySQL no respondió a tiempo en el puerto 3306.'
        exit 1
    }
    & $mysql -h 127.0.0.1 -P 3306 -u root -e @"
ALTER USER 'root'@'localhost' IDENTIFIED BY 'insecure';
CREATE DATABASE IF NOT EXISTS pdyc2026 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
FLUSH PRIVILEGES;
"@
    Write-Host 'MySQL listo: usuario root, contraseña insecure, base pdyc2026.'
    exit 0
}

Write-Host "Iniciando mysqld (datadir existente)..."
Start-Process -FilePath $mysqld -ArgumentList @(
    '--basedir=' + $Basedir,
    '--datadir=' + $DataDir,
    '--bind-address=127.0.0.1',
    '--port=3306'
) -WindowStyle Minimized
Write-Host 'mysqld lanzado. Esperá unos segundos y ejecutá la app Spring (puerto 8080 libre).'
