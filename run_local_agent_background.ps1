Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$python = "C:\Users\swg\AppData\Local\Programs\Python\Python312\python.exe"

if (-not (Test-Path $python)) {
  throw "Python runtime not found at $python"
}

Set-Location $projectRoot
$serverScript = Join-Path $projectRoot "serve_local_agent.py"
$process = Start-Process -FilePath $python -ArgumentList @($serverScript) -WorkingDirectory $projectRoot -PassThru
Write-Host "LocalAgent 1660 background server started with PID $($process.Id)"
