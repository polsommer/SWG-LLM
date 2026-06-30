Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$python = "C:\Users\swg\AppData\Local\Programs\Python\Python312\python.exe"

if (-not (Test-Path $python)) {
  throw "Python runtime not found at $python"
}

Write-Host "Starting LocalAgent 1660 from $projectRoot"
Write-Host "Using Python: $python"

Set-Location $projectRoot
& $python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
