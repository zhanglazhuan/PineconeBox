# ============================================================================
# inject.ps1 — Build + Inject PineCone Launcher into LineageOS ROM
#
# Usage (PowerShell):
#   .\scripts\inject.ps1                 # Build + inject with defaults
#   .\scripts\inject.ps1 -BuildOnly      # Just build, skip inject
#   .\scripts\inject.ps1 -InjectOnly     # Just inject (APK must already exist)
#   .\scripts\inject.ps1 -Rom "path\to\rom.zip" -Apk "path\to\app.apk"
# ============================================================================

param(
    [switch]$BuildOnly,
    [switch]$InjectOnly,
    [string]$Rom,
    [string]$Apk,
    [string]$Output
)

$ErrorActionPreference = "Stop"

# ---- Paths ----
$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir
$LauncherDir = Join-Path $ProjectDir "launcher"
$BaseOSDir   = Join-Path $ProjectDir "baseOS"

$DefaultRom  = Join-Path $BaseOSDir "lineage-23.2-20260520-UNOFFICIAL-KonstaKANG-rpi5-atv.zip"
$DefaultApk  = Join-Path $LauncherDir "app\build\outputs\apk\debug\app-debug.apk"
$InjectSh    = Join-Path $ScriptDir "inject_apk.sh"

# ---- Resolve ROM ----
if (-not $Rom) {
    if (Test-Path $DefaultRom) {
        $Rom = $DefaultRom
    } else {
        $found = Get-ChildItem -Path $BaseOSDir -Filter "*-atv.zip" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { $Rom = $found.FullName }
    }
}
if (-not $Rom -or -not (Test-Path $Rom)) {
    Write-Host "ERROR: ROM not found. Place ROM in baseOS\ or use -Rom <path>" -ForegroundColor Red
    exit 1
}

# ---- Resolve APK ----
if (-not $Apk) { $Apk = $DefaultApk }

# ---- Resolve Output ----
if (-not $Output) {
    $romFile = [System.IO.Path]::GetFileNameWithoutExtension($Rom)
    $romDir  = Split-Path -Parent $Rom
    $Output  = Join-Path $romDir "$romFile-pinecone.zip"
}

# ---- Convert Windows path to WSL /mnt/ path ----
function To-WslPath($winPath) {
    # Resolve if the path exists (source files), otherwise just normalize the string (output files)
    if (Test-Path $winPath) {
        $full = (Resolve-Path $winPath).Path
    } else {
        # New file that doesn't exist yet — resolve parent + append filename
        $parent = Split-Path -Parent $winPath
        $child  = Split-Path -Leaf $winPath
        if ($parent -and (Test-Path $parent)) {
            $full = Join-Path (Resolve-Path $parent).Path $child
        } else {
            $full = $winPath
        }
    }
    $drive = $full[0].ToString().ToLower()
    $rest  = ($full.Substring(2) -replace '\\', '/')
    return "/mnt/$drive$rest"
}

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " PineCone OS — Build & Inject" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  ROM   : $Rom"
Write-Host "  APK   : $Apk"
Write-Host "  Output: $Output"
Write-Host "=================================================="

# ---- Step 1: Build APK ----
if (-not $InjectOnly) {
    Write-Host "`n[1/2] Building APK ..." -ForegroundColor Green

    # Auto-detect JAVA_HOME
    if (-not $env:JAVA_HOME) {
        $candidates = @(
            "C:\Program Files\Android\Android Studio\jbr",
            "C:\Program Files\Java\jdk-17",
            "C:\Program Files\Java\jdk-11"
        )
        foreach ($c in $candidates) {
            if (Test-Path $c) {
                $env:JAVA_HOME = $c
                Write-Host "  JAVA_HOME = $c"
                break
            }
        }
    }

    Push-Location $LauncherDir
    try {
        $result = & .\gradlew.bat assembleDebug 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Host "ERROR: Build failed!" -ForegroundColor Red
            Write-Host ($result -join "`n")
            exit 1
        }
        Write-Host "  Build OK" -ForegroundColor Green
    } finally {
        Pop-Location
    }
} else {
    Write-Host "`n[1/2] Skipped (--InjectOnly)" -ForegroundColor Yellow
}

# ---- Check APK exists ----
if (-not (Test-Path $Apk)) {
    Write-Host "ERROR: APK not found: $Apk" -ForegroundColor Red
    Write-Host "  Build first: .\scripts\inject.ps1 -BuildOnly" -ForegroundColor Yellow
    exit 1
}

# ---- Step 2: Inject via WSL ----
if ($BuildOnly) {
    Write-Host "`n[2/2] Skipped (--BuildOnly). APK is at:" -ForegroundColor Yellow
    Write-Host "  $Apk"
    exit 0
}

Write-Host "`n[2/2] Injecting APK into ROM via WSL ..." -ForegroundColor Green
Write-Host "  (sudo will ask for your WSL password below)" -ForegroundColor Yellow

$wslRom    = To-WslPath $Rom
$wslApk    = To-WslPath $Apk
$wslOut    = To-WslPath $Output
$wslScript = To-WslPath $InjectSh

Write-Host "`n  Command: sudo bash $wslScript $wslRom $wslApk $wslOut`n"

# Run WSL with console passthrough — sudo password prompt works!
wsl -d Ubuntu -- sudo bash $wslScript $wslRom $wslApk $wslOut

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n==================================================" -ForegroundColor Green
    Write-Host " SUCCESS!" -ForegroundColor Green
    Write-Host " Modified ROM: $Output" -ForegroundColor Green
    Write-Host "==================================================" -ForegroundColor Green
} else {
    Write-Host "`nFAILED with exit code $LASTEXITCODE" -ForegroundColor Red
    exit $LASTEXITCODE
}
