[CmdletBinding()]
param([switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$url = 'http://127.0.0.1:18765/'
function Read-Plain([Security.SecureString]$value) {
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($value)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr); $value.Dispose() }
}

if (([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()).Port -contains 18765) {
    throw '端口 18765 已被使用。如果系统已经启动，请直接访问 http://127.0.0.1:18765/。'
}
New-Item -ItemType Directory -Path data -Force | Out-Null
$env:BOOTSTRAP_ADMIN_USERNAME = 'admin'
if (-not (Test-Path -LiteralPath 'data/classroom.mv.db') -and -not $env:BOOTSTRAP_ADMIN_PASSWORD) {
    $env:BOOTSTRAP_ADMIN_PASSWORD = Read-Plain (Read-Host '设置管理员 admin 的密码（至少 12 位）' -AsSecureString)
}
if (-not (Test-Path -LiteralPath 'data/classroom.mv.db') -and $env:BOOTSTRAP_ADMIN_PASSWORD.Length -lt 12) {
    throw '首次启动需要设置至少 12 位的管理员密码。'
}
if (-not $env:DEEPSEEK_API_KEY -and -not $NoBrowser) {
    $key = Read-Host '输入你自己的 DeepSeek API Key 启用云端 AI（直接回车可跳过）' -AsSecureString
    if ($key.Length) { $env:DEEPSEEK_API_KEY = Read-Plain $key } else { $key.Dispose() }
}
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 48
try { $random.GetBytes($bytes); $env:JWT_SECRET = [Convert]::ToBase64String($bytes) }
finally { $random.Dispose(); [Array]::Clear($bytes, 0, $bytes.Length) }
$env:AI_ENGINE = 'deepseek'
$env:AI_OCR_SCRIPT = Join-Path $PSScriptRoot 'Read-ImageText.ps1'
$job = $null
$drive = $null
try {
    $java = Join-Path $PSScriptRoot 'runtime\bin\java.exe'
    # 随包 Java 的 Windows 启动器在部分代码页下无法从中文路径加载 DLL。
    if ($PSScriptRoot -match '[^\x00-\x7F]') {
        foreach ($letter in 90..68) {
            $name = [string][char]$letter
            if (Get-PSDrive -Name $name -ErrorAction SilentlyContinue) { continue }
            $candidate = $name + ':'
            & subst.exe $candidate $PSScriptRoot
            if ($LASTEXITCODE -eq 0) { $drive = $candidate; break }
        }
        if (-not $drive) { throw '无法临时映射启动目录，请将便携版解压到纯英文路径后重试。' }
        Write-Host "[runtime-drive] $drive"
        Set-Location ($drive + '\')
        $java = $drive + '\runtime\bin\java.exe'
    }
    Write-Host "正在启动：$url  请保持此窗口打开。按 Ctrl+C 可停止服务。"
    if (-not $NoBrowser) {
        $job = Start-Job -ArgumentList $url -ScriptBlock {
            param($address)
            for ($i = 0; $i -lt 90; $i++) {
                try {
                    if ((Invoke-WebRequest $address -UseBasicParsing -TimeoutSec 1).StatusCode -eq 200) {
                        Start-Process $address
                        break
                    }
                } catch {}
                Start-Sleep -Seconds 1
            }
        }
    }
    & $java '-Dfile.encoding=UTF-8' -jar app.jar '--server.address=127.0.0.1' '--server.port=18765' `
        '--spring.profiles.active=portable' '--spring.web.resources.static-locations=file:./web/' `
        '--spring.datasource.url=jdbc:h2:file:./data/classroom;MODE=MySQL' '--spring.datasource.username=sa' `
        '--spring.datasource.password=' '--spring.flyway.baseline-on-migrate=false'
    if ($LASTEXITCODE -ne 0) { throw '服务启动失败，请查看上方错误。' }
} finally {
    if ($job) { Remove-Job $job -Force }
    if ($drive) { Set-Location -LiteralPath $PSScriptRoot; & subst.exe $drive /D }
    Remove-Item Env:BOOTSTRAP_ADMIN_PASSWORD,Env:DEEPSEEK_API_KEY,Env:JWT_SECRET -ErrorAction SilentlyContinue
}
