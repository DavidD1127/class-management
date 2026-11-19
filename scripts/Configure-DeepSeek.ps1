[CmdletBinding()]
param([switch]$ValidateOnly)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Security\Microsoft.PowerShell.Security.psd1') -ErrorAction Stop
$projectRoot = Split-Path -Parent $PSScriptRoot
$secretDir = Join-Path $projectRoot '.secrets'
$keyPath = Join-Path $secretDir 'deepseek-key.dpapi'
$reportPath = Join-Path $secretDir 'deepseek-verification.json'

# Windows DPAPI binds this encrypted file to the current Windows account.
if ($ValidateOnly) {
    $tokens = $null; $parseErrors = $null
    [void][Management.Automation.Language.Parser]::ParseFile($PSCommandPath,[ref]$tokens,[ref]$parseErrors)
    if ($parseErrors.Count) { throw 'PowerShell syntax validation failed.' }
    Write-Output 'Syntax OK; no credentials read and no network request sent.'
    return
}

New-Item -ItemType Directory -Path $secretDir -Force | Out-Null
$report = [ordered]@{status='waiting_for_input';verifiedAt=$null;model='deepseek-flash';modelsVerified=$false;chatVerified=$false;savedEncrypted=$false;httpStatus=$null;message='Waiting for local hidden input.'}
function Save-Report {
    [IO.File]::WriteAllText($reportPath,($report | ConvertTo-Json),(New-Object Text.UTF8Encoding($false)))
}
Save-Report
Write-Host 'DeepSeek official API setup' -ForegroundColor Cyan
Write-Host 'Paste your API Key here, then press Enter. Input is hidden.'
Write-Host 'The key is sent only to https://api.deepseek.com for verification.'
Write-Host 'This runs one tiny chat request. It does not send student data.'
Write-Host 'Success saves a Windows-encrypted key in the project .secrets directory.'
Write-Host 'No backend process will be restarted.'
$secure = $null; $ptr = [IntPtr]::Zero; $plain = $null; $client = $null
$request = $null; $response = $null; $phase = 'input'
try {
    $secure = Read-Host 'DeepSeek API Key (hidden)' -AsSecureString
    if ($secure.Length -eq 0) { throw 'empty' }
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr).Trim()
    if ([string]::IsNullOrWhiteSpace($plain) -or $plain -match '[\r\n]') { throw 'invalid' }
    $encrypted = ConvertFrom-SecureString -SecureString $secure
    Add-Type -AssemblyName System.Net.Http
    $handler = New-Object Net.Http.HttpClientHandler
    $handler.AllowAutoRedirect = $false
    $client = New-Object Net.Http.HttpClient($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(45)
    $client.DefaultRequestHeaders.Authorization = New-Object Net.Http.Headers.AuthenticationHeaderValue('Bearer',$plain)
    $plain = $null
    $report.status='verifying';$report.message='Checking official API.';Save-Report
    $phase='models'
    Write-Host 'Checking authentication and model availability...'
    $response = $client.GetAsync('https://api.deepseek.com/models').GetAwaiter().GetResult()
    $report.httpStatus=[int]$response.StatusCode
    if (-not $response.IsSuccessStatusCode) { throw 'http' }
    $catalog = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
    if (-not (@($catalog.data.id) -contains $report.model)) { throw 'model' }
    $report.modelsVerified=$true
    $response.Dispose();$response=$null
    $phase='chat'
    Write-Host 'Checking a minimal chat response...'
    $body = @{model=$report.model;messages=@(@{role='user';content='Reply with OK only.'});max_tokens=32;thinking=@{type='disabled'}} | ConvertTo-Json -Depth 8 -Compress
    $request = New-Object Net.Http.HttpRequestMessage([Net.Http.HttpMethod]::Post,'https://api.deepseek.com/chat/completions')
    $request.Content = New-Object Net.Http.StringContent($body,[Text.Encoding]::UTF8,'application/json')
    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    $report.httpStatus=[int]$response.StatusCode
    if (-not $response.IsSuccessStatusCode) { throw 'http' }
    $chat = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
    if ([string]::IsNullOrWhiteSpace($chat.choices[0].message.content)) { throw 'response' }
    $report.chatVerified=$true
    $phase='save'
    [IO.File]::WriteAllText($keyPath,$encrypted,[Text.Encoding]::ASCII)
    $report.savedEncrypted=$true;$report.status='success';$report.verifiedAt=[DateTimeOffset]::Now.ToString('o');$report.message='Official API authentication and chat verified. Encrypted local key saved.'
    Save-Report
    Write-Host 'SUCCESS: Official API authentication and chat verified; encrypted key saved.' -ForegroundColor Green
    Write-Host 'The existing backend startup helper will load it on its next start.'
} catch {
    $report.errorType=$_.Exception.GetType().FullName
    $report.errorId=$_.FullyQualifiedErrorId
    $report.errorCode=$_.Exception.HResult
    $report.errorLine=$_.InvocationInfo.ScriptLineNumber
    $report.status='failed';$report.verifiedAt=[DateTimeOffset]::Now.ToString('o')
    $report.message = if ($phase -eq 'input') { 'No valid key entered; nothing saved.' }
        elseif ($phase -eq 'save') { 'API verified but encrypted local save failed.' }
        elseif ($report.httpStatus -in @(401,403)) { 'Authentication failed. Check the official API key.' }
        elseif ($report.httpStatus -eq 402) { 'API balance is insufficient.' }
        elseif ($report.httpStatus -eq 429) { 'API rate limit reached. Retry later.' }
        elseif ($phase -eq 'models' -and $report.httpStatus -eq 200) { 'Project model deepseek-flash is unavailable for this account.' }
        else { 'Connection or response check failed. No key saved; retry later.' }
    Save-Report
    Write-Host $report.message -ForegroundColor Yellow
} finally {
    if ($response) { $response.Dispose() }
    if ($request) { $request.Dispose() }
    if ($client) { $client.DefaultRequestHeaders.Authorization=$null;$client.Dispose() }
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    if ($secure) { $secure.Dispose() }
    $plain=$null;$encrypted=$null;$chat=$null;$catalog=$null
}
Write-Host 'You may close this setup window.'
[void](Read-Host 'Press Enter to close')
