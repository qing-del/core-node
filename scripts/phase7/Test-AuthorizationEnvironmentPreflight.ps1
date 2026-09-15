[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$BaseUri,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$RedisHost,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$MySqlHost,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$EvidenceDirectory,

    [ValidateRange(1, 65535)]
    [int]$RedisPort = 6379,

    [ValidateRange(1, 65535)]
    [int]$MySqlPort = 3306,

    [ValidateRange(1, 60)]
    [int]$TimeoutSeconds = 10,

    # This is deliberately opt-in. The application does not expose a JWK endpoint in the first release.
    [string]$JwkSetUri
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function New-Result {
    param([string]$Name, [bool]$Passed, [string]$Detail)
    [PSCustomObject]@{
        name = $Name
        passed = $Passed
        detail = $Detail
    }
}

function Test-TcpEndpoint {
    param([string]$Name, [string]$HostName, [int]$Port)
    try {
        $reachable = Test-NetConnection -ComputerName $HostName -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue
        if ($reachable) {
            return New-Result $Name $true ("tcp://{0}:{1} reachable" -f $HostName, $Port)
        }
        return New-Result $Name $false ("tcp://{0}:{1} is not reachable" -f $HostName, $Port)
    } catch {
        return New-Result $Name $false ("tcp probe failed for {0}:{1}" -f $HostName, $Port)
    }
}

function Test-HttpGet {
    param([string]$Name, [string]$Uri, [int]$RequestTimeoutSeconds)
    try {
        $response = Invoke-WebRequest -Uri $Uri -Method Get -TimeoutSec $RequestTimeoutSeconds -UseBasicParsing
        if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
            return New-Result $Name $true ("GET {0} returned HTTP {1}" -f $Uri, $response.StatusCode)
        }
        return New-Result $Name $false ("GET {0} returned HTTP {1}" -f $Uri, $response.StatusCode)
    } catch {
        return New-Result $Name $false ("GET {0} did not return a successful response" -f $Uri)
    }
}

function Test-JwkSet {
    param([string]$Uri, [int]$RequestTimeoutSeconds)
    try {
        $response = Invoke-WebRequest -Uri $Uri -Method Get -TimeoutSec $RequestTimeoutSeconds -UseBasicParsing
        $payload = $response.Content | ConvertFrom-Json
        if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300 -or $null -eq $payload.keys -or $payload.keys.Count -lt 1) {
            return New-Result 'optional-jwk-set' $false ("GET {0} did not return a non-empty JWK Set" -f $Uri)
        }
        $rsaCount = @($payload.keys | Where-Object { $_.kty -eq 'RSA' -and $_.alg -eq 'RS256' }).Count
        if ($rsaCount -lt 1) {
            return New-Result 'optional-jwk-set' $false ("GET {0} returned no RS256 RSA key" -f $Uri)
        }
        return New-Result 'optional-jwk-set' $true ("GET {0} returned {1} JWK(s), including {2} RS256 RSA key(s)" -f $Uri, $payload.keys.Count, $rsaCount)
    } catch {
        return New-Result 'optional-jwk-set' $false ("GET {0} did not return a valid non-empty RS256 JWK Set" -f $Uri)
    }
}

try {
    $base = [Uri]$BaseUri
    if ($base.Scheme -ne 'http' -and $base.Scheme -ne 'https') {
        throw 'BaseUri must use http or https.'
    }
    if (-not [string]::IsNullOrWhiteSpace($JwkSetUri)) {
        $jwk = [Uri]$JwkSetUri
        if ($jwk.Scheme -ne 'http' -and $jwk.Scheme -ne 'https') {
            throw 'JwkSetUri must use http or https when supplied.'
        }
    }

    $null = New-Item -ItemType Directory -Force -Path $EvidenceDirectory
    $normalizedBaseUri = $base.AbsoluteUri.TrimEnd('/')
    $results = @(
        (Test-TcpEndpoint 'redis-tcp' $RedisHost $RedisPort),
        (Test-TcpEndpoint 'mysql-tcp' $MySqlHost $MySqlPort),
        (Test-HttpGet 'actuator-liveness' ($normalizedBaseUri + '/actuator/health/liveness') $TimeoutSeconds),
        (Test-HttpGet 'actuator-readiness' ($normalizedBaseUri + '/actuator/health/readiness') $TimeoutSeconds)
    )
    if (-not [string]::IsNullOrWhiteSpace($JwkSetUri)) {
        $results += Test-JwkSet $JwkSetUri $TimeoutSeconds
    }

    $report = [PSCustomObject]@{
        generatedAtUtc = [DateTime]::UtcNow.ToString('o')
        mode = 'strict-read-only-preflight'
        baseUri = $normalizedBaseUri
        redis = [PSCustomObject]@{ host = $RedisHost; port = $RedisPort }
        mysql = [PSCustomObject]@{ host = $MySqlHost; port = $MySqlPort }
        optionalJwkSetUriChecked = -not [string]::IsNullOrWhiteSpace($JwkSetUri)
        results = $results
        passed = -not ($results | Where-Object { -not $_.passed })
    }
    $stamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
    $jsonPath = Join-Path $EvidenceDirectory ("phase7-preflight-{0}.json" -f $stamp)
    $markdownPath = Join-Path $EvidenceDirectory ("phase7-preflight-{0}.md" -f $stamp)
    $report | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 -Path $jsonPath
    $lines = @('# Phase 7 environment preflight', '', ("Generated (UTC): {0}" -f $report.generatedAtUtc), '', '| check | passed | detail |', '| --- | --- | --- |')
    foreach ($result in $results) {
        $lines += ("| {0} | {1} | {2} |" -f $result.name, $result.passed, $result.detail)
    }
    $lines | Set-Content -Encoding utf8 -Path $markdownPath
    Write-Host ("Evidence: {0}" -f $jsonPath)
    Write-Host ("Evidence: {0}" -f $markdownPath)
    if (-not $report.passed) {
        [Console]::Error.WriteLine('Phase 7 preflight is blocked. See the evidence files; no external state was changed.')
        exit 1
    }
} catch {
    [Console]::Error.WriteLine('Phase 7 preflight could not run safely: ' + $_.Exception.Message)
    exit 2
}
