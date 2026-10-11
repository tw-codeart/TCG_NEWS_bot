function Confirm-NapCatLogin {
    param([Uri]$BaseUri, [hashtable]$Headers)
    try {
        $login = Invoke-RestMethod -Method Post -Uri ($BaseUri.AbsoluteUri.TrimEnd('/') + '/get_login_info') -Headers $Headers -ContentType 'application/json' -Body '{}' -TimeoutSec 10
    } catch {
        $failure = $_.Exception
        $code = $null
        $webStatus = $null
        while ($null -ne $failure) {
            if ($null -ne $failure.Response -and $null -ne $failure.Response.StatusCode) { $code = [int]$failure.Response.StatusCode }
            if ($failure -is [System.Net.WebException]) { $webStatus = $failure.Status }
            $failure = $failure.InnerException
        }
        if ($code -eq 401 -or $code -eq 403) {
            throw 'NAPCAT_AUTH: HTTP authentication failed. QQ_ACCESS_TOKEN must match the NapCat HTTP server Token, not the WebUI Token.'
        }
        if ($code -eq 404) {
            throw 'NAPCAT_ROUTE: get_login_info is missing. QQ_API_BASE_URL must point to the OneBot HTTP server, usually port 3000, not WebUI port 6099.'
        }
        if ($null -ne $code) {
            throw ('NAPCAT_HTTP: HTTP ' + $code + '. Verify QQ login and the NapCat HTTP server; check NapCat logs.')
        }
        if ($webStatus -eq [System.Net.WebExceptionStatus]::Timeout) {
            throw 'NAPCAT_TIMEOUT: HTTP API did not respond within 10 seconds. Check the NapCat process and HTTP server.'
        }
        if ($webStatus -eq [System.Net.WebExceptionStatus]::ConnectFailure -or $webStatus -eq [System.Net.WebExceptionStatus]::NameResolutionFailure) {
            throw 'NAPCAT_UNAVAILABLE: Cannot connect to the configured HTTP host/port. Start NapCat, sign in to QQ and enable its HTTP server.'
        }
        throw 'NAPCAT_REQUEST: HTTP request failed. Check the configured HTTP host/port and the NapCat process.'
    }
    if ($null -eq $login -or $null -eq $login.retcode -or $login.status -notin @('ok','failed')) {
        throw 'NAPCAT_RESPONSE: Response is not a OneBot result. Check QQ_API_BASE_URL and use the HTTP server instead of WebUI.'
    }
    if ($login.retcode -ne 0 -or $login.status -ne 'ok') {
        $retcode = [string]$login.retcode
        if ($retcode -notmatch '^-?[0-9]+$') { $retcode = 'unknown' }
        throw ('NAPCAT_API: OneBot rejected get_login_info; retcode=' + $retcode + '. Confirm QQ is logged in and check NapCat logs.')
    }
    if ([string]$login.data.user_id -notmatch '^[1-9][0-9]*$') {
        throw 'NAPCAT_LOGIN: API returned no valid logged-in QQ identity. Sign in to the bot account inside NapCat.'
    }
    Write-Host 'NAPCAT_OK: HTTP API confirmed a logged-in QQ account. No group message was sent.'
}
