# Glass Living - one-command local launcher
# Run from PowerShell or CMD:
#   .\open-local.cmd

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$Port = 8080
$BaseUrl = "http://localhost:$Port"
$HealthUrl = "$BaseUrl/actuator/health"
$OutLog = Join-Path $PSScriptRoot "app.out.log"
$ErrLog = Join-Path $PSScriptRoot "app.err.log"

function Write-Step($Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Test-Url($Url) {
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
        return ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500)
    } catch {
        return $false
    }
}

function Open-Browser {
    Write-Host "Opening $BaseUrl ..." -ForegroundColor Green
    Start-Process $BaseUrl
    Write-Host ""
    Write-Host "Login test:" -ForegroundColor Cyan
    Write-Host "  owner@glass.living  / password123"
    Write-Host "  tenant@glass.living / password123"
    Write-Host "  admin@glass.living  / password123"
}

function Ensure-Postgres {
    Write-Step "Checking PostgreSQL"
    $serviceName = "postgresql-x64-16"
    $service = Get-Service -Name $serviceName -ErrorAction SilentlyContinue
    if (-not $service) {
        Write-Host "PostgreSQL service '$serviceName' was not found. I will continue, but the app needs PostgreSQL running." -ForegroundColor Yellow
        return
    }

    if ($service.Status -ne "Running") {
        Write-Host "Starting PostgreSQL service..." -ForegroundColor Yellow
        Start-Service $serviceName
        (Get-Service -Name $serviceName).WaitForStatus("Running", "00:00:20")
    }

    Write-Host "PostgreSQL is running." -ForegroundColor Green
}

function Ensure-Database {
    Write-Step "Checking database"
    $psqlCandidates = @(
        "C:\Program Files\PostgreSQL\16\bin\psql.exe",
        "C:\Program Files\PostgreSQL\17\bin\psql.exe",
        "C:\Program Files\PostgreSQL\15\bin\psql.exe"
    )
    $psql = $psqlCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $psql) {
        $cmd = Get-Command psql.exe -ErrorAction SilentlyContinue
        if ($cmd) { $psql = $cmd.Source }
    }

    if (-not $psql) {
        Write-Host "Could not find psql.exe. Skipping database auto-create." -ForegroundColor Yellow
        return
    }

    $env:PGPASSWORD = "postgres"
    $exists = & $psql -h localhost -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='glassliving'" 2>$null
    if (($exists | Out-String).Trim() -ne "1") {
        Write-Host "Creating database glassliving..." -ForegroundColor Yellow
        & $psql -h localhost -U postgres -d postgres -c "CREATE DATABASE glassliving WITH ENCODING='UTF8' TEMPLATE=template0;"
    }
    Write-Host "Database is ready." -ForegroundColor Green
}

function Ensure-PortFreeOrOpen {
    if (Test-Url $HealthUrl -or Test-Url $BaseUrl) {
        Write-Step "App is already running"
        Open-Browser
        exit 0
    }

    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $conn) {
        return
    }

    $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$($conn.OwningProcess)" -ErrorAction SilentlyContinue
    $commandLine = if ($processInfo) { $processInfo.CommandLine } else { "" }
    if ($commandLine -match "glass-living|quanlyphongtronew") {
        Write-Host "Port $Port is used by this project but it is not responding. Stopping stale process $($conn.OwningProcess)..." -ForegroundColor Yellow
        Stop-Process -Id $conn.OwningProcess -Force
        Start-Sleep -Seconds 2
        return
    }

    throw "Port $Port is already used by process $($conn.OwningProcess). Close it first, then run .\open-local.cmd again."
}

function Build-Css {
    Write-Step "Building CSS"
    if (-not (Test-Path "node_modules")) {
        npm install
    }

    $input = "src\main\resources\static\css\input.css"
    $output = "src\main\resources\static\css\app.css"
    $needsBuild = -not (Test-Path $output)
    if (-not $needsBuild -and (Test-Path $input)) {
        $needsBuild = (Get-Item $input).LastWriteTime -gt (Get-Item $output).LastWriteTime
    }

    if ($needsBuild) {
        npm run build:css
    } else {
        Write-Host "CSS is up to date." -ForegroundColor DarkGray
    }
}

function Build-Jar {
    Write-Step "Building Spring Boot jar"
    & .\mvnw.cmd -DskipTests package
}

function Start-App {
    Write-Step "Starting Glass Living"
    if (Test-Path $OutLog) { Clear-Content $OutLog }
    if (Test-Path $ErrLog) { Clear-Content $ErrLog }

    $process = Start-Process -FilePath "java" `
        -ArgumentList @("-jar", "target\glass-living.jar", "--spring.profiles.active=dev") `
        -WorkingDirectory $PSScriptRoot `
        -RedirectStandardOutput $OutLog `
        -RedirectStandardError $ErrLog `
        -PassThru `
        -WindowStyle Hidden

    Write-Host "Started process $($process.Id). Waiting for $BaseUrl ..." -ForegroundColor Yellow
    for ($i = 1; $i -le 75; $i++) {
        if (Test-Url $HealthUrl -or Test-Url $BaseUrl) {
            Write-Host "Glass Living is ready." -ForegroundColor Green
            Open-Browser
            return
        }
        Start-Sleep -Seconds 1
    }

    Write-Host ""
    Write-Host "App did not become ready in time. Last app.out.log lines:" -ForegroundColor Red
    if (Test-Path $OutLog) { Get-Content $OutLog -Tail 60 }
    Write-Host ""
    Write-Host "Last app.err.log lines:" -ForegroundColor Red
    if (Test-Path $ErrLog) { Get-Content $ErrLog -Tail 60 }
    exit 1
}

Write-Host "Glass Living local launcher" -ForegroundColor Magenta
Ensure-PortFreeOrOpen
Ensure-Postgres
Ensure-Database
Build-Css
Build-Jar
Start-App

