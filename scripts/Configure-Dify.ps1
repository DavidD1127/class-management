[CmdletBinding()]
param([string]$ApiUrl='http://127.0.0.1:8088/v1')
$ErrorActionPreference='Stop'
$uri=[Uri]$ApiUrl
if($uri.Scheme -notin @('http','https') -or ($uri.Scheme -eq 'http' -and -not $uri.IsLoopback)){throw '仅本机可使用HTTP，远程Dify必须使用HTTPS'}
$root=Split-Path -Parent $PSScriptRoot
$secretDir=Join-Path $root '.secrets'
New-Item -ItemType Directory -Path $secretDir -Force | Out-Null
Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Security\Microsoft.PowerShell.Security.psd1')
Write-Host '在 Dify 中发布班级助手工作流，然后在本机此窗口输入该应用的 API Key。不要发送到聊天。'
$secure=Read-Host 'Dify 应用 API Key（隐藏输入）' -AsSecureString
try{
 if($secure.Length -eq 0){throw 'API Key 不能为空'}
 [IO.File]::WriteAllText((Join-Path $secretDir 'dify-key.dpapi'),(ConvertFrom-SecureString $secure),[Text.Encoding]::ASCII)
 [IO.File]::WriteAllText((Join-Path $secretDir 'dify-url.txt'),$ApiUrl.TrimEnd('/'),[Text.Encoding]::ASCII)
 Write-Output 'Dify 应用密钥已用当前 Windows 账号加密保存。下次启动智能助手时生效。'
}finally{$secure.Dispose()}
