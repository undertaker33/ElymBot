[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$AppApkPath,

    [Parameter(Mandatory)]
    [string]$ApkAssetName
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw 'This script requires PowerShell 7 or newer.'
}

$maxApkBytes = 256MB
$assetNamePattern = '^[A-Za-z0-9][A-Za-z0-9._+-]{0,119}$'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

function Get-RegularFile([string]$Path, [string]$Description, [long]$MaximumBytes) {
    $file = Get-Item -LiteralPath $Path -ErrorAction Stop
    if ($file.PSIsContainer -or
        ($file.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0 -or
        $file.Length -lt 1 -or $file.Length -gt $MaximumBytes
    ) {
        throw "$Description must be a regular, non-empty file within its size limit."
    }
    return $file
}

function Save-Snapshot([string]$SourcePath, [string]$DestinationPath) {
    $source = Get-RegularFile $SourcePath 'Candidate App APK' $maxApkBytes
    $beforeLength = $source.Length
    $beforeWriteTime = $source.LastWriteTimeUtc
    $input = [System.IO.File]::Open(
        $source.FullName,
        [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read,
        [System.IO.FileShare]::Read
    )
    $output = $null
    try {
        $output = [System.IO.File]::Open(
            $DestinationPath,
            [System.IO.FileMode]::CreateNew,
            [System.IO.FileAccess]::Write,
            [System.IO.FileShare]::None
        )
        $buffer = [byte[]]::new(64KB)
        $copied = [long]0
        while (($read = $input.Read($buffer, 0, $buffer.Length)) -gt 0) {
            if ($copied + $read -gt $maxApkBytes) { throw 'Candidate App APK exceeds the updater size limit.' }
            $output.Write($buffer, 0, $read)
            $copied += $read
        }
        $output.Flush($true)
        if ($copied -ne $beforeLength) { throw 'Candidate App APK changed during snapshot.' }
    } finally {
        if ($null -ne $output) { $output.Dispose() }
        $input.Dispose()
    }
    $after = Get-Item -LiteralPath $source.FullName
    if ($after.Length -ne $beforeLength -or $after.LastWriteTimeUtc -ne $beforeWriteTime) {
        throw 'Candidate App APK changed during snapshot.'
    }
    return Get-Item -LiteralPath $DestinationPath
}

if ($ApkAssetName -cnotmatch $assetNamePattern -or -not $ApkAssetName.EndsWith('.apk', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'APK asset name does not match the App parser pattern.'
}
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sourceApk = Get-RegularFile $AppApkPath 'Candidate App APK' $maxApkBytes

$sdkRoot = if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
    $env:ANDROID_HOME
} elseif (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
    $env:ANDROID_SDK_ROOT
} elseif (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    Join-Path $env:LOCALAPPDATA 'Android/Sdk'
} else {
    throw 'Set ANDROID_HOME or ANDROID_SDK_ROOT to use the App sidecar builder.'
}
$versionCatalog = Get-Content -LiteralPath (Join-Path $repoRoot 'gradle/libs.versions.toml') -Raw
$toolsMatch = [Regex]::Match($versionCatalog, '(?m)^android-buildTools\s*=\s*"([0-9]+(?:\.[0-9]+){1,2})"\s*$')
if (-not $toolsMatch.Success) { throw 'Pinned Android Build Tools version could not be read.' }
$toolsDirectory = Join-Path $sdkRoot "build-tools/$($toolsMatch.Groups[1].Value)"
$aapt = Join-Path $toolsDirectory 'aapt.exe'
$apksigner = Join-Path $toolsDirectory 'apksigner.bat'
if (-not (Test-Path -LiteralPath $aapt -PathType Leaf) -or
    -not (Test-Path -LiteralPath $apksigner -PathType Leaf)
) {
    throw 'Pinned SDK aapt or apksigner was not found.'
}

$workBase = Join-Path $repoRoot 'build/tmp/app-release-sidecar'
if (-not (Test-Path -LiteralPath $workBase)) { New-Item -ItemType Directory -Path $workBase -Force | Out-Null }
$workBaseItem = Get-Item -LiteralPath $workBase
if (-not $workBaseItem.PSIsContainer -or ($workBaseItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
    throw 'Temporary directory is not a normal build directory.'
}
$workRoot = Join-Path $workBase ([Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $workRoot | Out-Null
$snapshotApkPath = Join-Path $workRoot $ApkAssetName
$stageDirectory = Join-Path $workRoot 'release'
$assetsDirectory = Join-Path $stageDirectory 'release-assets'

try {
    $snapshotApk = Save-Snapshot $sourceApk.FullName $snapshotApkPath
    $badging = & $aapt 'dump' 'badging' $snapshotApk.FullName 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Android aapt could not read the candidate APK.' }
    $packageLine = @($badging | Where-Object { $_ -match '^package:' }) | Select-Object -First 1
    $packageMatch = [Regex]::Match(
        [string]$packageLine,
        "^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']*)'"
    )
    if (-not $packageMatch.Success -or $packageMatch.Groups[1].Value -cne 'com.elymbot.android') {
        throw 'Candidate APK package metadata does not match the App updater identity.'
    }
    $versionCode = [long]0
    if (-not [long]::TryParse($packageMatch.Groups[2].Value, [ref]$versionCode) -or $versionCode -le 0) {
        throw 'Candidate APK versionCode is invalid.'
    }
    $versionName = $packageMatch.Groups[3].Value
    if ([string]::IsNullOrWhiteSpace($versionName) -or $versionName.Length -gt 80 -or
        ($versionName.ToCharArray() | Where-Object { [char]::IsControl($_) })
    ) {
        throw 'Candidate APK versionName is invalid.'
    }

    $updaterSourcePath = Join-Path $repoRoot 'app/src/main/kotlin/com/elymbot/android/runtime/pluginpackage/OfficialAppUpdateService.kt'
    $updaterSource = Get-Content -LiteralPath $updaterSourcePath -Raw
    $signerMatch = [Regex]::Match($updaterSource, 'OFFICIAL_SIGNER_SHA256\s*=\s*"([0-9a-fA-F]{64})"')
    if (-not $signerMatch.Success) { throw 'App updater trusted signer digest could not be read.' }
    $expectedSigner = $signerMatch.Groups[1].Value.ToLowerInvariant()

    $signerOutput = & $apksigner 'verify' '--print-certs' $snapshotApk.FullName 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Android apksigner rejected the candidate APK.' }
    $signerDigests = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($line in $signerOutput) {
        if ($line -match '(?:Signer #\d+|V\d+ Signer(?: #\d+)?):? certificate SHA-256 digest:\s*([0-9a-fA-F]{64})') {
            [void]$signerDigests.Add($Matches[1].ToLowerInvariant())
        }
    }
    if ($signerDigests.Count -ne 1 -or -not $signerDigests.Contains($expectedSigner)) {
        throw 'Candidate APK signer does not match the App updater trusted signer.'
    }

    $apkSha256 = (Get-FileHash -LiteralPath $snapshotApk.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    $metadata = [ordered]@{
        formatVersion = 1
        packageName = 'com.elymbot.android'
        versionCode = $versionCode
        versionName = $versionName
        apkAssetName = $ApkAssetName
        apkSha256 = $apkSha256
    }
    $metadataBytes = $utf8NoBom.GetBytes((ConvertTo-Json -InputObject $metadata -Depth 4 -Compress))
    if ($metadataBytes.Length -gt 64KB) { throw 'Generated App sidecar exceeds the App parser size limit.' }

    New-Item -ItemType Directory -Path $assetsDirectory -Force | Out-Null
    [System.IO.File]::WriteAllBytes((Join-Path $assetsDirectory 'elymbot-release.json'), $metadataBytes)
    [System.IO.File]::Move($snapshotApk.FullName, (Join-Path $assetsDirectory $ApkAssetName))

    $outputRoot = Join-Path $repoRoot 'build/outputs/official-app-releases'
    if (-not (Test-Path -LiteralPath $outputRoot)) { New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null }
    $outputRootItem = Get-Item -LiteralPath $outputRoot
    if (-not $outputRootItem.PSIsContainer -or ($outputRootItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'Output root is not a normal build directory.'
    }
    $outputName = "version-$versionCode-$($apkSha256.Substring(0, 12))"
    $outputDirectory = Join-Path $outputRoot $outputName
    if (Test-Path -LiteralPath $outputDirectory) { throw 'App release output already exists; refusing to replace it.' }
    [System.IO.Directory]::Move($stageDirectory, $outputDirectory)

    [pscustomobject]@{
        ReleaseAssetsDirectory = Join-Path $outputDirectory 'release-assets'
        ApkSha256 = $apkSha256
        SidecarSha256 = [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($metadataBytes)).ToLowerInvariant()
        PackageName = 'com.elymbot.android'
        VersionCode = $versionCode
        VersionName = $versionName
    }
} finally {
    if (Test-Path -LiteralPath $workRoot) {
        $allowedRoot = [System.IO.Path]::GetFullPath($workBase).TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
        $resolvedRoot = [System.IO.Path]::GetFullPath($workRoot)
        if (-not $resolvedRoot.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing to clean a temporary path outside the generated build directory.'
        }
        [System.IO.Directory]::Delete($resolvedRoot, $true)
    }
}
