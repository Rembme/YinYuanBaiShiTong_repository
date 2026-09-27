$projectRoot = Split-Path -Parent $PSScriptRoot
$nodeVersion = (& node --version 2>&1 | Out-String).Trim()
if (-not $nodeVersion) { throw '未找到 Node.js。请安装 Node.js 20.19 或更高版本。' }
Push-Location (Join-Path $projectRoot 'web')
try {
  if (-not (Test-Path -LiteralPath 'node_modules')) { npm install }
  npm run dev
} finally { Pop-Location }
