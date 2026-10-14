$allowed=@('QQ_OFFLINE_RELOGIN_REQUIRED','QQ_AUTH_FAILED','QQ_UNAVAILABLE','QQ_API_FAILURE','SOURCE_FETCH_FAILED','POLLING_STALE','POLLING_NOT_STARTED','DELIVERY_PENDING_FAILURE','POLLING_DISABLED','JAVA_UNAVAILABLE','JAVA_RESTART_PENDING','RSSHUB_RESTART_PENDING','STORAGE_NOT_READY','DISK_SPACE_LOW','DISK_CHECK_FAILED','ALL_HEALTHY','OPERATIONS_CONFIG_ERROR')
$code=$env:TCG_ALERT_CODE
if($allowed -notcontains $code) { exit 1 }
$shell=New-Object -ComObject WScript.Shell
$null=$shell.Popup(('TCG bot: '+$code+'. Check logs/operations.log and logs/errors.log. QQ offline may require scanning again.'),10,'TCG bot',48)
