[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$PluginPackagePath,

    [Parameter(Mandatory)]
    [string]$PublisherPublicKeyDerPath,

    [Parameter(Mandatory)]
    [string]$GitHubPackageUrl,

    [string]$GiteePackageUrl,

    [Parameter(Mandatory)]
    [string]$DirectoryRootPrivateKeyPemPath,

    [string]$PreviousDirectoryJsonPath,

    [string]$PreviousDirectorySignaturePath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw 'This script requires PowerShell 7 or newer.'
}

$identifierPattern = '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$'
$tokenPattern = '^[A-Za-z0-9][A-Za-z0-9._+-]{0,79}$'
$abiPattern = '^[A-Za-z0-9][A-Za-z0-9._-]{0,39}$'
$entryClassPattern = '^([A-Za-z_$][A-Za-z0-9_$]*\.)+[A-Za-z_$][A-Za-z0-9_$]*$'
$assetNamePattern = '^[A-Za-z0-9][A-Za-z0-9._+-]{0,119}$'
$sha256Pattern = '^[0-9a-fA-F]{64}$'
$portablePathPattern = '^[A-Za-z0-9._/-]+$'
$maxArchiveBytes = 128MB
$maxManifestBytes = 64KB
$maxPackageSignatureBytes = 512
$maxPluginJarBytes = 64MB
$maxPluginDexBytes = 64MB
$maxDirectoryBytes = 512KB
$maxDirectorySignatureBytes = 1024
$maxDirectoryPackages = 128
$utf8Strict = [System.Text.UTF8Encoding]::new($false, $true)

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

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

function Save-Snapshot([string]$SourcePath, [string]$DestinationPath, [long]$MaximumBytes, [string]$Description) {
    $source = Get-RegularFile $SourcePath $Description $MaximumBytes
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
            if ($copied + $read -gt $MaximumBytes) { throw "$Description exceeds its size limit." }
            $output.Write($buffer, 0, $read)
            $copied += $read
        }
        $output.Flush($true)
        if ($copied -ne $beforeLength) { throw "$Description changed while its snapshot was being read." }
    } finally {
        if ($null -ne $output) { $output.Dispose() }
        $input.Dispose()
    }
    $after = Get-Item -LiteralPath $source.FullName
    if ($after.Length -ne $beforeLength -or $after.LastWriteTimeUtc -ne $beforeWriteTime) {
        throw "$Description changed while its snapshot was being read."
    }
    $snapshot = Get-Item -LiteralPath $DestinationPath
    return [pscustomobject]@{
        File = $snapshot
        Length = [long]$snapshot.Length
        Sha256 = (Get-FileHash -LiteralPath $snapshot.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

function Read-BoundedFile([string]$Path, [long]$MaximumBytes, [string]$Description) {
    $file = Get-RegularFile $Path $Description $MaximumBytes
    return ,([System.IO.File]::ReadAllBytes($file.FullName))
}

function Read-ZipEntryBytes([System.IO.Compression.ZipArchiveEntry]$Entry, [long]$MaximumBytes) {
    if ($Entry.Length -lt 0 -or $Entry.Length -gt $MaximumBytes) {
        throw 'A ZIP entry exceeds the expected size limit.'
    }
    $memory = [System.IO.MemoryStream]::new()
    $stream = $null
    try {
        $stream = $Entry.Open()
        $buffer = [byte[]]::new(32KB)
        while (($read = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            if ($memory.Length + $read -gt $MaximumBytes) { throw 'A ZIP entry exceeds its size limit.' }
            $memory.Write($buffer, 0, $read)
        }
        if ($memory.Length -ne $Entry.Length) { throw 'A ZIP entry length is inconsistent.' }
        return ,$memory.ToArray()
    } finally {
        if ($null -ne $stream) { $stream.Dispose() }
        $memory.Dispose()
    }
}

function Copy-ZipEntry([System.IO.Compression.ZipArchiveEntry]$Entry, [string]$Path, [long]$MaximumBytes) {
    if ($Entry.Length -lt 0 -or $Entry.Length -gt $MaximumBytes) { throw 'Plugin JAR exceeds its size limit.' }
    $input = $Entry.Open()
    $output = $null
    $hash = [System.Security.Cryptography.IncrementalHash]::CreateHash([System.Security.Cryptography.HashAlgorithmName]::SHA256)
    try {
        $output = [System.IO.File]::Open($Path, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
        $buffer = [byte[]]::new(64KB)
        $copied = [long]0
        while (($read = $input.Read($buffer, 0, $buffer.Length)) -gt 0) {
            if ($copied + $read -gt $MaximumBytes) { throw 'Plugin JAR exceeds its size limit.' }
            $output.Write($buffer, 0, $read)
            $hash.AppendData($buffer, 0, $read)
            $copied += $read
        }
        $output.Flush($true)
        if ($copied -ne $Entry.Length) { throw 'Plugin JAR length is inconsistent.' }
        return [pscustomobject]@{
            Length = $copied
            Sha256 = [Convert]::ToHexString($hash.GetHashAndReset()).ToLowerInvariant()
        }
    } finally {
        if ($null -ne $output) { $output.Dispose() }
        $input.Dispose()
        $hash.Dispose()
    }
}

function Assert-NoDuplicateJsonFields([System.Text.Json.JsonElement]$Element) {
    if ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
        $seen = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($property in $Element.EnumerateObject()) {
            if (-not $seen.Add($property.Name)) { throw 'JSON object repeats a field.' }
            Assert-NoDuplicateJsonFields $property.Value
        }
    } elseif ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
        foreach ($item in $Element.EnumerateArray()) { Assert-NoDuplicateJsonFields $item }
    }
}

function Read-StrictJson([byte[]]$Bytes, [long]$MaximumBytes, [string]$Description) {
    if ($Bytes.Length -lt 1 -or $Bytes.Length -gt $MaximumBytes -or
        ($Bytes.Length -ge 3 -and $Bytes[0] -eq 0xEF -and $Bytes[1] -eq 0xBB -and $Bytes[2] -eq 0xBF)
    ) {
        throw "$Description has an invalid size or UTF-8 BOM."
    }
    try {
        $text = $utf8Strict.GetString($Bytes)
        $options = [System.Text.Json.JsonDocumentOptions]::new()
        $options.AllowTrailingCommas = $false
        $options.CommentHandling = [System.Text.Json.JsonCommentHandling]::Disallow
        $options.MaxDepth = 32
        $document = [System.Text.Json.JsonDocument]::Parse($text, $options)
        Assert-NoDuplicateJsonFields $document.RootElement
        return [pscustomobject]@{ Text = $text; Document = $document }
    } catch {
        throw "$Description is not strict UTF-8 JSON or repeats a field."
    }
}

function Assert-Fields(
    [System.Text.Json.JsonElement]$Element,
    [string[]]$Required,
    [string[]]$Optional,
    [string]$Description
) {
    if ($Element.ValueKind -ne [System.Text.Json.JsonValueKind]::Object) { throw "$Description must be an object." }
    $fields = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($property in $Element.EnumerateObject()) {
        if ($property.Name -cnotin $Required -and $property.Name -cnotin $Optional) {
            throw "$Description contains an unsupported field."
        }
        [void]$fields.Add($property.Name)
    }
    foreach ($name in $Required) {
        if (-not $fields.Contains($name)) { throw "$Description is missing a required field." }
    }
    return ,$fields
}

function Get-JsonString([System.Text.Json.JsonElement]$Element, [string]$Name, [string]$Description) {
    $value = $Element.GetProperty($Name)
    if ($value.ValueKind -ne [System.Text.Json.JsonValueKind]::String) { throw "$Description field must be a string." }
    return $value.GetString()
}

function Get-JsonLong([System.Text.Json.JsonElement]$Element, [string]$Name, [string]$Description) {
    $value = $Element.GetProperty($Name)
    if ($value.ValueKind -ne [System.Text.Json.JsonValueKind]::Number) { throw "$Description field must be an integer." }
    try { return $value.GetInt64() } catch { throw "$Description field is outside the integer range." }
}

function Get-JsonStringArray([System.Text.Json.JsonElement]$Element, [string]$Name, [string]$Description) {
    $array = $Element.GetProperty($Name)
    if ($array.ValueKind -ne [System.Text.Json.JsonValueKind]::Array) { throw "$Description field must be an array." }
    $items = [System.Collections.Generic.List[string]]::new()
    $seen = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($item in $array.EnumerateArray()) {
        if ($item.ValueKind -ne [System.Text.Json.JsonValueKind]::String) { throw "$Description array must contain strings." }
        $text = $item.GetString()
        if (-not $seen.Add($text)) { throw "$Description array repeats an item." }
        $items.Add($text)
        if ($items.Count -gt 16) { throw "$Description array is too large." }
    }
    if ($items.Count -eq 0) { throw "$Description array must not be empty." }
    $sorted = [string[]]$items.ToArray()
    [Array]::Sort($sorted, [StringComparer]::Ordinal)
    for ($index = 0; $index -lt $sorted.Length; $index++) {
        if ($sorted[$index] -cne $items[$index]) { throw "$Description array must be ordinal-sorted." }
    }
    return $items.ToArray()
}

function Import-PublicRsa([byte[]]$Der, [string]$Description) {
    if ($Der.Length -lt 128 -or $Der.Length -gt 1024) { throw "$Description has an invalid size." }
    $rsa = [System.Security.Cryptography.RSA]::Create()
    try {
        $body = [Convert]::ToBase64String($Der, [Base64FormattingOptions]::InsertLineBreaks)
        $rsa.ImportFromPem("-----BEGIN PUBLIC KEY-----`n$body`n-----END PUBLIC KEY-----")
        if ($rsa.KeySize -lt 2048 -or $rsa.KeySize -gt 4096 -or
            [Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo()) -cne [Convert]::ToBase64String($Der)
        ) {
            throw "$Description is not a canonical RSA SubjectPublicKeyInfo DER key."
        }
        return $rsa
    } catch {
        $rsa.Dispose()
        throw "$Description is not a supported RSA public key."
    }
}

function Assert-DownloadUrl([string]$Value, [string]$Source) {
    $uri = $null
    if (-not [Uri]::TryCreate($Value, [UriKind]::Absolute, [ref]$uri) -or
        $uri.Scheme -cne 'https' -or $uri.Port -notin @(-1, 443) -or
        -not [string]::IsNullOrEmpty($uri.UserInfo) -or -not [string]::IsNullOrEmpty($uri.Fragment)
    ) {
        throw "$Source URL must be HTTPS without user information or a fragment."
    }
    $hostName = $uri.IdnHost.ToLowerInvariant()
    if ($Source -ceq 'GitHub') {
        $allowed = $hostName -ceq 'github.com' -or $hostName.EndsWith('.github.com', [StringComparison]::Ordinal) -or
            $hostName -ceq 'githubusercontent.com' -or $hostName.EndsWith('.githubusercontent.com', [StringComparison]::Ordinal)
    } else {
        $allowed = $hostName -ceq 'gitee.com' -or $hostName.EndsWith('.gitee.com', [StringComparison]::Ordinal)
    }
    if (-not $allowed) { throw "$Source URL host is not accepted by the App parser." }
}

function Get-IdentityKey([string]$PublisherId, [string]$PluginId, [string]$Version) {
    return "$PublisherId`0$PluginId`0$Version"
}

function Read-PreviousDirectory([byte[]]$JsonBytes, [byte[]]$SignatureBytes) {
    $documentInfo = Read-StrictJson $JsonBytes $maxDirectoryBytes 'Previous directory'
    try {
        $root = $documentInfo.Document.RootElement
        [void](Assert-Fields $root @('formatVersion', 'plugins') @() 'Previous directory')
        if ((Get-JsonLong $root 'formatVersion' 'Previous directory') -ne 1) { throw 'Previous directory version is unsupported.' }
        $plugins = $root.GetProperty('plugins')
        if ($plugins.ValueKind -ne [System.Text.Json.JsonValueKind]::Array -or
            $plugins.GetArrayLength() -lt 1 -or $plugins.GetArrayLength() -ge $maxDirectoryPackages
        ) {
            throw 'Previous directory does not leave room for the new release.'
        }
        foreach ($plugin in $plugins.EnumerateArray()) {
            [void](Assert-Fields $plugin @(
                    'publisherId', 'pluginId', 'version', 'hostApi', 'abis', 'publisherPublicKey', 'package'
                ) @() 'Previous directory plugin')
            $publisher = Get-JsonString $plugin 'publisherId' 'Previous directory plugin'
            $pluginId = Get-JsonString $plugin 'pluginId' 'Previous directory plugin'
            $version = Get-JsonString $plugin 'version' 'Previous directory plugin'
            $hostApi = Get-JsonString $plugin 'hostApi' 'Previous directory plugin'
            if ($publisher -cnotmatch $identifierPattern -or $pluginId -cnotmatch $identifierPattern -or
                $version -cnotmatch $tokenPattern -or $hostApi -cnotmatch $tokenPattern
            ) {
                throw 'Previous directory plugin identity is invalid.'
            }
            $oldAbis = @(Get-JsonStringArray $plugin 'abis' 'Previous directory ABI')
            if ($oldAbis | Where-Object { $_ -cnotmatch $abiPattern }) { throw 'Previous directory ABI is invalid.' }
            $encodedKey = Get-JsonString $plugin 'publisherPublicKey' 'Previous directory key'
            try { $decodedKey = [Convert]::FromBase64String($encodedKey) }
            catch { throw 'Previous directory publisher key is invalid.' }
            if ([Convert]::ToBase64String($decodedKey) -cne $encodedKey) { throw 'Previous directory publisher key is not canonical.' }
            $oldPublisherKey = Import-PublicRsa $decodedKey 'Previous directory publisher key'
            $oldPublisherKey.Dispose()

            $oldPackage = $plugin.GetProperty('package')
            $oldPackageFields = Assert-Fields $oldPackage @('size', 'sha256', 'githubUrl') @('giteeUrl') 'Previous directory package'
            $oldSize = Get-JsonLong $oldPackage 'size' 'Previous directory package'
            $oldDigest = Get-JsonString $oldPackage 'sha256' 'Previous directory package'
            $oldGitHub = Get-JsonString $oldPackage 'githubUrl' 'Previous directory package'
            if ($oldSize -lt 1 -or $oldSize -gt $maxArchiveBytes -or $oldDigest -cnotmatch $sha256Pattern) {
                throw 'Previous directory package size or digest is invalid.'
            }
            Assert-DownloadUrl $oldGitHub 'GitHub'
            if ($oldPackageFields.Contains('giteeUrl')) {
                $oldGiteeValue = $oldPackage.GetProperty('giteeUrl')
                if ($oldGiteeValue.ValueKind -eq [System.Text.Json.JsonValueKind]::String) {
                    Assert-DownloadUrl $oldGiteeValue.GetString() 'Gitee'
                } elseif ($oldGiteeValue.ValueKind -ne [System.Text.Json.JsonValueKind]::Null) {
                    throw 'Previous directory Gitee URL has an invalid type.'
                }
            }
        }
        $model = ConvertFrom-Json -InputObject $documentInfo.Text -AsHashtable -Depth 32
        $keys = [System.Collections.Generic.List[string]]::new()
        foreach ($record in $model.plugins) {
            $keys.Add((Get-IdentityKey $record.publisherId $record.pluginId $record.version))
        }
        $sorted = [string[]]$keys.ToArray()
        [Array]::Sort($sorted, [StringComparer]::Ordinal)
        for ($index = 0; $index -lt $sorted.Length; $index++) {
            if ($sorted[$index] -cne $keys[$index] -or
                ($index -gt 0 -and $sorted[$index] -ceq $sorted[$index - 1])
            ) {
                throw 'Previous directory releases are duplicated or not in parser order.'
            }
        }
        return $model.plugins
    } finally {
        $documentInfo.Document.Dispose()
    }
}

if (($PreviousDirectoryJsonPath -and -not $PreviousDirectorySignaturePath) -or
    ($PreviousDirectorySignaturePath -and -not $PreviousDirectoryJsonPath)
) {
    throw 'Previous directory JSON and signature must be supplied together.'
}

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sourcePackage = Get-RegularFile $PluginPackagePath 'Signed plugin package' $maxArchiveBytes
if ($sourcePackage.Extension -cne '.elymbot-plugin' -or $sourcePackage.Name -cnotmatch $assetNamePattern) {
    throw 'Plugin package must be a valid .elymbot-plugin asset name.'
}
$publicKeyBytes = Read-BoundedFile $PublisherPublicKeyDerPath 1024 'Publisher public key'
$publisherRsa = Import-PublicRsa $publicKeyBytes 'Publisher public key'
$rootKeyFile = Get-RegularFile $DirectoryRootPrivateKeyPemPath 'Directory root private key' 64KB

$workBase = Join-Path $repoRoot 'build/tmp/plugin-directory'
if (-not (Test-Path -LiteralPath $workBase)) { New-Item -ItemType Directory -Path $workBase -Force | Out-Null }
$workBaseItem = Get-Item -LiteralPath $workBase
if (-not $workBaseItem.PSIsContainer -or ($workBaseItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
    throw 'Temporary directory is not a normal build directory.'
}
$workRoot = Join-Path $workBase ([Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $workRoot | Out-Null
$snapshotPackagePath = Join-Path $workRoot $sourcePackage.Name
$innerJarPath = Join-Path $workRoot 'plugin.jar'
$stageDirectory = Join-Path $workRoot 'release'
$releaseAssetsDirectory = Join-Path $stageDirectory 'release-assets'
$reviewDirectory = Join-Path $stageDirectory 'review'
$rootRsa = [System.Security.Cryptography.RSA]::Create()
$outputDirectory = $null

try {
    $packageSnapshot = Save-Snapshot $sourcePackage.FullName $snapshotPackagePath $maxArchiveBytes 'Signed plugin package'
    try {
        $rootRsa.ImportFromPem([System.IO.File]::ReadAllText($rootKeyFile.FullName))
    } catch {
        throw 'The explicitly supplied directory root PEM is not a supported RSA key.'
    }
    if ($rootRsa.KeySize -lt 2048 -or $rootRsa.KeySize -gt 4096) {
        throw 'Directory root key must be an RSA key between 2048 and 4096 bits.'
    }
    $rootPublicDer = $rootRsa.ExportSubjectPublicKeyInfo()

    $packageArchive = [System.IO.Compression.ZipFile]::OpenRead($packageSnapshot.File.FullName)
    try {
        if ($packageArchive.Entries.Count -ne 3) { throw 'Plugin package must contain exactly three ZIP entries.' }
        $entries = [System.Collections.Generic.Dictionary[string, System.IO.Compression.ZipArchiveEntry]]::new([StringComparer]::Ordinal)
        foreach ($entry in $packageArchive.Entries) {
            if ($entry.FullName.EndsWith('/') -or $entries.ContainsKey($entry.FullName)) {
                throw 'Plugin package contains a directory entry or duplicate name.'
            }
            $entries.Add($entry.FullName, $entry)
        }
        foreach ($name in @('manifest.json', 'signature.bin', 'payload/plugin.jar')) {
            if (-not $entries.ContainsKey($name)) { throw 'Plugin package does not match the App ZIP contract.' }
        }
        $manifestBytes = Read-ZipEntryBytes $entries['manifest.json'] $maxManifestBytes
        $packageSignature = Read-ZipEntryBytes $entries['signature.bin'] $maxPackageSignatureBytes
        $pluginJarEntry = $entries['payload/plugin.jar']
        $pluginJarDigest = Copy-ZipEntry $pluginJarEntry $innerJarPath $maxPluginJarBytes
        if ($packageSignature.Length -ne [int][Math]::Ceiling($publisherRsa.KeySize / 8.0) -or
            -not $publisherRsa.VerifyData(
                $manifestBytes,
                $packageSignature,
                [System.Security.Cryptography.HashAlgorithmName]::SHA256,
                [System.Security.Cryptography.RSASignaturePadding]::Pkcs1
            )
        ) {
            throw 'Plugin manifest signature does not match the supplied publisher public key.'
        }
    } finally {
        $packageArchive.Dispose()
    }

    $manifestInfo = Read-StrictJson $manifestBytes $maxManifestBytes 'Plugin manifest'
    try {
        $manifestRoot = $manifestInfo.Document.RootElement
        [void](Assert-Fields $manifestRoot @(
                'formatVersion', 'publisherId', 'pluginId', 'version', 'hostApi', 'abis', 'entryClass', 'files'
            ) @() 'Plugin manifest')
        $manifest = ConvertFrom-Json -InputObject $manifestInfo.Text -AsHashtable -Depth 16
        if ($manifest.formatVersion -ne 1 -or
            $manifest.publisherId -cnotmatch $identifierPattern -or $manifest.pluginId -cnotmatch $identifierPattern -or
            $manifest.version -cnotmatch $tokenPattern -or $manifest.hostApi -cnotmatch $tokenPattern -or
            $manifest.entryClass -cnotmatch $entryClassPattern -or $manifest.entryClass.StartsWith('java.') -or
            $manifest.entryClass.StartsWith('android.') -or $manifest.entryClass.StartsWith('com.elymbot.core.')
        ) {
            throw 'Signed plugin manifest identity is invalid.'
        }
        $abis = @(Get-JsonStringArray $manifestRoot 'abis' 'Plugin manifest')
        if ($abis | Where-Object { $_ -cnotmatch $abiPattern }) { throw 'Signed plugin manifest has an invalid ABI.' }
        $files = $manifestRoot.GetProperty('files')
        if ($files.ValueKind -ne [System.Text.Json.JsonValueKind]::Array -or $files.GetArrayLength() -ne 1) {
            throw 'Signed plugin manifest must contain exactly one payload file.'
        }
        $fileManifest = $files[0]
        [void](Assert-Fields $fileManifest @('path', 'size', 'sha256') @() 'Plugin payload manifest')
        $payloadPath = Get-JsonString $fileManifest 'path' 'Plugin payload manifest'
        $payloadSize = Get-JsonLong $fileManifest 'size' 'Plugin payload manifest'
        $payloadSha256 = (Get-JsonString $fileManifest 'sha256' 'Plugin payload manifest').ToLowerInvariant()
        if ($payloadPath -cne 'plugin.jar' -or $payloadSize -ne $pluginJarDigest.Length -or
            $payloadSha256 -cnotmatch $sha256Pattern -or $payloadSha256 -cne $pluginJarDigest.Sha256
        ) {
            throw 'Plugin JAR size or SHA-256 does not match its signed manifest.'
        }
    } finally {
        $manifestInfo.Document.Dispose()
    }

    $innerJar = [System.IO.Compression.ZipFile]::OpenRead($innerJarPath)
    try {
        if ($innerJar.Entries.Count -lt 1 -or $innerJar.Entries.Count -gt 256) { throw 'Plugin JAR entry count is invalid.' }
        $innerNames = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        $classesDexFound = $false
        foreach ($entry in $innerJar.Entries) {
            if ($entry.FullName.EndsWith('/') -or -not $innerNames.Add($entry.FullName)) {
                throw 'Plugin JAR contains a directory or duplicate entry.'
            }
            $parts = $entry.FullName.Split('/')
            if ($entry.FullName.Length -gt 240 -or $entry.FullName -cnotmatch $portablePathPattern -or
                $entry.FullName.Contains('\') -or ($parts | Where-Object { $_ -in @('', '.', '..') }) -or
                $entry.Length -lt 0 -or $entry.Length -gt $maxPluginDexBytes
            ) {
                throw 'Plugin JAR contains an oversized or unsafe entry.'
            }
            if ($entry.FullName -ceq 'classes.dex' -and $entry.Length -gt 0) { $classesDexFound = $true }
        }
        if (-not $classesDexFound) { throw 'Plugin JAR does not contain classes.dex.' }
    } finally {
        $innerJar.Dispose()
    }

    Assert-DownloadUrl $GitHubPackageUrl 'GitHub'
    if ($GiteePackageUrl) { Assert-DownloadUrl $GiteePackageUrl 'Gitee' }

    $priorRecords = @()
    if ($PreviousDirectoryJsonPath) {
        $previousBytes = Read-BoundedFile $PreviousDirectoryJsonPath $maxDirectoryBytes 'Previous directory'
        $previousSignature = Read-BoundedFile $PreviousDirectorySignaturePath $maxDirectorySignatureBytes 'Previous signature'
        if ($previousSignature.Length -ne [int][Math]::Ceiling($rootRsa.KeySize / 8.0) -or
            -not $rootRsa.VerifyData(
                $previousBytes,
                $previousSignature,
                [System.Security.Cryptography.HashAlgorithmName]::SHA256,
                [System.Security.Cryptography.RSASignaturePadding]::Pkcs1
            )
        ) {
            throw 'Previous directory signature does not match the supplied root key.'
        }
        $priorRecords = @(Read-PreviousDirectory $previousBytes $previousSignature)
    }

    $records = [System.Collections.Generic.SortedDictionary[string, object]]::new([StringComparer]::Ordinal)
    foreach ($record in $priorRecords) {
        $key = Get-IdentityKey $record.publisherId $record.pluginId $record.version
        if ($records.ContainsKey($key)) { throw 'Previous directory has duplicate releases.' }
        $records.Add($key, $record)
    }
    $newRecord = [ordered]@{
        publisherId = $manifest.publisherId
        pluginId = $manifest.pluginId
        version = $manifest.version
        hostApi = $manifest.hostApi
        abis = [string[]]$manifest.abis
        publisherPublicKey = [Convert]::ToBase64String($publicKeyBytes)
        package = [ordered]@{
            size = [long]$packageSnapshot.Length
            sha256 = $packageSnapshot.Sha256
            githubUrl = $GitHubPackageUrl
            giteeUrl = if ($GiteePackageUrl) { $GiteePackageUrl } else { $null }
        }
    }
    $newKey = Get-IdentityKey $newRecord.publisherId $newRecord.pluginId $newRecord.version
    if ($records.ContainsKey($newKey)) { throw 'This plugin release identity already exists in the signed directory.' }
    $records.Add($newKey, $newRecord)
    if ($records.Count -gt $maxDirectoryPackages) { throw 'Signed directory exceeds the App parser release limit.' }

    $directoryObject = [ordered]@{ formatVersion = 1; plugins = @($records.Values) }
    $directoryJson = ConvertTo-Json -InputObject $directoryObject -Depth 16 -Compress
    $directoryBytes = $utf8Strict.GetBytes($directoryJson)
    if ($directoryBytes.Length -gt $maxDirectoryBytes) { throw 'Generated directory exceeds the App parser size limit.' }
    $directorySignature = $rootRsa.SignData(
        $directoryBytes,
        [System.Security.Cryptography.HashAlgorithmName]::SHA256,
        [System.Security.Cryptography.RSASignaturePadding]::Pkcs1
    )
    if (-not $rootRsa.VerifyData(
            $directoryBytes,
            $directorySignature,
            [System.Security.Cryptography.HashAlgorithmName]::SHA256,
            [System.Security.Cryptography.RSASignaturePadding]::Pkcs1
        )
    ) {
        throw 'Generated plugin directory signature did not verify.'
    }

    New-Item -ItemType Directory -Path $releaseAssetsDirectory -Force | Out-Null
    New-Item -ItemType Directory -Path $reviewDirectory | Out-Null
    [System.IO.File]::WriteAllBytes((Join-Path $releaseAssetsDirectory 'elymbot-plugin-directory.json'), $directoryBytes)
    [System.IO.File]::WriteAllBytes((Join-Path $releaseAssetsDirectory 'elymbot-plugin-directory.sig'), $directorySignature)
    [System.IO.File]::WriteAllBytes((Join-Path $reviewDirectory 'directory-root.der'), $rootPublicDer)
    [System.IO.File]::Move($packageSnapshot.File.FullName, (Join-Path $releaseAssetsDirectory $sourcePackage.Name))

    $outputRoot = Join-Path $repoRoot 'build/outputs/official-plugin-directories'
    if (-not (Test-Path -LiteralPath $outputRoot)) { New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null }
    $outputRootItem = Get-Item -LiteralPath $outputRoot
    if (-not $outputRootItem.PSIsContainer -or ($outputRootItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'Output root is not a normal build directory.'
    }
    $outputName = "$($manifest.publisherId)-$($manifest.pluginId)-$($manifest.version)"
    $outputDirectory = Join-Path $outputRoot $outputName
    if (Test-Path -LiteralPath $outputDirectory) { throw 'Output already exists; refusing to replace release artifacts.' }
    [System.IO.Directory]::Move($stageDirectory, $outputDirectory)

    [pscustomobject]@{
        ReleaseAssetsDirectory = Join-Path $outputDirectory 'release-assets'
        ReviewDirectory = Join-Path $outputDirectory 'review'
        DirectorySha256 = [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($directoryBytes)).ToLowerInvariant()
        RootPublicKeyDerSha256 = [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($rootPublicDer)).ToLowerInvariant()
        PluginPackageSha256 = $packageSnapshot.Sha256
        PackageSizeBytes = $packageSnapshot.Length
    }
} finally {
    $publisherRsa.Dispose()
    $rootRsa.Dispose()
    if ($workRoot -and (Test-Path -LiteralPath $workRoot)) {
        $allowedRoot = [System.IO.Path]::GetFullPath($workBase).TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
        $resolvedRoot = [System.IO.Path]::GetFullPath($workRoot)
        if (-not $resolvedRoot.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing to clean a temporary path outside the generated build directory.'
        }
        [System.IO.Directory]::Delete($resolvedRoot, $true)
    }
}
