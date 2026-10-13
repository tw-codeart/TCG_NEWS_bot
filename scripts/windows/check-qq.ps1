. (Join-Path $PSScriptRoot 'common.ps1')
. (Join-Path $PSScriptRoot 'onebot-config.ps1')
. (Join-Path $PSScriptRoot 'onebot-preflight.ps1')
$adapter=Get-QqAdapterName
$base=$null
if(-not [Uri]::TryCreate($env:QQ_API_BASE_URL,[UriKind]::Absolute,[ref]$base) -or $base.Scheme -notin @('http','https') -or $base.UserInfo -or $base.Query -or $base.Fragment) { throw 'QQ_CONFIG: Set QQ_API_BASE_URL to the OneBot HTTP server URL without credentials/query/fragment.' }
$headers=@{}
if($env:QQ_ACCESS_TOKEN) { $headers.Authorization='Bearer '+$env:QQ_ACCESS_TOKEN }
Write-Host ('Checking adapter='+$adapter+', HTTP host='+$base.Host+', port='+$base.Port+'. Credentials are not printed.')
Confirm-OneBotLogin -BaseUri $base -Headers $headers -Adapter $adapter
