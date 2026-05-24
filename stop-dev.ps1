# Glass Living - Tat server dev
# Chay: .\stop-dev.ps1

$ErrorActionPreference = "SilentlyContinue"

$conn = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($conn) {
    Write-Host "Tat process $($conn.OwningProcess) tren port 8080..." -ForegroundColor Yellow
    Stop-Process -Id $conn.OwningProcess -Force
    Write-Host "Da tat." -ForegroundColor Green
} else {
    Write-Host "Khong co process nao tren port 8080." -ForegroundColor DarkGray
}

# Don dep cac java.exe leftover (Spring DevTools spawn nhieu process)
$javas = Get-Process -Name "java" -ErrorAction SilentlyContinue
if ($javas) {
    Write-Host "Tim thay $($javas.Count) java.exe — tat het? (y/N): " -ForegroundColor Yellow -NoNewline
    $r = Read-Host
    if ($r -eq "y") {
        $javas | Stop-Process -Force
        Write-Host "Da tat." -ForegroundColor Green
    }
}
