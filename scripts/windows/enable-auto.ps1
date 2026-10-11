. (Join-Path $PSScriptRoot 'common.ps1')
. (Join-Path $PSScriptRoot 'napcat-preflight.ps1')
Assert-FreePort 8080
if ([string]::IsNullOrWhiteSpace($env:TWITTER_AUTH_TOKEN)) { throw 'Set TWITTER_AUTH_TOKEN in config.env first.' }
if ($env:QQ_TEST_GROUP_ID -notmatch '^[1-9][0-9]*$') { throw 'Set QQ_TEST_GROUP_ID in config.env first.' }
$base = $null
if (-not [Uri]::TryCreate($env:QQ_API_BASE_URL, [UriKind]::Absolute, [ref]$base) -or
    $base.Scheme -notin @('http','https') -or $base.UserInfo -or $base.Query -or $base.Fragment) {
    throw 'Set QQ_API_BASE_URL to an HTTP(S) URL without credentials/query/fragment.'
}
$headers = @{}
if (-not [string]::IsNullOrWhiteSpace($env:QQ_ACCESS_TOKEN)) { $headers.Authorization = 'Bearer ' + $env:QQ_ACCESS_TOKEN }
Confirm-NapCatLogin -BaseUri $base -Headers $headers
try {
    $feed = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:1200/twitter/user/wstcg' -TimeoutSec 30
    $settings = New-Object System.Xml.XmlReaderSettings
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $text = New-Object System.IO.StringReader([string]$feed.Content)
    $reader = [System.Xml.XmlReader]::Create($text,$settings)
    try {
        $document = New-Object System.Xml.XmlDocument
        $document.XmlResolver = $null
        $document.Load($reader)
        if ($document.DocumentElement.LocalName -notin @('rss','feed')) { throw 'Not an RSS/Atom feed.' }
    } finally { $reader.Dispose(); $text.Dispose() }
} catch { throw 'RSSHub WS feed check failed. Start RSSHub and check its X login configuration.' }

# Preserve all existing secrets/settings; update only the public operational switches.
$lines = @([System.IO.File]::ReadAllLines($configFile))
foreach ($setting in @('QQ_ENABLED=true','QQ_IMAGE_FORWARD_ENABLED=true','RSSHUB_POLLING_ENABLED=true','RSSHUB_POLLING_INTERVAL=PT5M')) {
    $key = ($setting -split '=',2)[0]
    $found = $false
    for ($i=0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match ('^\s*' + [regex]::Escape($key) + '\s*=')) { $lines[$i]=$setting; $found=$true }
    }
    if (-not $found) { $lines += $setting }
}
[System.IO.File]::WriteAllLines($configFile,$lines,(New-Object System.Text.UTF8Encoding($false)))
Write-Host 'NapCat login and RSSHub WS feed checks passed. No test message was sent.'
Write-Host 'Automatic QQ delivery enabled: first check after 10 seconds, then every 5 minutes.'
Write-Host 'Historical baseline is suppressed. Failed/limited new records remain pending.'
Write-Host 'Start start-app.cmd now. Keep Java, RSSHub and NapCat running; prevent laptop sleep.'
