# TeleTrack360 - Local Development Startup

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  TeleTrack360 Local Development" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Load environment variables from .env
Write-Host "Loading environment variables from .env..." -ForegroundColor Yellow
if (Test-Path ".env") {
    Get-Content .env | ForEach-Object {
        if ($_ -and $_ -notmatch '^#') {
            $parts = $_ -split '=', 2
            if ($parts.Length -eq 2) {
                $name = $parts[0].Trim()
                $value = $parts[1].Trim()
                $value = $value -replace '^"', '' -replace '"$', ''
                $value = $value -replace "^'", "" -replace "'$", ""
                [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
                Write-Host "  OK $name" -ForegroundColor Green
            }
        }
    }
    Write-Host ""
} else {
    Write-Host "  ERROR .env file not found!" -ForegroundColor Red
    exit 1
}

# Start infrastructure in Docker
Write-Host "Starting infrastructure services (Docker)..." -ForegroundColor Yellow
Write-Host "  - PostgreSQL (port 5433)" -ForegroundColor Gray
Write-Host "  - Kafka + Zookeeper" -ForegroundColor Gray
Write-Host "  - Eureka Discovery (port 8761)" -ForegroundColor Gray
docker-compose up -d postgres kafka zookeeper discovery-service
Write-Host ""

# Wait for infrastructure to be ready
Write-Host "Waiting for infrastructure to be ready..." -ForegroundColor Yellow
Start-Sleep -Seconds 20
Write-Host "  OK Infrastructure ready" -ForegroundColor Green
Write-Host ""

# Function to start a service in a new window
function Start-Service {
    param(
        [string]$ServiceName,
        [string]$ServicePath,
        [int]$Port
    )

    Write-Host "Starting $ServiceName on port $Port..." -ForegroundColor Cyan

    $script = @"
`$host.ui.RawUI.WindowTitle = '$ServiceName'
`$host.UI.RawUI.BackgroundColor = 'DarkBlue'
`$host.UI.RawUI.ForegroundColor = 'White'
Clear-Host
Write-Host '========================================'
Write-Host '  $ServiceName'
Write-Host '  Port: $Port'
Write-Host '========================================'
Write-Host ''
cd '$ServicePath'
mvn spring-boot:run
"@

    Start-Process powershell -ArgumentList "-NoExit", "-Command", $script
    Start-Sleep -Seconds 2
}

# Get current directory
$rootDir = (Get-Location).Path

# Start Config Service
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Starting Core Services" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Start-Service -ServiceName "Config Service" -ServicePath "$rootDir\config\config-service" -Port 8888

Write-Host "Waiting for Config Service to start..." -ForegroundColor Yellow
Start-Sleep -Seconds 25
Write-Host "  OK Config Service should be ready" -ForegroundColor Green
Write-Host ""

# Start Business Services
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Starting Business Services" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Start-Service -ServiceName "User Service" -ServicePath "$rootDir\services\user-service" -Port 8081
Start-Service -ServiceName "Incident Service" -ServicePath "$rootDir\services\incident-service" -Port 8082
Start-Service -ServiceName "Notification Service" -ServicePath "$rootDir\services\notification-service" -Port 8083
Start-Service -ServiceName "Reporting Service" -ServicePath "$rootDir\services\reporting-service" -Port 8084

Write-Host "Waiting for business services to start..." -ForegroundColor Yellow
Start-Sleep -Seconds 15
Write-Host ""

# Start API Gateway
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Starting API Gateway" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Start-Service -ServiceName "API Gateway" -ServicePath "$rootDir\gateway\api-gateway" -Port 8080

Write-Host "Waiting for API Gateway to start..." -ForegroundColor Yellow
Start-Sleep -Seconds 15
Write-Host ""

# Summary
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  All Services Started!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Infrastructure (Docker):" -ForegroundColor Cyan
Write-Host "  PostgreSQL:       localhost:5433" -ForegroundColor White
Write-Host "  Kafka:            localhost:9092" -ForegroundColor White
Write-Host "  Eureka:           http://localhost:8761" -ForegroundColor White
Write-Host ""
Write-Host "Services (Local):" -ForegroundColor Cyan
Write-Host "  Config Service:   http://localhost:8888" -ForegroundColor White
Write-Host "  User Service:     http://localhost:8081" -ForegroundColor White
Write-Host "  Incident Service: http://localhost:8082" -ForegroundColor White
Write-Host "  Notification:     http://localhost:8083" -ForegroundColor White
Write-Host "  Reporting:        http://localhost:8084" -ForegroundColor White
Write-Host "  API Gateway:      http://localhost:8080" -ForegroundColor White
Write-Host ""
Write-Host "Useful Links:" -ForegroundColor Cyan
Write-Host "  Eureka Dashboard: http://localhost:8761" -ForegroundColor Yellow
Write-Host "  API Gateway:      http://localhost:8080/api/v1/" -ForegroundColor Yellow
Write-Host ""
Write-Host "To stop all services:" -ForegroundColor Yellow
Write-Host "  1. Close each PowerShell window" -ForegroundColor Gray
Write-Host "  2. Run: docker-compose down" -ForegroundColor Gray
Write-Host ""
Write-Host "Press Ctrl+C to exit" -ForegroundColor Yellow

try {
    while ($true) { Start-Sleep -Seconds 1 }
} finally {
    Write-Host "Script terminated. Services still running." -ForegroundColor Yellow
}