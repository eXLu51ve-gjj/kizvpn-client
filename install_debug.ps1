# Script to build and install app on connected device
# Usage: .\install_debug.ps1

Write-Host "Building and installing app on connected device..." -ForegroundColor Green

# Change to project directory
Set-Location $PSScriptRoot

# Build and install app
.\gradlew.bat installDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nApp successfully installed on phone!" -ForegroundColor Green
    Write-Host "Open 'KIZ VPN' app on your phone" -ForegroundColor Yellow
} else {
    Write-Host "`nInstallation error. Check:" -ForegroundColor Red
    Write-Host "  1. Phone connected via USB" -ForegroundColor Yellow
    Write-Host "  2. USB debugging enabled on phone" -ForegroundColor Yellow
    Write-Host "  3. USB drivers installed" -ForegroundColor Yellow
    Write-Host "`nCheck connection: adb devices" -ForegroundColor Cyan
}
