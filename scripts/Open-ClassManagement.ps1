
[CmdletBinding()]
param([switch]$WithDify)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$logs=Join-Path $root '.test-data'
$log=Join-Path $logs 'system-launcher.log'
$url='http://127.0.0.1:15174/'
$health='http://127.0.0.1:8188/console/api/ping'
$mutex=New-Object Threading.Mutex($false,'Local\ClassManagementDesktopLauncher')
$locked=$false
function Port-Ready([int]$port){
 $client=New-Object Net.Sockets.TcpClient
 try{return $client.ConnectAsync('127.0.0.1',$port).Wait(500)}catch{return $false}finally{$client.Dispose()}
}
function Web-Ready([string]$address){
 try{return (Invoke-WebRequest -Uri $address -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200}catch{return $false}
}
try{
 $locked=$mutex.WaitOne(0)
 if(-not $locked){return}
 New-Item -ItemType Directory -Path $logs -Force | Out-Null
 Set-Content -LiteralPath $log -Value ('启动：'+(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')) -Encoding UTF8
 # 桌面启动不依赖开发终端的PATH；只补 JAVA_HOME 与显式指定的 node 目录，其余交给系统 PATH。
 if($env:JAVA_HOME){$env:PATH=(Join-Path $env:JAVA_HOME 'bin')+';'+$env:PATH}
 if($env:NODE_EXE){$env:PATH=(Split-Path -Parent $env:NODE_EXE)+';'+$env:PATH}
 $nodeExe=if($env:NODE_EXE){$env:NODE_EXE}else{'node.exe'}
 $env:API_PROXY_TARGET='http://127.0.0.1:8092'
 if(-not(Port-Ready 8092)){& (Join-Path $PSScriptRoot 'Start-AgentLocal.ps1') | Add-Content -LiteralPath $log}
 if(-not(Port-Ready 15174)){
  $vite=Join-Path $root 'frontend\node_modules\vite\bin\vite.js'
  Start-Process -FilePath $nodeExe -ArgumentList ('"'+$vite+'" --host 127.0.0.1 --port 15174 --strictPort') -WorkingDirectory (Join-Path $root 'frontend') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'agent-frontend.log') -RedirectStandardError (Join-Path $logs 'agent-frontend-error.log') | Out-Null
 }
 for($attempt=0;$attempt -lt 30;$attempt++){
  if(Web-Ready $url){break}
  Start-Sleep -Seconds 1
 }
 if(-not(Web-Ready $url)){throw '网页尚未就绪，请查看agent-frontend-error.log。'}
 if(-not(Port-Ready 8092)){throw '后端尚未就绪，请查看agent-backend.log。'}
 Add-Content -LiteralPath $log -Value ('网页就绪：'+(Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
 Start-Process $url

 # AI 默认走 DeepSeek 直连，不需要 Docker；只有显式 -WithDify 才拉起 Dify 容器。
 if($WithDify){ try{
  if(-not(Web-Ready $health)){
   if(-not(Port-Ready 8188)){& (Join-Path $PSScriptRoot 'Start-DockerLocal.ps1') | Add-Content -LiteralPath $log}
   for($attempt=0;$attempt -lt 60;$attempt++){
    if(Test-Path '\\.\pipe\dockerDesktopLinuxEngine'){break}
    Start-Sleep -Seconds 2
   }
   if(-not(Test-Path '\\.\pipe\dockerDesktopLinuxEngine')){throw 'Docker引擎尚未就绪，请查看Docker启动日志后重试。'}
   $docker=Join-Path $env:USERPROFILE 'AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe'
   $runtime=Join-Path $env:USERPROFILE 'ClassManagementAgent\dify\dify-1.17.1\docker'
   $compose=Start-Process -FilePath $docker -ArgumentList 'compose -p class-agent -f docker-compose.yaml -f class-agent.local.yaml up -d --no-recreate --pull never' -WorkingDirectory $runtime -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logs 'launcher-dify.log') -RedirectStandardError (Join-Path $logs 'launcher-dify-error.log')
   if(-not $compose.WaitForExit(120000)){throw 'AI服务启动超时，请查看launcher-dify-error.log。'}
   if($compose.ExitCode -ne 0){throw 'AI服务启动失败，请查看launcher-dify-error.log。'}
  }
  for($attempt=0;$attempt -lt 120;$attempt++){
   if(Web-Ready $health){break}
   Start-Sleep -Seconds 1
  }
  if(-not(Web-Ready $health)){throw 'AI服务尚未就绪。'}
  Add-Content -LiteralPath $log -Value ('AI就绪：'+(Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
 }catch{
  Add-Content -LiteralPath $log -Value ('AI服务启动异常：'+$_.Exception.Message)
 }
 }else{
  Add-Content -LiteralPath $log -Value 'AI：DeepSeek 直连，本次未启动 Docker/Dify（需要时用 -WithDify）。'
 }
}catch{
 Add-Content -LiteralPath $log -Value $_.Exception.Message
 $shell=New-Object -ComObject WScript.Shell
 $shell.Popup(('班级管理系统暂未启动完成：'+$_.Exception.Message+"`n日志："+$log),0,'班级管理系统',16) | Out-Null
}finally{
 if($locked){$mutex.ReleaseMutex()}
 $mutex.Dispose()
}
