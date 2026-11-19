[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$name = 'portable-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
$stage = Join-Path $root "output\$name"
$bundle = Join-Path $stage 'ClassManagement'
$build = Join-Path $stage 'build'
$javaBin = Split-Path (Get-Command java.exe -CommandType Application | Select-Object -First 1 -ExpandProperty Source)
New-Item -ItemType Directory -Path $bundle -Force | Out-Null
Push-Location (Join-Path $root frontend)
try {
    # 新克隆的仓库没有 node_modules；先装依赖，否则 vite 找不到会构建失败。
    if (-not (Test-Path -LiteralPath 'node_modules')) {
        Write-Host '首次构建：安装前端依赖（npm ci）……'
        & npm.cmd ci
        if ($LASTEXITCODE) { throw '前端依赖安装失败，请检查网络与 npm 镜像配置' }
    }
    & npm.cmd run build
    if ($LASTEXITCODE) { throw '前端构建失败' }
} finally { Pop-Location }
Push-Location (Join-Path $root backend)
try {
    # 优先用仓库自带的 Maven Wrapper，未安装 Maven 的机器也能构建
    $mvnCmd = if (Test-Path -LiteralPath (Join-Path $root 'backend/mvnw.cmd')) { '.\mvnw.cmd' } else { 'mvn.cmd' }
    & $mvnCmd '-Pisolated-build,portable' "-Dverification.build.directory=../output/$name/build" '-DskipTests' package
    if ($LASTEXITCODE) { throw '后端构建失败' }
} finally { Pop-Location }
Push-Location $bundle
try {
    & (Join-Path $javaBin 'jlink.exe') --add-modules 'java.se,jdk.crypto.ec,jdk.unsupported,jdk.zipfs' --strip-debug --no-header-files --no-man-pages --compress=2 --output runtime
    if ($LASTEXITCODE) { throw 'Java 运行环境打包失败' }
} finally { Pop-Location }
$builtJar = @(Get-ChildItem -LiteralPath $build -Filter 'class-management-*.jar' -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike '*.original' } | Sort-Object LastWriteTime -Descending)[0].FullName
if (-not $builtJar) { throw '未找到构建产物：' + $build }
Copy-Item -LiteralPath $builtJar -Destination (Join-Path $bundle 'app.jar')
Copy-Item -LiteralPath (Join-Path $root 'frontend/dist') -Destination (Join-Path $bundle 'web') -Recurse
foreach ($file in @('portable/Start.cmd','portable/Start.ps1','portable/使用说明.txt','Read-ImageText.ps1')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination $bundle
}
$zip = Join-Path $stage 'ClassManagement-Windows-x64.zip'
Compress-Archive -LiteralPath $bundle -DestinationPath $zip -CompressionLevel Fastest
Write-Output $zip
