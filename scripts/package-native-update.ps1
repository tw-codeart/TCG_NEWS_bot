param([string]$OutputDirectory=(Join-Path $PSScriptRoot '../deployment-output'))
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$jar=Join-Path $root 'target/tcg-news-bot-0.1.0-SNAPSHOT.jar'
if(-not (Test-Path -LiteralPath $jar -PathType Leaf)){throw 'Run mvn test and mvn package first.'}
$null=New-Item -ItemType Directory -Path $OutputDirectory -Force
$name='tcg-native-update-'+(Get-Date -Format 'yyyyMMdd-HHmmss')
$out=Join-Path (Resolve-Path -LiteralPath $OutputDirectory).Path $name
if(Test-Path -LiteralPath $out){throw 'Output already exists.'}
$null=New-Item -ItemType Directory -Path $out
Copy-Item -LiteralPath $jar -Destination (Join-Path $out 'app.jar')
Get-ChildItem -LiteralPath (Join-Path $root 'scripts/windows') -File |
    Where-Object {$_.Extension -in @('.cmd','.ps1') -or $_.Name -in @('config.example.env','operations.example.json')} |
    Copy-Item -Destination $out
foreach($file in @('windows-operations.md','qq-group-subscriptions.md','separated-game-storage.md','automatic-qq-delivery.md','compact-forwards.md','ocg-linked-articles.md','llbot-migration.md')) {
    Copy-Item -LiteralPath (Join-Path $root ('docs/'+$file)) -Destination $out
}
$instructions=@'
Incremental update for an existing native Windows bundle; not a standalone installer.
Stop Java/RSSHub, copy app.jar and scripts into the original bundle folder.
Preserve config.env, data, runtime, rsshub and private callback settings.
Do not overwrite config.env with config.example.env or downgrade the JAR.
Keep your selected NapCat/LLBot adapter logged in, then use start-bot.cmd (do not start duplicate services).
Use check-bot.cmd for status and stop-bot.cmd to stop owned Java/RSSHub.
install-autostart.cmd is optional and installs a current-user logon task.
See llbot-migration.md for LLBot setup; use check-qq.cmd for read-only API/online checks.
See windows-operations.md for QQ launcher, retention and optional Japanese OCR.
This update excludes runtimes, QQ adapter/Tesseract installers, credentials and databases.
'@
[IO.File]::WriteAllText((Join-Path $out 'UPDATE.txt'),$instructions,(New-Object Text.UTF8Encoding($false)))
$zip=$out+'.zip'
Compress-Archive -Path (Join-Path $out '*') -DestinationPath $zip
Write-Host ('Native update: '+$zip)
Write-Host ('SHA256: '+(Get-FileHash -LiteralPath $zip).Hash)
