$ErrorActionPreference='Stop'
$news=@(Invoke-RestMethod 'http://127.0.0.1:8080/api/news?game=YUGIOH_OCG' -TimeoutSec 15)
$latest=$news|Where-Object {$_.category -eq 'CARD_REVEAL' -and $_.sourceUrl -match '^https://x\.com/yu_gi_oh_jp/status/[0-9]+$'}|
    Sort-Object publishedAt,createdAt -Descending|Select-Object -First 1
if($null -eq $latest){throw 'No stored official JP card post. Fetch must successfully retrieve the official article first.'}
$postId=([string]$latest.sourceUrl -split '/')[-1]
Write-Host 'Sending ONE stored @yu_gi_oh_jp post to the configured QQ test group. No scraping is triggered.'
Invoke-RestMethod -Method Post -Uri ('http://127.0.0.1:8080/api/notifications/posts?game=YUGIOH_OCG&limit=1&postId='+$postId) -TimeoutSec 600|ConvertTo-Json -Depth 5
