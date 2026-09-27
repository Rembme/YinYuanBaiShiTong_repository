$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envFile) {
  foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
    $separator = $trimmed.IndexOf('=')
    if ($separator -lt 1) { continue }
    $name = $trimmed.Substring(0, $separator).Trim()
    $value = $trimmed.Substring($separator + 1).Trim().Trim('"').Trim("'")
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
  }
}
$javaExecutable = 'java'
if ($env:JAVA_HOME) {
  $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
  if (Test-Path -LiteralPath $candidate) {
    $javaExecutable = $candidate
    $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + [IO.Path]::PathSeparator + $env:PATH
  }
}
$versionText = (& $javaExecutable -version 2>&1 | Out-String)
$major = 0
if ($versionText -match 'version "1\.(\d+)') { $major = [int]$Matches[1] }
elseif ($versionText -match 'version "(\d+)') { $major = [int]$Matches[1] }
if ($major -lt 17) { throw "需要 JDK 17 或更高版本；当前 Java 版本未达到要求。请安装 JDK 后重新打开 PowerShell。" }
Push-Location (Join-Path $projectRoot 'server')
try { mvn spring-boot:run } finally { Pop-Location }
