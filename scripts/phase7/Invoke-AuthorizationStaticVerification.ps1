[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$EvidenceDirectory,

    [ValidateNotNullOrEmpty()]
    [string]$JavaHome = 'D:\CodeTool\JDK17',

    [ValidateNotNullOrEmpty()]
    [string]$MavenExecutable = 'mvn',

    [switch]$SkipMaven
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-RepositoryRoot {
    $candidate = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
    if (-not (Test-Path (Join-Path $candidate 'pom.xml'))) {
        throw 'Repository root with pom.xml was not found from the script location.'
    }
    return $candidate
}

function Get-SurefireSummary {
    param([string]$RepositoryRoot, [datetime]$StartedAtUtc)
    $reports = Get-ChildItem -Path $RepositoryRoot -Recurse -File -Filter 'TEST-*.xml' |
        Where-Object { $_.LastWriteTimeUtc -ge $StartedAtUtc.AddSeconds(-2) }
    $total = 0
    $failures = 0
    $errors = 0
    $skipped = 0
    foreach ($report in $reports) {
        try {
            [xml]$xml = Get-Content -Raw -Path $report.FullName
            $suite = $xml.testsuite
            if ($null -ne $suite) {
                $total += [int]$suite.tests
                $failures += [int]$suite.failures
                $errors += [int]$suite.errors
                $skipped += [int]$suite.skipped
            }
        } catch {
            throw ('Could not parse Surefire report: ' + $report.FullName)
        }
    }
    return [PSCustomObject]@{
        reportCount = @($reports).Count
        tests = $total
        failures = $failures
        errors = $errors
        skipped = $skipped
    }
}

try {
    $repositoryRoot = Get-RepositoryRoot
    $javaBinary = Join-Path $JavaHome 'bin\java.exe'
    if (-not (Test-Path $javaBinary)) {
        throw ('JDK java.exe was not found: ' + $javaBinary)
    }
    $savedErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $javaVersionOutput = @(& $javaBinary -version 2>&1 | ForEach-Object {
                if ($_ -is [System.Management.Automation.ErrorRecord]) {
                    $_.Exception.Message
                } else {
                    $_.ToString()
                }
            }) -join "`n"
        $javaVersionExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $savedErrorActionPreference
    }
    if ($javaVersionExitCode -ne 0 -or $javaVersionOutput -notmatch 'version "21(?:\.|[^0-9])') {
        throw 'Phase 7 static verification requires JDK 21.'
    }
    $maven = Get-Command $MavenExecutable -ErrorAction Stop
    $null = New-Item -ItemType Directory -Force -Path $EvidenceDirectory
    $stamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
    $commandPath = Join-Path $EvidenceDirectory ("phase7-static-command-{0}.txt" -f $stamp)
    $logPath = Join-Path $EvidenceDirectory ("phase7-static-maven-{0}.log" -f $stamp)
    $jsonPath = Join-Path $EvidenceDirectory ("phase7-static-summary-{0}.json" -f $stamp)
    $markdownPath = Join-Path $EvidenceDirectory ("phase7-static-summary-{0}.md" -f $stamp)
    $mavenArguments = @(
        '-o',
        '-pl', ':middleware-common-security,:middleware-common-web,:middleware-module-system-biz,:middleware-server',
        '-am',
        '-Denforcer.skip=true',
        'test'
    )
    $commandText = @(
        'mode=strict-static-verification',
        'network=offline Maven dependency resolution only (-o); no container command is invoked',
        ('repositoryRoot=' + $repositoryRoot),
        ('javaHome=' + (Resolve-Path $JavaHome).Path),
        ('javaVersion=' + ($javaVersionOutput -replace "`r?`n", ' | ')),
        ('command=' + $maven.Source + ' ' + ($mavenArguments -join ' '))
    )
    $commandText | Set-Content -Encoding utf8 -Path $commandPath

    $originalJavaHome = $env:JAVA_HOME
    $originalPath = $env:Path
    $env:JAVA_HOME = (Resolve-Path $JavaHome).Path
    $env:Path = ((Join-Path $env:JAVA_HOME 'bin') + [IO.Path]::PathSeparator + $originalPath)
    try {
        if ($SkipMaven) {
            'Maven execution skipped by explicit -SkipMaven.' | Set-Content -Encoding utf8 -Path $logPath
            $mavenExitCode = $null
            $summary = $null
        } else {
            $startedAtUtc = [DateTime]::UtcNow
            $savedMavenErrorActionPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                & $maven.Source @mavenArguments 2>&1 | Tee-Object -FilePath $logPath | Out-Host
                $mavenExitCode = $LASTEXITCODE
            } finally {
                $ErrorActionPreference = $savedMavenErrorActionPreference
            }
            $summary = Get-SurefireSummary $repositoryRoot $startedAtUtc
        }
    } finally {
        $env:JAVA_HOME = $originalJavaHome
        $env:Path = $originalPath
    }

    $report = [PSCustomObject]@{
        generatedAtUtc = [DateTime]::UtcNow.ToString('o')
        mode = if ($SkipMaven) { 'static-command-dry-run' } else { 'strict-static-verification' }
        repositoryRoot = $repositoryRoot
        javaVersion = ($javaVersionOutput -replace "`r?`n", ' | ')
        mavenCommand = $maven.Source + ' ' + ($mavenArguments -join ' ')
        mavenExitCode = $mavenExitCode
        surefire = $summary
        passed = if ($SkipMaven) { $true } else { $mavenExitCode -eq 0 -and $summary.failures -eq 0 -and $summary.errors -eq 0 }
    }
    $report | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 -Path $jsonPath
    $lines = @(
        '# Phase 7 static verification',
        '',
        ('Generated (UTC): ' + $report.generatedAtUtc),
        ('Mode: ' + $report.mode),
        ('Maven exit code: ' + $report.mavenExitCode),
        ('Passed: ' + $report.passed),
        '',
        'This command uses Maven offline dependency resolution and does not invoke Docker or a remote write operation.'
    )
    if ($null -ne $summary) {
        $lines += @('', '| reports | tests | failures | errors | skipped |', '| ---: | ---: | ---: | ---: | ---: |',
            ("| {0} | {1} | {2} | {3} | {4} |" -f $summary.reportCount, $summary.tests, $summary.failures, $summary.errors, $summary.skipped))
    }
    $lines | Set-Content -Encoding utf8 -Path $markdownPath
    Write-Host ('Evidence: ' + $commandPath)
    Write-Host ('Evidence: ' + $logPath)
    Write-Host ('Evidence: ' + $jsonPath)
    Write-Host ('Evidence: ' + $markdownPath)
    if (-not $report.passed) {
        [Console]::Error.WriteLine('Phase 7 static verification failed. See the evidence files.')
        exit 1
    }
} catch {
    [Console]::Error.WriteLine('Phase 7 static verification could not run safely: ' + $_.Exception.Message)
    exit 2
}
