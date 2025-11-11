# Script to check connected Android devices
# Usage: .\check_device.ps1

Write-Host "Checking connected Android devices..." -ForegroundColor Cyan
Write-Host ""

# Check if ADB is available
$adbPath = Get-Command adb -ErrorAction SilentlyContinue

if (-not $adbPath) {
    Write-Host "ADB not found in PATH" -ForegroundColor Red
    Write-Host "Try using Android Studio to run the app" -ForegroundColor Yellow
    exit 1
}

# Check connected devices
$devices = adb devices

Write-Host $devices

# Parse output
$deviceLines = $devices | Select-Object -Skip 1 | Where-Object { $_ -match "device" }

if ($deviceLines.Count -eq 0) {
    Write-Host "`nNo devices found!" -ForegroundColor Red
    Write-Host "`nWhat to check:" -ForegroundColor Yellow
    Write-Host "  1. Phone connected via USB" -ForegroundColor White
    Write-Host "  2. USB debugging enabled (Settings -> Developer options -> USB debugging)" -ForegroundColor White
    Write-Host "  3. Allow USB debugging on phone (confirmation dialog should appear)" -ForegroundColor White
    Write-Host "  4. USB drivers installed" -ForegroundColor White
} else {
    Write-Host "`nFound devices: $($deviceLines.Count)" -ForegroundColor Green
    Write-Host "You can run the app!" -ForegroundColor Green
}
