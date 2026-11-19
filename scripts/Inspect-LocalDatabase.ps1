[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9_.-]+$')][string]$MySqlUser = 'root',
    [ValidatePattern('^[A-Za-z0-9_.-]+$')][string]$MySqlHost = '127.0.0.1',
    [ValidateRange(1,65535)][int]$MySqlPort = 3306,
    [switch]$PrepareApplication
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$reportDir = Join-Path $projectRoot '.secrets'
$reportPath = Join-Path $reportDir 'local-database-audit.json'
$mysqlPath = Join-Path $(if($env:MYSQL_BIN){$env:MYSQL_BIN}else{Join-Path $env:ProgramFiles 'MySQL\MySQL Server 8.4\bin'}) 'mysql.exe'
if (-not (Test-Path -LiteralPath $mysqlPath)) { throw 'MySQL 8.4 client not found.' }
New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
$sql = @"
SELECT JSON_OBJECT(
 'serverVersion',VERSION(),
 'authenticatedAccount',CURRENT_USER(),
 'databases',(SELECT JSON_ARRAYAGG(SCHEMA_NAME) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('class_management','smart_class_management')),
 'tables',(SELECT JSON_ARRAYAGG(JSON_OBJECT('schema',TABLE_SCHEMA,'name',TABLE_NAME,'engine',ENGINE,'collation',TABLE_COLLATION)) FROM information_schema.TABLES WHERE TABLE_SCHEMA IN ('class_management','smart_class_management')),
 'columns',(SELECT JSON_ARRAYAGG(JSON_OBJECT('schema',TABLE_SCHEMA,'table',TABLE_NAME,'name',COLUMN_NAME,'type',COLUMN_TYPE,'nullable',IS_NULLABLE,'default',COLUMN_DEFAULT,'extra',EXTRA)) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA IN ('class_management','smart_class_management') AND TABLE_NAME IN ('users','classes','students','flyway_schema_history')),
 'constraints',(SELECT JSON_ARRAYAGG(JSON_OBJECT('schema',CONSTRAINT_SCHEMA,'table',TABLE_NAME,'name',CONSTRAINT_NAME,'type',CONSTRAINT_TYPE)) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA IN ('class_management','smart_class_management') AND TABLE_NAME IN ('users','classes','students')),
 'keys',(SELECT JSON_ARRAYAGG(JSON_OBJECT('schema',TABLE_SCHEMA,'table',TABLE_NAME,'constraint',CONSTRAINT_NAME,'column',COLUMN_NAME,'position',ORDINAL_POSITION,'referencedTable',REFERENCED_TABLE_NAME,'referencedColumn',REFERENCED_COLUMN_NAME)) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA IN ('class_management','smart_class_management') AND TABLE_NAME IN ('users','classes','students'))
);
"@
function Invoke-MySql([string]$Query, [Security.SecureString]$Password, [string]$LoginUser = $MySqlUser) {
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Password)
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = $mysqlPath
    $start.Arguments = '--connect-timeout=10 --protocol=tcp --default-character-set=utf8mb4 --batch --raw --skip-column-names --host=' + $MySqlHost + ' --port=' + $MySqlPort + ' --user=' + $LoginUser
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.StandardOutputEncoding = [Text.Encoding]::UTF8
    try {
        $start.EnvironmentVariables['MYSQL_PWD'] = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
        $process = New-Object Diagnostics.Process
        $process.StartInfo = $start
        [void]$process.Start()
    } finally {
        $start.EnvironmentVariables.Remove('MYSQL_PWD')
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
    try {
        $process.StandardInput.WriteLine('SET NAMES utf8mb4;')
        $process.StandardInput.WriteLine($Query)
        $process.StandardInput.Close()
        $outputTask = $process.StandardOutput.ReadToEndAsync()
        $errorTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(30000)) { $process.Kill(); throw 'Database inspection timed out; no database changes were requested.' }
        $output = $outputTask.GetAwaiter().GetResult()
        $errorOutput = $errorTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw $errorOutput.Trim() }
        return $output.Trim()
    } finally { $process.Dispose() }
}
if ($PrepareApplication) {
    Write-Host '准备空的 class_management 库：创建专用账号并授予该库权限；不重置任何已有密码。' -ForegroundColor Cyan
} else {
    Write-Host '本地数据库只读检查：不会修改密码、表结构或业务数据。' -ForegroundColor Cyan
}
Write-Host ('连接：{0}@{1}:{2}' -f $MySqlUser,$MySqlHost,$MySqlPort)
Write-Host '请输入 MySQL Workbench 当前使用的密码。密码不会显示或保存。'
$password = Read-Host 'MySQL 密码' -AsSecureString
try {
    $metadata = Invoke-MySql -Query $sql -Password $password | ConvertFrom-Json
    $counts = @()
    $migrations = @()
    foreach ($table in $metadata.tables) {
        if ($table.name -in @('users','classes','students','flyway_schema_history')) {
            # 数据库和表名必须同时通过固定白名单，不能来自任意外部输入。
            if ($table.schema -notin @('class_management','smart_class_management')) { throw 'Unexpected database name.' }
            $qualified = '`' + $table.schema + '`.`' + $table.name + '`'
            $count = Invoke-MySql -Query ('SELECT COUNT(*) FROM ' + $qualified + ';') -Password $password
            $counts += [pscustomobject]@{ schema=$table.schema; table=$table.name; count=[long]$count }
            if ($table.name -eq 'flyway_schema_history') {
                $history = Invoke-MySql -Query ("SELECT JSON_ARRAYAGG(JSON_OBJECT('version',version,'description',description,'script',script,'checksum',checksum,'success',success)) FROM " + $qualified + ';') -Password $password
                $migrations += [pscustomobject]@{schema=$table.schema; history=@($history | ConvertFrom-Json | ForEach-Object { $_ })}
            }
        }
    }
    $report = [ordered]@{ connected=$true; checkedAt=[DateTimeOffset]::Now.ToString('o'); host=$MySqlHost; port=$MySqlPort; metadata=$metadata; counts=$counts; migrations=$migrations }
    if ($PrepareApplication) {
        if ('class_management' -notin $metadata.databases) { throw 'class_management 数据库不存在，停止操作。' }
        $existingTables = @($metadata.tables | Where-Object { $_.schema -eq 'class_management' })
        if ($existingTables.Count -ne 0) { throw '数据库已包含表；本脚本只准备已确认的空库，停止自动操作。' }
        $config = @{}
        Get-Content -LiteralPath (Join-Path $projectRoot '.secrets/mysql.env') | ForEach-Object {
            if ($_ -match '^([^#=]+)=(.*)$') { $config[$matches[1].Trim()] = $matches[2].Trim().Trim([char]34).Trim([char]39) }
        }
        $appPassword = $config['SPRING_DATASOURCE_PASSWORD']
        if ([string]::IsNullOrWhiteSpace($appPassword) -or $appPassword.Length -lt 12) { throw '已有本地配置缺少足够长度的项目密码。' }
        $appUser = 'class_management_app'
        $exists = Invoke-MySql -Query "SELECT COUNT(*) FROM mysql.user WHERE User='class_management_app' AND Host='localhost';" -Password $password
        $appSecure = New-Object Security.SecureString
        foreach ($character in $appPassword.ToCharArray()) { $appSecure.AppendChar($character) }
        $appSecure.MakeReadOnly()
        try {
            if ([int]$exists -eq 0) {
                $literal = $appPassword.Replace("'","''")
                $create = "SET SESSION sql_mode='NO_BACKSLASH_ESCAPES'; CREATE USER 'class_management_app'@'localhost' IDENTIFIED BY '" + $literal + "';"
                Invoke-MySql -Query $create -Password $password | Out-Null
                $create = $null; $literal = $null
            } else {
                # 已存在的账号必须能通过原密码认证，禁止通过重置密码接管。
                Invoke-MySql -Query 'SELECT 1;' -Password $appSecure -LoginUser $appUser | Out-Null
            }
            Invoke-MySql -Query "GRANT ALL PRIVILEGES ON class_management.* TO 'class_management_app'@'localhost';" -Password $password | Out-Null
            $appAccount = Invoke-MySql -Query 'SELECT CURRENT_USER();' -Password $appSecure -LoginUser $appUser
            $report.applicationReady = $true
            $report.applicationAccount = $appAccount
            $report.database = 'class_management'
        } finally {
            $appSecure.Dispose(); $appPassword = $null; $config.Clear()
        }
    }
    [IO.File]::WriteAllText($reportPath,($report | ConvertTo-Json -Depth 15),(New-Object Text.UTF8Encoding($false)))
    Write-Host '连接成功。检查报告已生成，未读取学生姓名、学号或账号密码。' -ForegroundColor Green
    Write-Host $reportPath
    if ($PrepareApplication) {
        & (Join-Path $PSScriptRoot 'Start-LocalBackend.ps1') -Verify
        $afterMetadata = Invoke-MySql -Query $sql -Password $password | ConvertFrom-Json
        $afterCounts = @()
        foreach ($tableName in @('users','classes','students')) {
            $value = Invoke-MySql -Query ('SELECT COUNT(*) FROM class_management.' + $tableName + ';') -Password $password
            $afterCounts += [pscustomobject]@{table=$tableName;count=[long]$value}
        }
        $historySql = "SELECT JSON_ARRAYAGG(JSON_OBJECT('version',version,'script',script,'checksum',checksum,'success',success)) FROM class_management.flyway_schema_history;"
        $afterHistory = @(Invoke-MySql -Query $historySql -Password $password | ConvertFrom-Json | ForEach-Object { $_ })
        $report.afterInitialization = [ordered]@{metadata=$afterMetadata;counts=$afterCounts;migrations=$afterHistory}
        [IO.File]::WriteAllText($reportPath,($report | ConvertTo-Json -Depth 15),(New-Object Text.UTF8Encoding($false)))
        Write-Host '迁移后表结构、约束、记录数量和 Flyway 历史已写入报告。' -ForegroundColor Green
    }
} catch {
    if ($report -and $report.connected) {
        $report.error=$_.Exception.Message
        $report.completed=$false
    } else {
        $report = [ordered]@{connected=$false;checkedAt=[DateTimeOffset]::Now.ToString('o');error=$_.Exception.Message}
    }
    [IO.File]::WriteAllText($reportPath,($report | ConvertTo-Json -Depth 15),(New-Object Text.UTF8Encoding($false)))
    Write-Host ('检查未完成：' + $_.Exception.Message) -ForegroundColor Red
} finally {
    if ($password) { $password.Dispose() }
}
Read-Host '按 Enter 关闭窗口' | Out-Null
