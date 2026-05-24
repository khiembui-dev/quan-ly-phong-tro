# Glass Living - Khoi dong server dev (PowerShell)
# Chay: .\start-dev.ps1
# Hoac click chuot phai -> Run with PowerShell

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host ""
Write-Host "===== Glass Living dev server =====" -ForegroundColor Cyan
Write-Host ""

# 1. Postgres
Write-Host "[1/4] Kiem tra PostgreSQL..." -ForegroundColor Yellow
$pg = Get-Service -Name "postgresql-x64-16" -ErrorAction SilentlyContinue
if (-not $pg) {
    Write-Host "  Khong tim thay service 'postgresql-x64-16'. Cai PostgreSQL 16 truoc." -ForegroundColor Red
    exit 1
}
if ($pg.Status -ne "Running") {
    Write-Host "  Postgres chua chay -> dang start (can quyen Admin)..." -ForegroundColor Yellow
    Start-Service "postgresql-x64-16"
}
Write-Host "  Postgres OK" -ForegroundColor Green

# 2. Database
Write-Host "[2/4] Kiem tra database 'glassliving'..." -ForegroundColor Yellow
$env:PGPASSWORD = "postgres"
$psql = "C:\Program Files\PostgreSQL\16\bin\psql.exe"
$exists = & $psql -h localhost -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='glassliving'"
if ($exists -ne "1") {
    Write-Host "  Tao database glassliving..." -ForegroundColor Yellow
    & $psql -h localhost -U postgres -d postgres -c "CREATE DATABASE glassliving WITH ENCODING='UTF8' TEMPLATE=template0;"
}
Write-Host "  Database OK" -ForegroundColor Green

# 3. Port 8080
Write-Host "[3/4] Kiem tra port 8080..." -ForegroundColor Yellow
$busy = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($busy) {
    Write-Host "  Port 8080 dang bi process $($busy.OwningProcess) chiem." -ForegroundColor Red
    Write-Host "  Chay '.\stop-dev.ps1' truoc, hoac 'taskkill /F /PID $($busy.OwningProcess)'" -ForegroundColor Red
    exit 1
}
Write-Host "  Port 8080 OK" -ForegroundColor Green

# 4. Build CSS (skip neu app.css moi hon input.css)
Write-Host "[4/4] Build CSS..." -ForegroundColor Yellow
$inFile  = ".\src\main\resources\static\css\input.css"
$outFile = ".\src\main\resources\static\css\app.css"
$needBuild = $true
if ((Test-Path $outFile) -and ((Get-Item $outFile).LastWriteTime -gt (Get-Item $inFile).LastWriteTime)) {
    $needBuild = $false
}
if ($needBuild) {
    if (-not (Test-Path ".\node_modules")) { npm install }
    npm run build:css
} else {
    Write-Host "  app.css moi hon input.css -> bo qua build" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "===== Khoi dong Spring Boot (Ctrl+C de tat) =====" -ForegroundColor Cyan
Write-Host "Mo browser: http://localhost:8080" -ForegroundColor Cyan
Write-Host "  owner@glass.living  / password123  -> /admin" -ForegroundColor DarkGray
Write-Host "  tenant@glass.living / password123  -> /me" -ForegroundColor DarkGray
Write-Host "  admin@glass.living  / password123  -> all" -ForegroundColor DarkGray
Write-Host ""

& .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
