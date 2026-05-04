# ==========================================================================
# PayRoute Hub — start all backend services
# ==========================================================================
# Right-click → "Run with PowerShell"  (or run from any terminal):
#     .\start-all.ps1
#
# Each service launches in its own PowerShell window so logs stay visible
# and you can stop one without touching the others.
#
# Order matters only loosely (Eureka discovery is fault-tolerant), but we
# start the discovery server first and give it ~12 seconds to come up
# before launching the rest.
# ==========================================================================

$ErrorActionPreference = 'Stop'

# Resolve repository root (this script lives in payroute-hub\backend\)
$BackendDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  PayRoute Hub  ·  starting all backend services" -ForegroundColor Cyan
Write-Host "  Backend root: $BackendDir" -ForegroundColor DarkGray
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

# ---- Service launch order ----
# Tuple: <pretty name>, <module folder>, <port>
$services = @(
    @{ Name = 'Discovery Server'; Module = 'discovery-server'    ; Port = 8761 },
    @{ Name = 'IAM Service'     ; Module = 'iam-service'         ; Port = 8081 },
    @{ Name = 'Party Service'   ; Module = 'party-service'       ; Port = 8082 },
    @{ Name = 'Payment Service' ; Module = 'payment-service'     ; Port = 8083 },
    @{ Name = 'Routing Service' ; Module = 'routing-service'     ; Port = 8084 },
    @{ Name = 'Ledger Service'  ; Module = 'ledger-service'      ; Port = 8085 },
    @{ Name = 'Notification Svc'; Module = 'notification-service'; Port = 8086 },
    @{ Name = 'Compliance Svc'  ; Module = 'compliance-service'  ; Port = 8087 },
    @{ Name = 'Exception Svc'   ; Module = 'exception-service'   ; Port = 8088 },
    @{ Name = 'Settlement Svc'  ; Module = 'settlement-service'  ; Port = 8089 },
    @{ Name = 'API Gateway'     ; Module = 'api-gateway'         ; Port = 9080 }
)

function Start-Service-Window {
    param([string]$Name, [string]$Module, [int]$Port)

    $servicePath = Join-Path $BackendDir $Module
    if (-not (Test-Path $servicePath)) {
        Write-Host "  [SKIP] $Name  — folder not found: $servicePath" -ForegroundColor Yellow
        return
    }

    $title = "PayRoute - $Name (port $Port)"
    # Build a single-line command that:
    #   1. Sets a friendly window title
    #   2. cd's into the service folder
    #   3. Runs mvn spring-boot:run
    #   4. Pauses on exit so the user can read the last error line
    $cmd = "`$Host.UI.RawUI.WindowTitle = '$title'; " +
           "Set-Location -Path '$servicePath'; " +
           "Write-Host 'Starting $Name on port $Port ...' -ForegroundColor Cyan; " +
           "mvn spring-boot:run; " +
           "Write-Host ''; " +
           "Write-Host '$Name has stopped. Press Enter to close this window.' -ForegroundColor Yellow; " +
           "Read-Host"

    Start-Process -FilePath 'powershell.exe' `
                  -ArgumentList '-NoExit', '-NoProfile', '-Command', $cmd `
                  -WorkingDirectory $servicePath | Out-Null

    Write-Host ("  [START] {0,-20}  port {1}" -f $Name, $Port) -ForegroundColor Green
}

# ---- Discovery first ----
Start-Service-Window -Name $services[0].Name -Module $services[0].Module -Port $services[0].Port

Write-Host ""
Write-Host "  Waiting 12s for Eureka to come up before starting the rest..." -ForegroundColor DarkGray
Start-Sleep -Seconds 12
Write-Host ""

# ---- All other services in parallel ----
foreach ($svc in $services[1..($services.Count - 1)]) {
    Start-Service-Window -Name $svc.Name -Module $svc.Module -Port $svc.Port
    Start-Sleep -Milliseconds 600  # tiny stagger to avoid Maven dependency-resolve thrash
}

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  All services launching. Useful URLs:" -ForegroundColor Cyan
Write-Host "    Eureka dashboard : http://localhost:8761" -ForegroundColor DarkGray
Write-Host "    API gateway      : http://localhost:9080" -ForegroundColor DarkGray
Write-Host "    Frontend (run separately) : http://localhost:3000" -ForegroundColor DarkGray
Write-Host ""
Write-Host "  To shut everything down, run: .\stop-all.ps1" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""
