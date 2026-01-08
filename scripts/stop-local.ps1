# ============================================
# TeleTrack360 - Stop Local Services
# ============================================

Write-Host "========================================" -ForegroundColor Red
Write-Host "  Stopping TeleTrack360 Services" -ForegroundColor Red
Write-Host "========================================" -ForegroundColor Red
Write-Host ""

# Stop Docker containers
Write-Host "Stopping Docker containers..." -ForegroundColor Yellow
docker-compose down
Write-Host "  ✓ Docker containers stopped" -ForegroundColor Green
Write-Host ""

# Kill Maven processes
Write-Host "Stopping local services..." -ForegroundColor Yellow
$mavenProcesses = Get-Process -Name "java" -ErrorAction SilentlyContinue |
                  Where-Object { $_.CommandLine -like "*spring-boot*" }

if ($mavenProcesses) {
    $mavenProcesses | ForEach-Object {
        Write-Host "  • Stopping process $($_.Id)..." -ForegroundColor Gray
        Stop-Process -Id $_.Id -Force
    }
    Write-Host "  ✓ All local services stopped" -ForegroundColor Green
} else {
    Write-Host "  • No running services found" -ForegroundColor Gray
}

Write-Host ""
Write-Host "All services stopped!" -ForegroundColor Green