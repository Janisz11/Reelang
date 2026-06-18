

$repoRoot = $PSScriptRoot
$pythonExe = Join-Path $repoRoot "backend\.venv\Scripts\python.exe"
$adbExe = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$results = @()

function Run-Step {
    param(
        [string]$Name,
        [string]$WorkingDir,
        [string]$Command,
        [string[]]$Arguments
    )

    Write-Host ""
    Write-Host "==== $Name ====" -ForegroundColor Cyan

    Push-Location $WorkingDir
    try {
        & $Command @Arguments
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }

    $script:results += [PSCustomObject]@{ Name = $Name; ExitCode = $exitCode }
}


function Run-AndroidInstrumentedTests {
    param(
        [string]$Name,
        [string]$AndroidDir,
        [string]$ApplicationId,
        [string]$TestRunner
    )

    Write-Host ""
    Write-Host "==== $Name ====" -ForegroundColor Cyan

    Push-Location $AndroidDir
    try {
        # 1. Build the app and the test APK
        & ".\gradlew.bat" assembleDebug assembleDebugAndroidTest
        if ($LASTEXITCODE -ne 0) {
            $script:results += [PSCustomObject]@{ Name = $Name; ExitCode = $LASTEXITCODE }
            return
        }

        # 2. Install both APKs
        & ".\gradlew.bat" installDebug
        if ($LASTEXITCODE -ne 0) {
            $script:results += [PSCustomObject]@{ Name = $Name; ExitCode = $LASTEXITCODE }
            return
        }

        $testApk = Get-ChildItem -Path "app\build\outputs\apk\androidTest\debug" -Filter "*.apk" | Select-Object -First 1
        if (-not $testApk) {
            Write-Host "Could not find androidTest APK" -ForegroundColor Red
            $script:results += [PSCustomObject]@{ Name = $Name; ExitCode = 1 }
            return
        }

        & $adbExe install -r $testApk.FullName
        if ($LASTEXITCODE -ne 0) {
            $script:results += [PSCustomObject]@{ Name = $Name; ExitCode = $LASTEXITCODE }
            return
        }

        # 3. Run the instrumented tests directly via am instrument
        $instrumentTarget = "$ApplicationId.test/$TestRunner"
        $output = & $adbExe shell am instrument -w -e package $ApplicationId $instrumentTarget 2>&1
        $output | ForEach-Object { Write-Host $_ }

        $outputText = $output -join "`n"

        # am instrument exits 0 even on test failures, so parse the textual result.
        if ($outputText -match "OK \(\d+ tests?\)") {
            $exitCode = 0
        } elseif ($outputText -match "FAILURES!!!" -or $outputText -match "INSTRUMENTATION_FAILED" -or $outputText -match "shortMsg=") {
            $exitCode = 1
        } else {
            # Unexpected output (e.g. nothing ran) -> treat as failure
            Write-Host "Could not determine test result from am instrument output" -ForegroundColor Red
            $exitCode = 1
        }

        $script:results += [PSCustomObject]@{ Name = $Name; ExitCode = $exitCode }
    } finally {
        Pop-Location
    }
}

# ========================= UNIT TESTS =========================
Write-Host "`n########## UNIT TESTS ##########" -ForegroundColor Magenta

Run-Step -Name "Android unit tests" `
    -WorkingDir (Join-Path $repoRoot "android") `
    -Command ".\gradlew.bat" -Arguments @("testDebugUnitTest")

Run-Step -Name "Backend unit tests" `
    -WorkingDir (Join-Path $repoRoot "backend") `
    -Command $pythonExe -Arguments @("-m", "pytest", "tests/unit", "-v")

Run-Step -Name "ReeLang AI unit tests" `
    -WorkingDir (Join-Path $repoRoot "reelang_ai") `
    -Command $pythonExe -Arguments @("-m", "pytest", "tests/unit", "-v")

# ====================== INTEGRATION TESTS ======================
Write-Host "`n########## INTEGRATION TESTS ##########" -ForegroundColor Magenta

Run-AndroidInstrumentedTests -Name "Android integration tests" `
    -AndroidDir (Join-Path $repoRoot "android") `
    -ApplicationId "com.example.reelang" `
    -TestRunner "androidx.test.runner.AndroidJUnitRunner"

Run-Step -Name "Backend integration tests" `
    -WorkingDir (Join-Path $repoRoot "backend") `
    -Command $pythonExe -Arguments @("-m", "pytest", "tests/integration", "-v")

Run-Step -Name "ReeLang AI integration tests" `
    -WorkingDir (Join-Path $repoRoot "reelang_ai") `
    -Command $pythonExe -Arguments @("-m", "pytest", "tests/integration", "-v")

# ============================ SUMMARY ============================
Write-Host "`n########## SUMMARY ##########" -ForegroundColor Magenta
foreach ($r in $results) {
    if ($r.ExitCode -eq 0) {
        Write-Host ("{0,-30} PASS" -f $r.Name) -ForegroundColor Green
    } else {
        Write-Host ("{0,-30} FAIL (exit $($r.ExitCode))" -f $r.Name) -ForegroundColor Red
    }
}

$failed = $results | Where-Object { $_.ExitCode -ne 0 }
if ($failed.Count -gt 0) {
    exit 1
}
exit 0