[CmdletBinding()]
param([string]$MySqlBin=$(if($env:MYSQL_BIN){$env:MYSQL_BIN}else{Join-Path $env:ProgramFiles 'MySQL\MySQL Server 8.4\bin'}),
      [ValidateRange(1,65535)][int]$Port=3306,
      [ValidatePattern('^[A-Za-z0-9_]+$')][string]$User='class_management_demo_app')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($env:DEMO_DB_PASSWORD)) { throw '请在本地设置 DEMO_DB_PASSWORD；不要发到聊天或提交Git。' }
$client=Join-Path $MySqlBin 'mysql.exe'
function Invoke-DemoSql([string]$sql) {
    $info=New-Object Diagnostics.ProcessStartInfo
    $info.FileName=$client
    $info.Arguments="--no-defaults --protocol=tcp --host=127.0.0.1 --port=$Port --user=$User --default-character-set=utf8mb4 --batch --raw --skip-column-names class_management_demo"
    $info.UseShellExecute=$false; $info.CreateNoWindow=$true
    $info.RedirectStandardInput=$true; $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
    $info.StandardOutputEncoding=[Text.Encoding]::UTF8
    $info.EnvironmentVariables['MYSQL_PWD']=$env:DEMO_DB_PASSWORD
    $process=New-Object Diagnostics.Process; $process.StartInfo=$info
    [void]$process.Start()
    $errorTask=$process.StandardError.ReadToEndAsync()
    $bytes=[Text.Encoding]::UTF8.GetBytes($sql)
    $process.StandardInput.BaseStream.Write($bytes,0,$bytes.Length);$process.StandardInput.Close()
    $output=$process.StandardOutput.ReadToEnd();$process.WaitForExit()
    $diagnostic=$errorTask.GetAwaiter().GetResult()
    if($process.ExitCode -ne 0){throw '演示库SQL执行失败，事务未确认提交；请本地检查连接、表结构和约束。'}
    return $output.Trim()
}
# 数据库名称固定为专用演示库，执行前必须已有V1–V4表结构。
$tables='classes','students','exams','scores','assignments','assignment_records','attendance','praises','ai_operations'
$total=0
foreach($table in $tables){$total += [long](Invoke-DemoSql "SELECT COUNT(*) FROM $table;")}
if($total -gt 0){throw '演示库已有业务数据，本次未修改。重复执行会安全停止；需要重演时请新建空演示库或按备份恢复说明操作，禁止清空正式库。'}
$temp=Join-Path ([IO.Path]::GetTempPath()) ('class-demo-'+[Guid]::NewGuid().ToString('N')+'.sql')
try {
    & node (Join-Path $PSScriptRoot 'Generate-DemoSql.mjs') $temp
    if($LASTEXITCODE -ne 0){throw '演示SQL生成失败'}
    Invoke-DemoSql ([IO.File]::ReadAllText($temp,[Text.Encoding]::UTF8)) | Out-Null
    Write-Output '专用演示库初始化完成：3班、36名虚构学生；再次执行会停止，不覆盖现有数据。'
} finally {if(Test-Path -LiteralPath $temp){Remove-Item -LiteralPath $temp}}
