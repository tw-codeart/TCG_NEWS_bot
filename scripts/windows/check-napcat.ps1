. (Join-Path $PSScriptRoot 'common.ps1')
. (Join-Path $PSScriptRoot 'napcat-preflight.ps1')
$base = $null
if (-not [Uri]::TryCreate($env:QQ_API_BASE_URL,[UriKind]::Absolute,[ref]$base) -or
    $base.Scheme -notin @('http','https') -or $base.UserInfo -or $base.Query -or $base.Fragment) {
    throw 'NAPCAT_CONFIG: Set QQ_API_BASE_URL to the OneBot HTTP server URL without credentials/query/fragment.'
}
$headers = @{}
if (-not [string]::IsNullOrWhiteSpace($env:QQ_ACCESS_TOKEN)) { $headers.Authorization = 'Bearer ' + $env:QQ_ACCESS_TOKEN }
Write-Host ('Checking configured NapCat HTTP host=' + $base.Host + ', port=' + $base.Port + '. Credentials are not printed.')
Confirm-NapCatLogin -BaseUri $base -Headers $headers
