[CmdletBinding()]
param([switch]$Verify,[switch]$UseIsolatedBuild,
      [ValidateSet('target','target-verification','target-phase6','target-phase7')][string]$BuildDirectory='target-phase7')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$secretDir = Join-Path $projectRoot '.secrets'
$logDir = Join-Path $projectRoot '.test-data'
$selectedBuild = if ($UseIsolatedBuild) { 'target-verification' } else { $BuildDirectory }
$jar = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot ('backend/' + $selectedBuild)) -Filter 'class-management-*.jar' -File -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending)[0].FullName
$reportPath = Join-Path $secretDir 'local-backend-verification.json'
if (-not (Test-Path -LiteralPath $jar)) { throw ('构建产物不存在：' + $selectedBuild + '。请先按 README 构建相应版本。') }
$portCheck = New-Object Net.Sockets.TcpClient
try {
    $portOpen = $false
    try { $portCheck.Connect('127.0.0.1',8080); $portOpen=$true } catch [Net.Sockets.SocketException] { }
    if ($portOpen) { throw '端口 8080 已被占用；请先确认已有服务，不会自动终止进程。' }
} finally { $portCheck.Dispose() }
$config = @{}
Get-Content -LiteralPath (Join-Path $secretDir 'mysql.env') | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $config[$matches[1].Trim()] = $matches[2].Trim().Trim([char]34).Trim([char]39) }
}
if ([string]::IsNullOrWhiteSpace($config['SPRING_DATASOURCE_PASSWORD'])) { throw '缺少本地项目数据库密码。' }
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
Write-Host '启动本地后端：class_management_app -> class_management，端口 8080。' -ForegroundColor Cyan
Write-Host '请设置系统 admin 的登录密码（至少12位）；已有 admin 时输入原密码。'
Write-Host '这是班级管理系统的密码，不是 MySQL root 密码；不会保存或显示。'
$adminPassword = Read-Host '系统 admin 密码' -AsSecureString
if ($adminPassword.Length -lt 12) { $adminPassword.Dispose(); $config.Clear(); throw '系统密码至少12位，请重新运行。' }
$environmentNames = @('DB_URL','DB_USERNAME','DB_PASSWORD','JWT_SECRET','BOOTSTRAP_ADMIN_USERNAME','BOOTSTRAP_ADMIN_PASSWORD','SERVER_PORT','DEEPSEEK_API_KEY')
$previous = @{}
foreach ($name in $environmentNames) { $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminPassword)
$backend = $null
try {
    $deepSeekKeyPath = Join-Path $secretDir 'deepseek-key.dpapi'
    if ([string]::IsNullOrWhiteSpace($env:DEEPSEEK_API_KEY) -and (Test-Path -LiteralPath $deepSeekKeyPath)) {
        Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Security\Microsoft.PowerShell.Security.psd1') -ErrorAction Stop
        $deepSeekSecure = ConvertTo-SecureString ([IO.File]::ReadAllText($deepSeekKeyPath))
        $deepSeekPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($deepSeekSecure)
        try { $env:DEEPSEEK_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($deepSeekPtr).Trim() }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($deepSeekPtr); $deepSeekSecure.Dispose() }
    }
    $env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/class_management?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
    $env:DB_USERNAME = 'class_management_app'
    $env:DB_PASSWORD = $config['SPRING_DATASOURCE_PASSWORD']
    if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
        $bytes = New-Object byte[] 48
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $random.GetBytes($bytes) } finally { $random.Dispose() }
        $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
        [Array]::Clear($bytes,0,$bytes.Length)
    }
    $env:BOOTSTRAP_ADMIN_USERNAME = 'admin'
    $env:BOOTSTRAP_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $env:SERVER_PORT = '8080'
    # Java 的密码和密钥仅通过子进程环境传递，命令行和文件中均不写入。
    $backend = Start-Process -FilePath 'java.exe' -ArgumentList ('-Dfile.encoding=UTF-8 -jar "' + $jar + '"') -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDir 'local-backend.log') -RedirectStandardError (Join-Path $logDir 'local-backend-error.log')
} finally {
    foreach ($name in $environmentNames) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    $config.Clear()
}
try {
    $ready = $false
    for ($attempt=0; $attempt -lt 45; $attempt++) {
        if ($backend.HasExited) { throw '后端提前退出，请检查 .test-data/local-backend.log 和 local-backend-error.log。' }
        try { Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8080/api/students' -TimeoutSec 2 | Out-Null }
        catch { if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401) { $ready=$true; break } }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw '后端未在预期时间就绪，请检查日志。' }
    $report = [ordered]@{ started=$true; pid=$backend.Id; verifiedAt=[DateTimeOffset]::Now.ToString('o'); database='class_management'; databaseAccount='class_management_app'; unauthenticatedRejected=$true }
    if ($Verify) {
        $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminPassword)
        try {
            $body = @{username='admin';password=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)} | ConvertTo-Json -Compress
            $login = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/auth/login' -ContentType 'application/json;charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
        } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr); $body=$null }
        if ($login.code -ne 0 -or -not $login.data.token) { throw '系统管理员登录验证失败。' }
        $headers = @{Authorization='Bearer ' + $login.data.token}
        try {
            $me = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/auth/me' -Headers $headers
            if ($me.data.role -ne 'ADMIN') { throw '系统管理员角色不正确。' }
            $classes = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/classes' -Headers $headers
            $students = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/students?page=0&size=10' -Headers $headers
            if ($classes.code -ne 0 -or $students.code -ne 0) { throw '业务接口验证失败。' }
            $report.adminLoginVerified=$true
            $report.classApiVerified=$true
            $report.studentApiVerified=$true
            $report.studentTotal=[long]$students.data.total
            $report.classTotal=@($classes.data).Count
        } finally { $headers.Clear(); $login=$null }
    }
    [IO.File]::WriteAllText($reportPath,($report | ConvertTo-Json -Depth 5),(New-Object Text.UTF8Encoding($false)))
    Write-Host '后端已启动，登录和接口检查完成。密码和令牌未保存。' -ForegroundColor Green
    Write-Host ('后端 PID：' + $backend.Id)
    Write-Host $reportPath
    Write-Host '关闭当前窗口不影响已启动的后端。本地随机 JWT 密钥重启后会变化，需重新登录。'
} catch {
    $failure = [ordered]@{started=$false;pid=$backend.Id;verifiedAt=[DateTimeOffset]::Now.ToString('o');error=$_.Exception.Message}
    [IO.File]::WriteAllText($reportPath,($failure | ConvertTo-Json -Depth 3),(New-Object Text.UTF8Encoding($false)))
    throw
} finally { $adminPassword.Dispose() }
