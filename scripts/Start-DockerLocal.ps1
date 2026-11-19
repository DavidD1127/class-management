[CmdletBinding()]
param([switch]$OpenDesktop)
$ErrorActionPreference='Stop'
$appRoot=Join-Path $env:USERPROFILE 'AppData\Local\Programs\DockerDesktop'
$app=Join-Path $appRoot 'Docker Desktop.exe'
if(-not(Test-Path -LiteralPath $app)){throw '未找到已安装的Docker Desktop'}
# 原AppData目录的EFS属性会使Docker原子重命名失败，运行数据单独放置。
$runtime=Join-Path $env:USERPROFILE 'ClassManagementAgent\DockerRuntime'
$roaming=Join-Path $runtime 'Roaming'
$local=Join-Path $runtime 'Local'
foreach($path in @($runtime,$roaming,$local,(Join-Path $roaming 'Docker'))){
 New-Item -ItemType Directory -Path $path -Force | Out-Null
 if((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::Encrypted){throw 'Docker独立运行目录仍有加密属性，未修改系统加密设置'}
}
$metadata=Join-Path $roaming 'Docker\install-settings.json'
$original=Join-Path $env:USERPROFILE 'AppData\Roaming\Docker\install-settings.json'
if(-not(Test-Path -LiteralPath $metadata) -and (Test-Path -LiteralPath $original)){
 [IO.File]::WriteAllBytes($metadata,[IO.File]::ReadAllBytes($original))
}
$running=@(Get-Process -Name 'com.docker.backend' -ErrorAction SilentlyContinue | Where-Object {$_.Path -and $_.Path.StartsWith($appRoot+'\',[StringComparison]::OrdinalIgnoreCase)})
if($running.Count -and -not $OpenDesktop){Write-Output 'Docker进程已在运行，部署前会单独检查引擎状态。';return}
$previousRoaming=$env:APPDATA;$previousLocal=$env:LOCALAPPDATA
try{
 $env:APPDATA=$roaming;$env:LOCALAPPDATA=$local
 if($OpenDesktop){Start-Process -FilePath $app -WindowStyle Hidden | Out-Null}
 else{
  Start-Process -FilePath (Join-Path $appRoot 'resources\com.docker.backend.exe') -ArgumentList '-accept-license -engine WSL2 -with-frontend=false' -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime 'backend.log') -RedirectStandardError (Join-Path $runtime 'backend-error.log') | Out-Null
 }
}finally{$env:APPDATA=$previousRoaming;$env:LOCALAPPDATA=$previousLocal}
Write-Output 'Docker启动请求已发送；进程使用独立运行目录，未改变Windows全局环境变量。'
