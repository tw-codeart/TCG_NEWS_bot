. (Join-Path $PSScriptRoot 'common.ps1')
if([string]::IsNullOrWhiteSpace($env:YUGIOH_JP_BROWSER_EXECUTABLE)) {
    $candidates=@((Join-Path ${env:ProgramFiles(x86)} 'Microsoft/Edge/Application/msedge.exe'),(Join-Path $env:ProgramFiles 'Google/Chrome/Application/chrome.exe'))
    $env:YUGIOH_JP_BROWSER_EXECUTABLE=$candidates|Where-Object {Test-Path -LiteralPath $_ -PathType Leaf}|Select-Object -First 1
}
Write-Host 'Read-only article check: no database, no QQ sending, no normal browser profile.'
& (Join-Path $PSScriptRoot 'runtime/java/bin/java.exe') '-Dfile.encoding=UTF-8' '-Dloader.main=com.twcodeart.tcgnews.source.ocg.ArticleReaderCheck' '-cp' 'app.jar' 'org.springframework.boot.loader.launch.PropertiesLauncher'
if($LASTEXITCODE -ne 0){throw 'ARTICLE_READ_FAILED: do not enable JP collection yet. Check the error above; a challenge needs manual investigation.'}
