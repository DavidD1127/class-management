[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$runtime=Join-Path $env:USERPROFILE 'ClassManagementAgent\dify'
$candidates=@((Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'),(Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe'))
$docker=$candidates | Where-Object {Test-Path -LiteralPath $_} | Select-Object -First 1
if(-not $docker){throw 'Docker Desktop 尚未安装。请先完成 WSL2/Virtual Machine Platform 安装与系统重启。'}
& (Join-Path $PSScriptRoot 'Start-DockerLocal.ps1')
$versionPath=Join-Path $root '_downloads/dify-version.txt'
if(-not(Test-Path -LiteralPath $versionPath)){throw '缺少固定版本信息，请先从官方发布页下载稳定版 Dify。'}
$version=([IO.File]::ReadAllText($versionPath)).Trim()
if($version -notmatch '^v?\d+\.\d+\.\d+(?:[-.][A-Za-z0-9.]+)?$'){throw 'Dify 版本信息无效'}
$archive=Join-Path $root ('_downloads/dify-'+$version+'.tar.gz')
if(-not(Test-Path -LiteralPath $archive)){throw 'Dify 官方源码归档尚未下载完成'}
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$source=Join-Path $runtime ('dify-'+$version.TrimStart('v'))
$composeDirectory=Join-Path $source 'docker'
if(-not(Test-Path -LiteralPath (Join-Path $composeDirectory 'docker-compose.yaml'))){
 & tar.exe -xf $archive -C $runtime ('dify-'+$version.TrimStart('v')+'/docker')
 if($LASTEXITCODE -ne 0){throw 'Dify 官方源码解压失败'}
}
$compose=Join-Path $composeDirectory 'docker-compose.yaml'
if(-not(Test-Path -LiteralPath $compose)){throw '没有找到官方 Docker Compose 配置'}
$envPath=Join-Path $composeDirectory '.env'
if(-not(Test-Path -LiteralPath $envPath)){
 $content=[IO.File]::ReadAllText((Join-Path $composeDirectory '.env.example'))
 $values=@{EXPOSE_NGINX_PORT='127.0.0.1:8088';EXPOSE_NGINX_SSL_PORT='127.0.0.1:8448';CONSOLE_WEB_URL='http://127.0.0.1:8088';CONSOLE_API_URL='http://127.0.0.1:8088';SERVICE_API_URL='http://127.0.0.1:8088';APP_WEB_URL='http://127.0.0.1:8088';APP_API_URL='http://127.0.0.1:8088'}
 $random=[Security.Cryptography.RandomNumberGenerator]::Create()
 try{foreach($name in @('SECRET_KEY','DB_PASSWORD','REDIS_PASSWORD','SANDBOX_API_KEY','PLUGIN_DAEMON_KEY','PLUGIN_DIFY_INNER_API_KEY')){$bytes=New-Object byte[] 32;$random.GetBytes($bytes);$values[$name]=([BitConverter]::ToString($bytes)).Replace('-','');[Array]::Clear($bytes,0,$bytes.Length)}}finally{$random.Dispose()}
 $values['CODE_EXECUTION_API_KEY']=$values['SANDBOX_API_KEY']
 $values['CELERY_BROKER_URL']='redis://:'+$values['REDIS_PASSWORD']+'@redis:6379/1'
 foreach($name in $values.Keys){$pattern='(?m)^'+[regex]::Escape($name)+'=.*$';if($content -match $pattern){$content=[regex]::Replace($content,$pattern,($name+'='+$values[$name]))}}
 [IO.File]::WriteAllText($envPath,$content,(New-Object Text.UTF8Encoding($false)))
 $values.Clear();$content=$null
}
$overridePath=Join-Path $composeDirectory 'class-agent.local.yaml'
if(-not(Test-Path -LiteralPath $overridePath)){
 [IO.File]::WriteAllText($overridePath,"services:`n  plugin_daemon:`n    ports: !reset []`n",(New-Object Text.UTF8Encoding($false)))
}
$composeArguments=@('compose','-p','class-agent','-f',$compose,'-f',$overridePath)
Push-Location $composeDirectory
try{
 & $docker @composeArguments config --quiet
 if($LASTEXITCODE -ne 0){throw 'Dify Compose 配置检查失败，尚未启动'}
 Write-Output 'Dify 本机配置检查通过。'
 try{& $docker info --format '{{.ServerVersion}}' 2>$null | Out-Null}catch{throw 'Docker 引擎未就绪，请先启动 Docker Desktop；系统刚启用 WSL2 时需先重启电脑。'}
 if($LASTEXITCODE -ne 0){throw 'Docker 引擎未就绪，请先启动 Docker Desktop；系统刚启用 WSL2 时需先重启电脑。'}
 & $docker @composeArguments up -d
 if($LASTEXITCODE -ne 0){throw 'Dify 容器启动失败，请检查 Docker 网络及镜像下载状态'}
}finally{Pop-Location}
Write-Output 'Dify 已发出启动请求。管理入口：http://127.0.0.1:8088'
Write-Output ('数据目录：'+$runtime)
Write-Output '请确认管理界面可访问，再导入 agent/class-agent.workflow.yml 并配置模型。现有班级 MySQL 不受此部署脚本影响。'
