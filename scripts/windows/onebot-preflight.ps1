function Invoke-OneBotCheck {
    param([Uri]$BaseUri,[hashtable]$Headers,[string]$Action)
    try {
        return Invoke-RestMethod -Method Post -Uri ($BaseUri.AbsoluteUri.TrimEnd('/')+'/'+$Action) -Headers $Headers -ContentType 'application/json' -Body '{}' -TimeoutSec 10
    } catch {
        $failure=$_.Exception;$code=$null;$webStatus=$null
        while($null -ne $failure) {
            if($null -ne $failure.Response -and $null -ne $failure.Response.StatusCode) { $code=[int]$failure.Response.StatusCode }
            if($failure -is [System.Net.WebException]) { $webStatus=$failure.Status }
            $failure=$failure.InnerException
        }
        if($code -in @(401,403)) { throw 'QQ_AUTH: QQ_ACCESS_TOKEN must match the OneBot HTTP server Token, not its WebUI Token.' }
        if($code -eq 404) { throw 'QQ_ROUTE: Use the OneBot 11 HTTP API address, not the WebUI, Milky or Satori address.' }
        if($null -ne $code) { throw ('QQ_HTTP: HTTP '+$code+'. Check the adapter login/API and its local logs.') }
        if($webStatus -eq [System.Net.WebExceptionStatus]::Timeout) { throw 'QQ_TIMEOUT: OneBot HTTP API did not respond within 10 seconds.' }
        throw 'QQ_UNAVAILABLE: Start the selected QQ adapter, log in and enable its OneBot 11 HTTP server.'
    }
}
function Confirm-OneBotLogin {
    param([Uri]$BaseUri,[hashtable]$Headers,[string]$Adapter='')
    if($Adapter -eq 'llbot') {
        $version=Invoke-OneBotCheck $BaseUri $Headers 'get_version_info'
        if($null -eq $version -or $version.status -ne 'ok' -or $null -eq $version.retcode -or $version.retcode -ne 0 -or $version.data.app_name -ne 'LLOneBot') {
            throw 'QQ_ADAPTER_MISMATCH: Expected LLBot (OneBot app_name=LLOneBot). Stop old NapCat and check the configured API port.'
        }
    }
    $login=Invoke-OneBotCheck $BaseUri $Headers 'get_login_info'
    if($null -eq $login -or $login.status -ne 'ok' -or $null -eq $login.retcode -or $login.retcode -ne 0 -or [string]$login.data.user_id -notmatch '^[1-9][0-9]*$') {
        throw 'QQ_LOGIN: OneBot did not return a valid logged-in bot identity.'
    }
    # get_login_info can be cached even after the actual QQ connection has gone offline.
    $status=Invoke-OneBotCheck $BaseUri $Headers 'get_status'
    if($null -eq $status -or $status.status -ne 'ok' -or $null -eq $status.retcode -or $status.retcode -ne 0) { throw 'QQ_STATUS: OneBot did not confirm a successful status query.' }
    if($status.data.online -isnot [bool] -or -not $status.data.online) { throw 'QQ_OFFLINE: Log in again in the selected QQ adapter.' }
    Write-Host 'QQ_OK: OneBot API reports a bot identity and online connection. No group message was sent.'
}
