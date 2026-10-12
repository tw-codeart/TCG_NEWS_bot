. (Join-Path $PSScriptRoot 'common.ps1')
if(Get-NetTCPConnection -LocalPort 1200 -State Listen -ErrorAction SilentlyContinue) {
try {
    $response=Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:1200/twitter/user/yu_gi_oh_jp' -TimeoutSec 30
    $settings=New-Object System.Xml.XmlReaderSettings
    $settings.DtdProcessing=[System.Xml.DtdProcessing]::Prohibit;$settings.XmlResolver=$null
    $text=New-Object IO.StringReader([string]$response.Content)
    $reader=[System.Xml.XmlReader]::Create($text,$settings)
    try {
        $document=New-Object System.Xml.XmlDocument;$document.XmlResolver=$null;$document.Load($reader)
        if($document.DocumentElement.LocalName -notin @('rss','feed')){throw 'Not an RSS/Atom feed.'}
    }finally{$reader.Dispose();$text.Dispose()}
}catch{throw 'Official JP RSSHub feed check failed. Start RSSHub and check its X login. No settings changed.'}
} else { Write-Host 'RSSHub is not running. Feed verification is deferred to startup polling.' }
$lines=@([IO.File]::ReadAllLines($configFile))
$publicSettings=@('RSSHUB_OCG_ENABLED=true','RSSHUB_OCG_JP_ENABLED=true','RSSHUB_OCG_JP_ROUTE=/twitter/user/yu_gi_oh_jp')
$browserCandidates=@((Join-Path ${env:ProgramFiles(x86)} 'Microsoft/Edge/Application/msedge.exe'),(Join-Path $env:ProgramFiles 'Microsoft/Edge/Application/msedge.exe'),(Join-Path $env:ProgramFiles 'Google/Chrome/Application/chrome.exe'))
$articleBrowser=$browserCandidates|Where-Object {Test-Path -LiteralPath $_ -PathType Leaf}|Select-Object -First 1
if($articleBrowser){
    $env:YUGIOH_JP_BROWSER_EXECUTABLE=$articleBrowser
    if((Test-Path -LiteralPath (Join-Path $PSScriptRoot 'app.jar')) -and (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'runtime/java/bin/java.exe'))) {
        & (Join-Path $PSScriptRoot 'runtime/java/bin/java.exe') '-Dfile.encoding=UTF-8' '-Dloader.main=com.twcodeart.tcgnews.source.ocg.ArticleReaderCheck' '-cp' 'app.jar' 'org.springframework.boot.loader.launch.PropertiesLauncher'
        if($LASTEXITCODE -ne 0){throw 'Official article browser check failed. No settings changed; keep the JP source disabled until retrieval is verified.'}
    }else{throw 'Missing app.jar or bundled Java. Install the complete update before enabling JP collection.'}
    $publicSettings+=@('YUGIOH_JP_BROWSER_ENABLED=true',('YUGIOH_JP_BROWSER_EXECUTABLE='+$articleBrowser))
    Write-Host 'Verified an installed browser with an isolated temporary profile for article reading.'
}
else {throw 'No Edge/Chrome executable detected. No settings changed; configure a supported article reader before enabling JP collection.'}
foreach($setting in $publicSettings) {
    $key=($setting -split '=',2)[0];$found=$false
    for($i=0;$i -lt $lines.Count;$i++) {if($lines[$i] -match ('^\s*'+[regex]::Escape($key)+'\s*=')){$lines[$i]=$setting;$found=$true}}
    if(-not $found){$lines+=$setting}
}
[IO.File]::WriteAllLines($configFile,$lines,(New-Object Text.UTF8Encoding($false)))
Write-Host 'Both OCG accounts enabled: @YuGiOh_OCG_INFO images and @yu_gi_oh_jp article new-card images.'
Write-Host 'Restart Java (or stop-bot.cmd then start-bot.cmd). Existing OCG group subscriptions apply to both accounts.'
Write-Host 'First matching JP snapshot is stored without automatic historical forwarding. Article HTTP access must also succeed.'
