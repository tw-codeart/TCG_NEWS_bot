$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'operations-lib.ps1')
. (Join-Path $PSScriptRoot 'napcat-recovery.ps1')
$state=New-NapCatRecoveryState @{napcatAutoRecover=$true;napcatDirectory=$env:TCG_MANAGED_NAPCAT_DIRECTORY;napcatQuickLoginAccount=$env:TCG_MANAGED_NAPCAT_ACCOUNT} 'napcat'
if($state.code -eq 'CONFIG_INVALID'){throw 'Invalid managed NapCat configuration'}
# Reuse NapCat's saved login ticket; never store passwords or automate challenges.
$env:NAPCAT_QUICK_ACCOUNT=$state.account
foreach($name in @('TWITTER_AUTH_TOKEN','QQ_ACCESS_TOKEN','QQ_COMMAND_SECRET','NAPCAT_QUICK_PASSWORD','NAPCAT_QUICK_PASSWORD_MD5')) {
    Remove-Item -LiteralPath ('Env:'+ $name) -ErrorAction SilentlyContinue
}
Set-Location -LiteralPath $state.directory
try {
    Write-OperationEvent 'NAPCAT_MANAGED_PROCESS_STARTING'
    # Output includes WebUI tokens and QR data; do not copy it into shared bot logs.
    $ErrorActionPreference='Continue';$LASTEXITCODE=1
    & (Join-Path $state.directory 'node.exe') (Join-Path $state.directory 'index.js') '-q' $state.account *> $null
    $result=$LASTEXITCODE;$ErrorActionPreference='Stop'
    Write-OperationEvent ('NAPCAT_MANAGED_EXIT_CODE_'+[int]$result)
    exit $result
}catch{Write-OperationEvent 'NAPCAT_MANAGED_PROCESS_FAILED';exit 1}
