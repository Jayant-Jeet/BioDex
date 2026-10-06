$ErrorActionPreference = "Stop"

$sdkRoot = $env:ANDROID_HOME
if (-not $sdkRoot) {
    $sdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
}
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot

if (-not $env:JAVA_HOME) {
    $studioJbr = Join-Path $env:LOCALAPPDATA "Programs\Android Studio\jbr"
    if (Test-Path $studioJbr) {
        $env:JAVA_HOME = $studioJbr
        $env:PATH = "$studioJbr\bin;$env:PATH"
    }
}

$gradleWrapper = Join-Path $PSScriptRoot "..\gradlew.bat"
$adb = Join-Path $sdkRoot "platform-tools\adb.exe"
$emulator = Join-Path $sdkRoot "emulator\emulator.exe"

if (-not (Test-Path $gradleWrapper)) {
    throw "Gradle wrapper not found: $gradleWrapper"
}
if (-not (Test-Path $adb)) {
    throw "Android SDK platform-tools not found at $adb"
}

function Get-ReadyDeviceSerial {
    $devices = & $adb devices
    foreach ($line in $devices) {
        if ($line -match '^(\S+)\s+device$') {
            return $Matches[1]
        }
    }
    return $null
}

$serial = Get-ReadyDeviceSerial
if (-not $serial) {
    if (-not (Test-Path $emulator)) {
        throw "No Android device is connected and the Android emulator is not installed."
    }
    $availableAvds = @(& $emulator -list-avds | Where-Object { $_.Trim() })
    if ($availableAvds.Count -eq 0) {
        throw "No Android device is connected and no emulator AVD exists. Create an AVD in Android Studio first."
    }

    $avd = $availableAvds[0].Trim()
    Write-Output "No ready Android device found; starting emulator '$avd'."
    $emulatorProcess = Start-Process -FilePath $emulator `
        -ArgumentList @("-avd", $avd, "-no-snapshot-load", "-gpu", "swiftshader_indirect") `
        -PassThru
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        $emulatorProcess.Refresh()
        if ($emulatorProcess.HasExited) {
            throw "Android emulator exited during startup with code $($emulatorProcess.ExitCode)."
        }
        $serial = Get-ReadyDeviceSerial
        if ($serial -and $serial.StartsWith("emulator-")) {
            $bootComplete = (& $adb -s $serial shell getprop sys.boot_completed).Trim()
            if ($bootComplete -eq "1") {
                break
            }
        }
        Start-Sleep -Seconds 5
    }
    if (-not $serial -or -not $serial.StartsWith("emulator-") -or $bootComplete -ne "1") {
        throw "Timed out waiting for Android emulator '$avd' to finish booting."
    }
}

$env:ANDROID_SERIAL = $serial
Write-Output "Using Android device $serial."

& $gradleWrapper installDebug --console=plain
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& $adb -s $serial shell monkey -p com.biodex 1
exit $LASTEXITCODE
