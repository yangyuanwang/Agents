# Start spring-ai-qdrant-rag locally:
#   - JDK 17 (the machine default is 1.8, Spring Boot 3.5 requires 17+)
#   - MySQL stays local (127.0.0.1:3306, database rag)
#   - Qdrant lives on the Dify server, reached through the SSH tunnel (127.0.0.1:6334)
#
# Prerequisite:
#   python C:\Users\91947\Documents\ChatGPT\Dify\.deploy\tunnel.py
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\start-backend.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

$env:JAVA_HOME = "D:\Program Files\Java\jdk-17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# --- application profile / model providers ---
$env:SPRING_PROFILES_ACTIVE = "ollama"
# --- Ollama lives on the Dify server too (tunneled 11434) ---
$env:OLLAMA_BASE_URL = "http://127.0.0.1:11434"
$env:OLLAMA_EMBEDDING_MODEL = "nomic-embed-text"

# --- Qdrant on the Dify server, via tunnel ---
$env:QDRANT_HOST = "127.0.0.1"
$env:QDRANT_GRPC_PORT = "6334"
$env:QDRANT_API_KEY = "fa7ee563ea8cba7e5122a31415dbd9f8"
$env:QDRANT_COLLECTION = "knowledge_base"

# --- MySQL local ---
$env:MYSQL_URL = "jdbc:mysql://127.0.0.1:3306/rag?useSSL=false&serverTimezone=Asia/Shanghai"
$env:MYSQL_USER = "root"
$env:MYSQL_PASSWORD = "123456"

foreach ($p in @(6334, 11434, 3306)) {
  $r = Test-NetConnection -ComputerName 127.0.0.1 -Port $p -WarningAction SilentlyContinue
  if (-not $r.TcpTestSucceeded) {
    Write-Host "[FAIL] 127.0.0.1:$p is not reachable." -ForegroundColor Red
    if ($p -eq 6334 -or $p -eq 11434) {
      Write-Host "       Start the tunnel: python C:\Users\91947\Documents\ChatGPT\Dify\.deploy\tunnel.py" -ForegroundColor Yellow
    }
    exit 1
  }
}

Push-Location $root
try {
  mvn spring-boot:run
}
finally {
  Pop-Location
}