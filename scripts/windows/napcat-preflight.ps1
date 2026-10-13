# Legacy function retained for existing local scripts.
. (Join-Path $PSScriptRoot 'onebot-preflight.ps1')
function Confirm-NapCatLogin {
    param([Uri]$BaseUri,[hashtable]$Headers)
    Confirm-OneBotLogin -BaseUri $BaseUri -Headers $Headers
}
