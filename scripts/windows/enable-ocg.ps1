. (Join-Path $PSScriptRoot 'common.ps1')
# Read-only preflight: do not change configuration if the official-account feed is unavailable.
try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:1200/twitter/user/YuGiOh_OCG_INFO' -TimeoutSec 30
    $settings = New-Object System.Xml.XmlReaderSettings
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $text = New-Object System.IO.StringReader([string]$response.Content)
    $reader = [System.Xml.XmlReader]::Create($text,$settings)
    try {
        $document = New-Object System.Xml.XmlDocument
        $document.XmlResolver = $null
        $document.Load($reader)
        if ($document.DocumentElement.LocalName -notin @('rss','feed')) { throw 'Not an RSS/Atom feed.' }
        $entries = $document.SelectNodes("//*[local-name()='item' or local-name()='entry']").Count
    } finally { $reader.Dispose(); $text.Dispose() }
} catch {
    throw 'OCG RSSHub feed check failed. Config was not changed; WS can continue running. Check the OCG route in RSSHub.'
}
$lines = @([System.IO.File]::ReadAllLines($configFile))
foreach ($setting in @('RSSHUB_OCG_ENABLED=true','RSSHUB_OCG_ROUTE=/twitter/user/YuGiOh_OCG_INFO')) {
    $key = ($setting -split '=',2)[0]
    $found = $false
    for ($i=0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match ('^\s*' + [regex]::Escape($key) + '\s*=')) { $lines[$i]=$setting; $found=$true }
    }
    if (-not $found) { $lines += $setting }
}
[System.IO.File]::WriteAllLines($configFile,$lines,(New-Object System.Text.UTF8Encoding($false)))
Write-Host ('OCG RSSHub feed is valid. Raw RSS/Atom entries: ' + $entries + ' (before card-announcement filtering).')
Write-Host 'OCG enabled in config.env. Existing secrets, QQ group and WS settings were preserved.'
Write-Host 'Restart only Java with start-auto.cmd to apply. Keep RSSHub and NapCat running.'
Write-Host 'OCG first nonempty snapshot is history only. Subsequent new announcements can notify QQ.'
Write-Host 'Check /api/notifications/status: it should list ws-jp-rsshub-card-reveals and ocg-jp-rsshub-card-reveals.'
