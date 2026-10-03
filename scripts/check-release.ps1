# Checks a Flowsy APK before sharing it. Read-only: it never changes the APK or the project.
#   - permissions (only the expected AndroidX internal one is allowed)
#   - debuggable is off
#   - version name and code
#   - none of the words in scripts\forbidden-words.txt (a local file, one word per line, not in git)
#     appears anywhere inside the APK (entry names and contents)
#   - no Markdown (.md) file is packaged (repository documents stay out of the APK)
# Usage (from the project folder):
#   powershell -ExecutionPolicy Bypass -File scripts\check-release.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\check-release.ps1 -Apk path\to\app.apk
param(
    [string]$Apk = "app\build\outputs\apk\release\app-release.apk",
    [string]$WordList = (Join-Path $PSScriptRoot "forbidden-words.txt")
)

$ErrorActionPreference = "Stop"
$failed = $false

if (-not (Test-Path $Apk)) { Write-Host "APK not found: $Apk" -ForegroundColor Red; exit 2 }
$Apk = (Resolve-Path $Apk).Path

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$apkanalyzer = Join-Path $sdk "cmdline-tools\latest\bin\apkanalyzer.bat"
$buildTools = Get-ChildItem (Join-Path $sdk "build-tools") -Directory | Sort-Object { [version]$_.Name } | Select-Object -Last 1
$aapt2 = Join-Path $buildTools.FullName "aapt2.exe"
$useApkanalyzer = Test-Path $apkanalyzer

Write-Host "APK: $Apk"
Write-Host ("Tool: " + $(if ($useApkanalyzer) { "apkanalyzer" } else { "aapt2 ($($buildTools.Name)) - apkanalyzer not installed" }))
Write-Host ""

# Version and debuggable
if ($useApkanalyzer) {
    $versionName = (& $apkanalyzer manifest version-name $Apk).Trim()
    $versionCode = (& $apkanalyzer manifest version-code $Apk).Trim()
    $debuggable = (& $apkanalyzer manifest debuggable $Apk).Trim()
    $permissions = & $apkanalyzer manifest permissions $Apk
} else {
    $badging = & $aapt2 dump badging $Apk
    $package = ($badging | Select-String "^package:").Line
    $versionName = [regex]::Match($package, "versionName='([^']*)'").Groups[1].Value
    $versionCode = [regex]::Match($package, "versionCode='([^']*)'").Groups[1].Value
    $debuggable = if ($badging | Select-String "application-debuggable") { "true" } else { "false" }
    $permissions = & $aapt2 dump permissions $Apk | Select-String "uses-permission" |
        ForEach-Object { [regex]::Match($_.Line, "name='([^']*)'").Groups[1].Value }
}
Write-Host "Version: $versionName (versionCode $versionCode)"
Write-Host "Debuggable: $debuggable"
if ($debuggable -ne "false") { Write-Host "  FAIL: the APK is debuggable" -ForegroundColor Red; $failed = $true }

# Permissions
# AndroidX adds one private, signature-protected permission named after the app id (any Flowsy build).
$allowed = "^com.elyacoub9.flowsy(.[a-z]+)?.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION$"
Write-Host ""
Write-Host "Permissions:"
if (-not $permissions) { Write-Host "  (none)" }
foreach ($p in $permissions) {
    if ($p -match $allowed) { Write-Host "  $p  (expected: AndroidX internal, signature-protected)" }
    else { Write-Host "  $p  UNEXPECTED" -ForegroundColor Red; $failed = $true }
}

# Search inside the APK. Entries are read straight from the zip: R8 gives resources short names that
# differ only by case (a.xml / A.xml), which cannot be extracted side by side on Windows (Expand-Archive fails).
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($Apk)
try {
    $entries = @($archive.Entries | Where-Object { -not $_.FullName.EndsWith("/") })
    if (-not (Test-Path $WordList)) { Write-Host "Word list not found: $WordList" -ForegroundColor Red; exit 2 }
    $patterns = @(Get-Content $WordList | ForEach-Object { $_.Trim().ToLowerInvariant() } | Where-Object { $_ -and -not $_.StartsWith("#") })
    Write-Host ""
    Write-Host ("Search in {0} files for the {1} words in {2}" -f $entries.Count, $patterns.Count, (Split-Path $WordList -Leaf))
    $hits = 0
    foreach ($entry in $entries) {
        $name = $entry.FullName
        foreach ($pattern in $patterns) {
            if ($name.ToLowerInvariant().Contains($pattern)) { Write-Host "  HIT (file name): $name" -ForegroundColor Red; $hits++ }
        }
        $stream = $entry.Open()
        $buffer = New-Object IO.MemoryStream
        $stream.CopyTo($buffer)
        $stream.Dispose()
        $bytes = $buffer.ToArray()
        # Text may be stored as UTF-8 (dex, XML) or UTF-16 (resources): search both.
        $utf8 = [Text.Encoding]::UTF8.GetString($bytes).ToLowerInvariant()
        $utf16 = [Text.Encoding]::Unicode.GetString($bytes).ToLowerInvariant()
        foreach ($pattern in $patterns) {
            if ($utf8.Contains($pattern) -or $utf16.Contains($pattern)) { Write-Host "  HIT: '$pattern' in $name" -ForegroundColor Red; $hits++ }
        }
    }
    foreach ($md in ($entries | Where-Object { $_.Name -like "*.md" })) { Write-Host "  FAIL: Markdown file packaged: $($md.FullName)" -ForegroundColor Red; $hits++ }
    if ($hits -eq 0) { Write-Host "  No match. No Markdown file is packaged." } else { $failed = $true }
} finally {
    $archive.Dispose()
}

Write-Host ""
if ($failed) { Write-Host "RESULT: FAIL" -ForegroundColor Red; exit 1 }
Write-Host "RESULT: OK" -ForegroundColor Green
exit 0
