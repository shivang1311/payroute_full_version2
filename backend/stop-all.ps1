# ==========================================================================
# PayRoute Hub — stop all backend services
# ==========================================================================
# Right-click → "Run with PowerShell"  (or run from any terminal):
#     .\stop-all.ps1
#
# Identifies the listening process on each PayRoute port and terminates it.
# Idempotent — ports that aren't listening are silently skipped.
# ==========================================================================

$ports = @(
    @{ Name = 'Discovery Server'; Port = 8761 },
    @{ Name = 'IAM Service'     ; Port = 8081 },
    @{ Name = 'Party Service'   ; Port = 8082 },
    @{ Name = 'Payment Service' ; Port = 8083 },
    @{ Name = 'Routing Service' ; Port = 8084 },
    @{ Name = 'Ledger Service'  ; Port = 8085 },
    @{ Name = 'Notification Svc'; Port = 8086 },
    @{ Name = 'Compliance Svc'  ; Port = 8087 },
    @{ Name = 'Exception Svc'   ; Port = 8088 },
    @{ Name = 'Settlement Svc'  ; Port = 8089 },
    @{ Name = 'API Gateway'     ; Port = 9080 }
)

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  PayRoute Hub  ·  stopping all backend services" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

$stopped = 0
$skipped = 0

foreach ($svc in $ports) {
    $conn = Get-NetTCPConnection -LocalPort $svc.Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $conn) {
        Write-Host ("  [SKIP] {0,-20}  port {1}  (not running)" -f $svc.Name, $svc.Port) -ForegroundColor DarkGray
        $skipped++
        continue
    }

    $procId = $conn.OwningProcess
    try {
        Stop-Process -Id $procId -Force -ErrorAction Stop
        Write-Host ("  [STOP] {0,-20}  port {1}  (PID {2})" -f $svc.Name, $svc.Port, $procId) -ForegroundColor Green
        $stopped++
    } catch {
        Write-Host ("  [FAIL] {0,-20}  port {1}  PID {2}: {3}" -f $svc.Name, $svc.Port, $procId, $_.Exception.Message) -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "  Stopped: $stopped   Skipped: $skipped" -ForegroundColor Cyan
Write-Host ""

# Optional: also close the spawned PowerShell windows that ran the services.
# (They print "Press Enter to close" after the JVM exits, which is the expected behaviour.)
