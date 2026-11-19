[CmdletBinding()]
param([ValidateSet('dify','deepseek')][string]$Engine='deepseek')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$logs=Join-Path $root '.test-data'
# 新克隆的仓库没有该目录；日志重定向与私有配置都在这里，先建出来。
if(-not(Test-Path -LiteralPath $logs)){New-Item -ItemType Directory -Path $logs -Force | Out-Null}
$buildDir=Join-Path $root 'backend/target-phase7-agent'
# 按通配匹配产物，避免把版本号写死在脚本里（改版本不用同步改这里）。
$jar=@(Get-ChildItem -LiteralPath $buildDir -Filter 'class-management-*.jar' -File -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending)[0].FullName
$secrets=Join-Path $root '.secrets'
if(-not $jar -or -not(Test-Path -LiteralPath $jar)){throw ('未找到后端产物，请先构建：'+$buildDir)}
# 本机环境可配置项：优先读同名环境变量，未设置时回退到通用默认值。
# 这样仓库本身不含任何本机路径，换机器只需配置环境变量。
$mysqlBin=if($env:MYSQL_BIN){$env:MYSQL_BIN}else{Join-Path $env:ProgramFiles 'MySQL\MySQL Server 8.4\bin'}
$dataDir=if($env:DEMO_DB_DATADIR){$env:DEMO_DB_DATADIR}else{Join-Path $root '.data/mysql'}
$adminUser=if($env:BOOTSTRAP_ADMIN_USERNAME){$env:BOOTSTRAP_ADMIN_USERNAME}else{'12345'}
Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Security\Microsoft.PowerShell.Security.psd1')
function Read-LocalSecret([string]$path){
 $secure=ConvertTo-SecureString ([IO.File]::ReadAllText($path));$pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
 try{return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer).Trim()}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer);$secure.Dispose()}
}
function Port-Ready([int]$port){$client=New-Object Net.Sockets.TcpClient;try{return $client.ConnectAsync('127.0.0.1',$port).Wait(1000)}catch{return $false}finally{$client.Dispose()}}
if($Engine -eq 'dify' -and -not(Test-Path -LiteralPath (Join-Path $secrets 'dify-key.dpapi'))){throw 'Dify应用尚未配置，请先完成Docker/WSL部署、发布工作流并运行Configure-Dify.ps1。临时验证原有模型可显式使用 -Engine deepseek。'}
if(Port-Ready 8092){throw '8092端口已有服务，请先核对后端版本；此脚本不会终止已有进程'}
$demoConfig=Join-Path $logs 'open-system-backend.ps1'
$config=@{}
if(Test-Path -LiteralPath $demoConfig){
 # 本机私有的演示库配置（已被 gitignore）：用语法树解析取值，不执行该文件。
 $ast=[Management.Automation.Language.Parser]::ParseFile($demoConfig,[ref]$null,[ref]$null)
 foreach($assignment in $ast.FindAll({param($n)$n -is [Management.Automation.Language.AssignmentStatementAst]},$true)){
  foreach($name in @('DB_URL','DB_USERNAME','DB_PASSWORD')){
   if($assignment.Left.Extent.Text -eq ('$env:'+$name)){
    $literal=$assignment.Right.Find({param($n)$n -is [Management.Automation.Language.StringConstantExpressionAst]},$true)
    if($null -eq $literal){throw '本地演示数据库配置格式不支持，请在本机检查'}
    $config[$name]=$literal.Value
   }
  }
 }
 if($config['DB_URL'] -notmatch '^jdbc:mysql://127\.0\.0\.1:3310/class_management_demo\?'){throw '演示库地址与预期不符，不会自动连接其他数据库'}
}else{
 # 新克隆的仓库没有该私有文件：改为从环境变量读取，方便他人直接上手。
 if(-not $env:DB_URL){throw '缺少数据库配置：请先 docker compose up -d 并设置 DB_URL/DB_USERNAME/DB_PASSWORD（可参考 .env.example），或按 docs/QUICKSTART.md 走便携版'}
 $config['DB_URL']=$env:DB_URL
 $config['DB_USERNAME']=$env:DB_USERNAME
 $config['DB_PASSWORD']=$env:DB_PASSWORD
 # 只校验库名，不限定主机端口，但绝不允许连到别的库上写演示数据。
 if($config['DB_URL'] -notmatch '/class_management_demo(\?|$)'){throw '数据库连接串必须指向 class_management_demo 库，不会自动连接其他数据库'}
}
if(-not(Port-Ready 3310)){
 # 数据目录不能放在 OneDrive 或 %TEMP%：OneDrive 会同步正在写入的表空间，%TEMP% 可能被清理工具递归删空。
 # 默认放在项目内 .data/mysql（已 gitignore）；可用环境变量 DEMO_DB_DATADIR 指向任意位置。
 $data=$dataDir
 if(-not(Test-Path -LiteralPath (Join-Path $data 'class_management_demo'))){throw ('演示数据目录不存在：'+$data+'。请先运行 scripts\Initialize-Demo.ps1，或用环境变量 DEMO_DB_DATADIR 指向已有数据目录')}
 # mysqld 从命令行取 --datadir，Windows 会把非 ASCII 字符转坏，路径必须纯英文。
 if($data -notmatch '^[\x20-\x7E]+$'){throw ('数据库路径含非 ASCII 字符，MySQL 命令行无法稳定处理：'+$data+'。请用环境变量 DEMO_DB_DATADIR 指定一个纯英文路径')}
 $mysql=Join-Path $mysqlBin 'mysqld.exe'
 Start-Process -FilePath $mysql -ArgumentList ('--no-defaults --datadir="'+$data+'" --log-error="'+(Join-Path $data 'agent-server.err')+'" --port=3310 --bind-address=127.0.0.1 --mysqlx=OFF --console') -WorkingDirectory $data -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'agent-mysql.log') -RedirectStandardError (Join-Path $logs 'agent-mysql-error.log') | Out-Null
 for($attempt=0;$attempt -lt 30;$attempt++){if(Port-Ready 3310){break};Start-Sleep -Milliseconds 500}
 if(-not(Port-Ready 3310)){throw '演示数据库尚未就绪，请查看agent-mysql-error.log'}
}
# 新克隆的仓库没有 .secrets 目录，先建出来再写密钥文件。
if(-not(Test-Path -LiteralPath $secrets)){New-Item -ItemType Directory -Path $secrets -Force | Out-Null}
$jwtPath=Join-Path $secrets 'agent-jwt.dpapi'
if(-not(Test-Path -LiteralPath $jwtPath)){
 $bytes=New-Object byte[] 48;$random=[Security.Cryptography.RandomNumberGenerator]::Create()
 try{$random.GetBytes($bytes);$secure=ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force;try{[IO.File]::WriteAllText($jwtPath,(ConvertFrom-SecureString $secure),[Text.Encoding]::ASCII)}finally{$secure.Dispose()}}finally{$random.Dispose();[Array]::Clear($bytes,0,$bytes.Length)}
}
$config['JWT_SECRET']=Read-LocalSecret $jwtPath
$config['AI_ENGINE']=$Engine;$config['AI_OCR_SCRIPT']=Join-Path $PSScriptRoot 'Read-ImageText.ps1'
$config['BOOTSTRAP_ADMIN_USERNAME']=$adminUser;$config['BOOTSTRAP_ADMIN_PASSWORD']=if($env:BOOTSTRAP_ADMIN_PASSWORD){$env:BOOTSTRAP_ADMIN_PASSWORD}else{''};$config['SERVER_PORT']='8092'
if($Engine -eq 'dify'){$config['DIFY_API_KEY']=Read-LocalSecret (Join-Path $secrets 'dify-key.dpapi');$config['DIFY_API_URL']=([IO.File]::ReadAllText((Join-Path $secrets 'dify-url.txt'))).Trim()}
else{
 # 没了密钥文件不该阻断启动：AI 助手会提示未配置，其余功能照常。
 $deepseekFile=Join-Path $secrets 'deepseek-key.dpapi'
 if(Test-Path -LiteralPath $deepseekFile){$config['DEEPSEEK_API_KEY']=Read-LocalSecret $deepseekFile}
 elseif($env:DEEPSEEK_API_KEY){$config['DEEPSEEK_API_KEY']=$env:DEEPSEEK_API_KEY}
 else{$config['DEEPSEEK_API_KEY']='';Write-Host '[提示] 未找到 DeepSeek 密钥，AI 助手将提示未配置，其余功能正常。可运行 scripts\Configure-DeepSeek.ps1 配置。'}
}
$previous=@{}
try{
 foreach($name in $config.Keys){$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process');[Environment]::SetEnvironmentVariable($name,$config[$name],'Process')}
 # 从 JAR 所在目录启动，避免 Windows Java 启动器误读命令行中的中文路径。
 $backend=Start-Process -FilePath 'java.exe' -ArgumentList ('-Dfile.encoding=UTF-8 -jar '+(Split-Path -Leaf $jar)) -WorkingDirectory (Split-Path -Parent $jar) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logs 'agent-backend.log') -RedirectStandardError (Join-Path $logs 'agent-backend-error.log')
}finally{foreach($name in $previous.Keys){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')};$config.Clear()}
for($attempt=0;$attempt -lt 120;$attempt++){if($backend.HasExited){throw '后端启动失败，请检查agent-backend.log'};if(Port-Ready 8092){break};Start-Sleep -Milliseconds 500}
if(-not(Port-Ready 8092)){throw '后端尚未就绪，请查看启动日志'}
if(-not(Port-Ready 15174)){
 $vite=Join-Path $root 'frontend/node_modules/vite/bin/vite.js'
 Start-Process -FilePath 'node.exe' -ArgumentList ('"'+$vite+'" --host 127.0.0.1 --port 15174 --strictPort') -WorkingDirectory (Join-Path $root 'frontend') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'agent-frontend.log') -RedirectStandardError (Join-Path $logs 'agent-frontend-error.log') | Out-Null
}
Write-Output ('后端已启动，当前引擎：'+$Engine+'。网页：http://127.0.0.1:15174/。登录账号：'+$adminUser+'。')
