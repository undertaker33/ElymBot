[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('scheduled-tasks', 'qq')]
    [string]$PluginId,

    [Parameter(Mandatory)]
    [string]$PublisherId,

    [Parameter(Mandatory)]
    [string]$Version,

    [string]$HostApi = 'elymbot-plugin-api-v1',

    [Parameter(Mandatory)]
    [string[]]$Abis,

    [Parameter(Mandatory)]
    [string]$EntryClass,

    [Parameter(Mandatory)]
    [string]$SigningPrivateKeyPath,

    [switch]$Offline,

    [hashtable]$ResourceFiles = @{},

    [hashtable]$ResourceSha256 = @{}
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw 'This packaging script requires PowerShell 7 or newer.'
}

$identifierPattern = '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'
$tokenPattern = '^[A-Za-z0-9][A-Za-z0-9._+-]{0,79}$'
$abiPattern = '^[A-Za-z0-9][A-Za-z0-9._-]{0,39}$'
$entryClassPattern = '^([A-Za-z_$][A-Za-z0-9_$]*\.)+[A-Za-z_$][A-Za-z0-9_$]*$'
$resourcePattern = '^[A-Za-z0-9._/-]+$'
$sha256Pattern = '^[0-9a-fA-F]{64}$'
$maximumPayloadFileBytes = 64MB
$maximumPackageBytes = 128MB

if ($PublisherId -cnotmatch $identifierPattern -or
    $PluginId -cnotmatch $identifierPattern -or
    $Version -cnotmatch $tokenPattern -or
    $HostApi -cnotmatch $tokenPattern -or
    $EntryClass -cnotmatch $entryClassPattern
) {
    throw 'The package identity, version, host API, or entry class is invalid.'
}

if ($Abis.Count -lt 1 -or $Abis.Count -gt 16 -or ($Abis | Where-Object { $_ -cnotmatch $abiPattern })) {
    throw 'Provide between one and sixteen valid ABI identifiers.'
}
$sortedAbis = [string[]]$Abis
[Array]::Sort($sortedAbis, [StringComparer]::Ordinal)
for ($index = 1; $index -lt $sortedAbis.Length; $index++) {
    if ($sortedAbis[$index] -ceq $sortedAbis[$index - 1]) {
        throw 'The ABI list contains a duplicate.'
    }
}

if ($ResourceFiles.Count -ne $ResourceSha256.Count) {
    throw 'Every explicit plugin resource requires one expected SHA-256 value.'
}
foreach ($resourcePath in $ResourceFiles.Keys) {
    if (-not $ResourceSha256.ContainsKey($resourcePath)) {
        throw 'Every explicit plugin resource requires one expected SHA-256 value.'
    }
}
foreach ($resourcePath in $ResourceSha256.Keys) {
    if (-not $ResourceFiles.ContainsKey($resourcePath)) {
        throw 'An expected resource digest has no matching resource file.'
    }
}

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$wrapper = Join-Path $repoRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) {
    throw 'The Gradle wrapper was not found at the repository root.'
}

$privateKeyFile = Get-Item -LiteralPath $SigningPrivateKeyPath -ErrorAction Stop
if ($privateKeyFile.PSIsContainer -or
    ($privateKeyFile.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0 -or
    $privateKeyFile.Length -lt 1 -or $privateKeyFile.Length -gt 64KB
) {
    throw 'The explicitly supplied RSA private-key PEM file is not a regular, bounded file.'
}

$buildDirectory = Join-Path $repoRoot 'build'
$stageRoot = Join-Path $buildDirectory 'tmp/elymbot-plugin-package'
$outputRoot = Join-Path $buildDirectory 'outputs/elymbot-plugins'
foreach ($directory in @($stageRoot, $outputRoot)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $directoryInfo = Get-Item -LiteralPath $directory
    if (-not $directoryInfo.PSIsContainer -or
        ($directoryInfo.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0
    ) {
        throw 'The generated package directory is not a private, ordinary build directory.'
    }
}

$temporaryRoot = Join-Path $stageRoot ([Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
$stagedPluginJar = Join-Path $temporaryRoot 'plugin.jar'
$partialPackage = Join-Path $temporaryRoot 'package.part'
$rsa = $null

try {
    Push-Location $repoRoot
    try {
        $gradleArguments = @('packagePluginDex', "-PelymbotPluginId=$PluginId", '--console=plain')
        if ($Offline) { $gradleArguments += '--offline' }
        & $wrapper @gradleArguments
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle plugin DEX packaging failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }

    $basePluginJar = Join-Path $repoRoot "plugins/$PluginId/build/outputs/elymbot-plugin/plugin.jar"
    if (-not (Test-Path -LiteralPath $basePluginJar -PathType Leaf)) {
        throw 'Gradle did not produce the plugin DEX JAR.'
    }

    if ($ResourceFiles.Count -eq 0) {
        Copy-Item -LiteralPath $basePluginJar -Destination $stagedPluginJar
    } else {
        Add-Type -AssemblyName System.IO.Compression
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archiveInput = [System.IO.Compression.ZipFile]::OpenRead($basePluginJar)
        $outputStream = [System.IO.File]::Open(
            $stagedPluginJar,
            [System.IO.FileMode]::CreateNew,
            [System.IO.FileAccess]::ReadWrite,
            [System.IO.FileShare]::None
        )
        $archiveOutput = [System.IO.Compression.ZipArchive]::new(
            $outputStream,
            [System.IO.Compression.ZipArchiveMode]::Create,
            $false
        )
        try {
            $resourceNames = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
            foreach ($entry in $archiveInput.Entries) {
                if ($entry.FullName.EndsWith('/')) { continue }
                if (-not $resourceNames.Add($entry.FullName)) {
                    throw 'The plugin DEX JAR contains a duplicate entry.'
                }
                $entrySegments = @($entry.FullName.Split('/') | Where-Object { $_ -in @('', '.', '..') })
                if ($entry.FullName -cnotmatch $resourcePattern -or
                    $entry.FullName.StartsWith('/') -or
                    $entry.FullName.Contains('\') -or
                    $entrySegments.Count -gt 0
                ) {
                    throw 'The plugin DEX JAR contains an unsafe entry path.'
                }
                $compression = if ($entry.FullName -match '(?i)\.(xz|zip|gz|png|jpe?g|webp|jar|apk)$') {
                    [System.IO.Compression.CompressionLevel]::NoCompression
                } else {
                    [System.IO.Compression.CompressionLevel]::Optimal
                }
                $targetEntry = $archiveOutput.CreateEntry($entry.FullName, $compression)
                $targetEntry.LastWriteTime = [DateTimeOffset]::new(1980, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
                $inputEntry = $entry.Open()
                $targetStream = $targetEntry.Open()
                try {
                    $inputEntry.CopyTo($targetStream)
                } finally {
                    $targetStream.Dispose()
                    $inputEntry.Dispose()
                }
            }

            $resourcePaths = [string[]]$ResourceFiles.Keys
            [Array]::Sort($resourcePaths, [StringComparer]::Ordinal)
            foreach ($resourcePath in $resourcePaths) {
                $segments = $resourcePath.Split('/')
                if ($resourcePath -cnotmatch $resourcePattern -or
                    $resourcePath.StartsWith('/') -or
                    $resourcePath.Contains('\') -or
                    $segments.Count -gt 16 -or
                    $resourcePath.Length -gt 240 -or
                    (@($segments | Where-Object { $_ -in @('', '.', '..') }).Count -gt 0)
                ) {
                    throw 'An explicit plugin resource path is unsafe.'
                }
                if (-not $resourceNames.Add($resourcePath)) {
                    throw 'An explicit plugin resource duplicates an existing plugin resource.'
                }

                $sourcePath = [string]$ResourceFiles[$resourcePath]
                $sourceFile = Get-Item -LiteralPath $sourcePath -ErrorAction Stop
                if ($sourceFile.PSIsContainer -or
                    ($sourceFile.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0 -or
                    $sourceFile.Length -lt 1 -or $sourceFile.Length -gt $maximumPayloadFileBytes
                ) {
                    throw 'An explicit plugin resource must be a regular, bounded file.'
                }
                $expectedHash = ([string]$ResourceSha256[$resourcePath]).ToLowerInvariant()
                if ($expectedHash -cnotmatch $sha256Pattern) {
                    throw 'An explicit plugin resource has an invalid expected SHA-256 value.'
                }
                $actualHash = (Get-FileHash -LiteralPath $sourceFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
                if ($actualHash -cne $expectedHash) {
                    throw "An explicit plugin resource did not match its expected SHA-256: $resourcePath"
                }

                $compression = if ($sourceFile.Extension -match '(?i)^\.(xz|zip|gz|png|jpe?g|webp|jar|apk)$') {
                    [System.IO.Compression.CompressionLevel]::NoCompression
                } else {
                    [System.IO.Compression.CompressionLevel]::Optimal
                }
                $targetEntry = $archiveOutput.CreateEntry($resourcePath, $compression)
                $targetEntry.LastWriteTime = [DateTimeOffset]::new(1980, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
                $sourceStream = [System.IO.File]::OpenRead($sourceFile.FullName)
                $targetStream = $targetEntry.Open()
                try {
                    $sourceStream.CopyTo($targetStream)
                } finally {
                    $targetStream.Dispose()
                    $sourceStream.Dispose()
                }
            }
        } finally {
            $archiveOutput.Dispose()
            $archiveInput.Dispose()
        }
    }

    $payloadInfo = Get-Item -LiteralPath $stagedPluginJar
    if ($payloadInfo.Length -gt $maximumPayloadFileBytes) {
        throw 'The finished plugin.jar exceeds the App stager 64 MiB per-file limit.'
    }
    $payloadHash = (Get-FileHash -LiteralPath $stagedPluginJar -Algorithm SHA256).Hash.ToLowerInvariant()

    $manifest = [ordered]@{
        formatVersion = 1
        publisherId = $PublisherId
        pluginId = $PluginId
        version = $Version
        hostApi = $HostApi
        abis = $sortedAbis
        entryClass = $EntryClass
        files = @(
            [ordered]@{
                path = 'plugin.jar'
                size = [long]$payloadInfo.Length
                sha256 = $payloadHash
            }
        )
    }
    $manifestJson = ConvertTo-Json -InputObject $manifest -Depth 8 -Compress
    $manifestBytes = [System.Text.UTF8Encoding]::new($false).GetBytes($manifestJson)
    if ($manifestBytes.Length -gt 64KB) {
        throw 'The generated plugin manifest exceeds the App stager limit.'
    }

    $rsa = [System.Security.Cryptography.RSA]::Create()
    try {
        $privateKeyPem = [System.IO.File]::ReadAllText($privateKeyFile.FullName)
        $rsa.ImportFromPem($privateKeyPem)
    } catch {
        throw 'Could not import the explicitly supplied RSA private-key PEM.'
    }
    if ($rsa.KeySize -lt 2048 -or $rsa.KeySize -gt 4096) {
        throw 'The supplied RSA signing key must be between 2048 and 4096 bits.'
    }
    $signatureBytes = $rsa.SignData(
        $manifestBytes,
        [System.Security.Cryptography.HashAlgorithmName]::SHA256,
        [System.Security.Cryptography.RSASignaturePadding]::Pkcs1
    )
    if ($signatureBytes.Length -gt 512 -or -not $rsa.VerifyData(
        $manifestBytes,
        $signatureBytes,
        [System.Security.Cryptography.HashAlgorithmName]::SHA256,
        [System.Security.Cryptography.RSASignaturePadding]::Pkcs1
    )) {
        throw 'The generated plugin signature did not verify.'
    }

    $outputFileName = "$PluginId-$Version.elymbot-plugin"
    $outputPath = Join-Path $outputRoot $outputFileName
    $outputStream = [System.IO.File]::Open(
        $partialPackage,
        [System.IO.FileMode]::CreateNew,
        [System.IO.FileAccess]::ReadWrite,
        [System.IO.FileShare]::None
    )
    $packageArchive = [System.IO.Compression.ZipArchive]::new(
        $outputStream,
        [System.IO.Compression.ZipArchiveMode]::Create,
        $false
    )
    try {
        $epoch = [DateTimeOffset]::new(1980, 1, 1, 0, 0, 0, [TimeSpan]::Zero)

        $manifestEntry = $packageArchive.CreateEntry('manifest.json', [System.IO.Compression.CompressionLevel]::Optimal)
        $manifestEntry.LastWriteTime = $epoch
        $manifestStream = $manifestEntry.Open()
        try { $manifestStream.Write($manifestBytes, 0, $manifestBytes.Length) } finally { $manifestStream.Dispose() }

        $signatureEntry = $packageArchive.CreateEntry('signature.bin', [System.IO.Compression.CompressionLevel]::NoCompression)
        $signatureEntry.LastWriteTime = $epoch
        $signatureStream = $signatureEntry.Open()
        try { $signatureStream.Write($signatureBytes, 0, $signatureBytes.Length) } finally { $signatureStream.Dispose() }

        $payloadEntry = $packageArchive.CreateEntry('payload/plugin.jar', [System.IO.Compression.CompressionLevel]::NoCompression)
        $payloadEntry.LastWriteTime = $epoch
        $payloadStream = $payloadEntry.Open()
        $pluginStream = [System.IO.File]::OpenRead($stagedPluginJar)
        try { $pluginStream.CopyTo($payloadStream) } finally { $payloadStream.Dispose(); $pluginStream.Dispose() }
    } finally {
        $packageArchive.Dispose()
    }

    $finalSize = (Get-Item -LiteralPath $partialPackage).Length
    if ($finalSize -gt $maximumPackageBytes) {
        throw 'The signed package exceeds the App stager 128 MiB archive limit.'
    }
    $verifyArchive = [System.IO.Compression.ZipFile]::OpenRead($partialPackage)
    try {
        $actualNames = @($verifyArchive.Entries | ForEach-Object FullName)
        $expectedNames = @('manifest.json', 'signature.bin', 'payload/plugin.jar')
        if ($actualNames.Count -ne 3 -or
            @(Compare-Object $expectedNames $actualNames -CaseSensitive).Count -ne 0
        ) {
            throw 'The generated package does not match the App stager entry contract.'
        }
    } finally {
        $verifyArchive.Dispose()
    }

    [System.IO.File]::Move($partialPackage, $outputPath, $true)
    Write-Output $outputPath
} finally {
    if ($null -ne $rsa) { $rsa.Dispose() }
    $resolvedStageRoot = [System.IO.Path]::GetFullPath($stageRoot).TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    $resolvedTemporaryRoot = [System.IO.Path]::GetFullPath($temporaryRoot)
    if (-not $resolvedTemporaryRoot.StartsWith($resolvedStageRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to clean a staging path outside the generated package directory.'
    }
    foreach ($temporaryFile in @($stagedPluginJar, $partialPackage)) {
        if (Test-Path -LiteralPath $temporaryFile -PathType Leaf) {
            Remove-Item -LiteralPath $temporaryFile -Force
        }
    }
    if ((Get-ChildItem -LiteralPath $temporaryRoot -Force | Measure-Object).Count -eq 0) {
        [System.IO.Directory]::Delete($temporaryRoot)
    }
}
