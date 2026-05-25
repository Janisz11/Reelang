$ErrorActionPreference = "SilentlyContinue"
$cloudflared = "C:\Program Files (x86)\cloudflared\cloudflared.exe"
$backendPath = "C:\Users\kjani\ReeLang-Repo\backend"
$apiClientPath = "C:\Users\kjani\ReeLang-Repo\android\app\src\main\java\com\example\reelang\network\ApiClient.kt"
$logFile = "C:\Users\kjani\ReeLang-Repo\cloudflared.log"

Write-Host "Starting ReeLang dev environment..." -ForegroundColor Cyan

# Start docker-compose
Start-Process powershell -ArgumentList "-NoProfile -Command `"cd '$backendPath'; docker-compose up`"" -WindowStyle Minimized

Write-Host "Docker starting..." -ForegroundColor Yellow

# Start cloudflared with log file
if (Test-Path $logFile) { Remove-Item $logFile }
Start-Process -FilePath $cloudflared -ArgumentList "tunnel --url http://localhost:8000" -RedirectStandardError $logFile -WindowStyle Hidden

Write-Host "Cloudflare tunnel starting..." -ForegroundColor Yellow

$url = $null
$attempts = 0
while ($null -eq $url -and $attempts -lt 40) {
    Start-Sleep -Seconds 1
    $attempts++
    if (Test-Path $logFile) {
        $content = Get-Content $logFile -Raw
        if ($content -match "https://[a-z0-9-]+\.trycloudflare\.com") {
            $url = $matches[0]
        }
    }
}

if ($url) {
    Write-Host ""
    Write-Host "=====================================" -ForegroundColor Green
    Write-Host "Tunnel URL: $url" -ForegroundColor Green
    Write-Host "API URL:    $url/api/v1/" -ForegroundColor Cyan
    Write-Host "=====================================" -ForegroundColor Green
    Write-Host "NOTE: ApiClient.kt uses static Railway URL — no update needed." -ForegroundColor Yellow
    $url + "/api/v1/" | Set-Clipboard
    Write-Host "(Tunnel URL copied to clipboard for reference)" -ForegroundColor Green
} else {
    Write-Host "Failed to get tunnel URL - check cloudflared.log" -ForegroundColor Red
}

Write-Host "Press Enter to stop..." -ForegroundColor Gray
Read-Host