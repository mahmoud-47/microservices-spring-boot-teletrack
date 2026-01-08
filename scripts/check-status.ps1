# ============================================
# TeleTrack360 - Check Service Status
# ============================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Service Health Check" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

function Test-Service {
    param(
        [string]$Name,
        [string]$Url
    )

    try {
        $response = Invoke-WebRequest -Uri $Url -TimeoutSec 5 -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Host "  ✓ $Name is UP" -ForegroundColor Green
            return $true
        }
    } catch {
        Write-Host "  ✗ $Name is DOWN" -ForegroundColor Red
        return $false
    }
}

Write-Host "Infrastructure (Docker):" -ForegroundColor Yellow
Test-Service -Name "Eureka Discovery" -Url "http://localhost:8761/actuator/health"
Write-Host ""

Write-Host "Services (Local):" -ForegroundColor Yellow
Test-Service -Name "Config Service  " -Url "http://localhost:8888/actuator/health"
Test-Service -Name "User Service    " -Url "http://localhost:8081/actuator/health"
Test-Service -Name "Incident Service" -Url "http://localhost:8082/actuator/health"
Test-Service -Name "Notification    " -Url "http://localhost:8083/actuator/health"
Test-Service -Name "Reporting       " -Url "http://localhost:8084/actuator/health"
Test-Service -Name "API Gateway     " -Url "http://localhost:8080/actuator/health"

Write-Host ""
Write-Host "Dashboard: http://localhost:8761" -ForegroundColor Cyan