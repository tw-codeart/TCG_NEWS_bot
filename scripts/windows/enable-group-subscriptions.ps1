. (Join-Path $PSScriptRoot 'common.ps1')
Assert-FreePort 8080
if ($env:GAME_STORAGE_SEPARATED_ENABLED -ne 'true') { throw 'Complete separated storage setup first; preserve the entire data folder.' }
if ($env:QQ_TEST_GROUP_ID -notmatch '^[1-9][0-9]*$') { throw 'Keep the original configured QQ test group ID for legacy delivery stamps.' }
$secret = $env:QQ_COMMAND_SECRET
if ([string]::IsNullOrWhiteSpace($secret)) {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $secret = [Convert]::ToBase64String($bytes)
}
if ($secret.Length -lt 16) { throw 'QQ_COMMAND_SECRET must have at least 16 characters.' }
$lines = @([System.IO.File]::ReadAllLines($configFile))
foreach ($setting in @('QQ_GROUP_SUBSCRIPTIONS_ENABLED=true',('QQ_COMMAND_SECRET=' + $secret))) {
    $key = ($setting -split '=',2)[0]; $found = $false
    for ($i=0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match ('^\s*' + [regex]::Escape($key) + '\s*=')) { $lines[$i]=$setting; $found=$true }
    }
    if (-not $found) { $lines += $setting }
}
[System.IO.File]::WriteAllLines($configFile,$lines,(New-Object System.Text.UTF8Encoding($false)))
$client = @{name='tcg-news-events';enable=$true;url='http://127.0.0.1:8080/api/qq/events';messagePostFormat='array';reportSelfMessage=$false;token=$secret;debug=$false}
[System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'napcat-http-client.local.json'),($client | ConvertTo-Json),(New-Object System.Text.UTF8Encoding($false)))
Write-Host 'Group subscription mode enabled. Existing API tokens and group ID were retained.'
Write-Host 'Add a NapCat HTTP CLIENT with the URL/token from napcat-http-client.local.json (do not share this private file).'
Write-Host 'Keep the existing NapCat HTTP SERVER for sending. Start Java with start-auto.cmd.'
Write-Host 'In a group, an owner/admin can use /news subscribe ws or /news subscribe ocg.'
Write-Host 'After activation, retain QQ_GROUP_SUBSCRIPTIONS_ENABLED=true and the original QQ_TEST_GROUP_ID.'
