. (Join-Path $PSScriptRoot 'common.ps1')
if($env:QQ_OCR_ENABLED -eq 'false') { Write-Host 'OCR_DISABLED: images still send normally.';exit }
if($env:QQ_OCR_PROVIDER -eq 'tesseract') {
    if([string]::IsNullOrWhiteSpace($env:TESSERACT_EXECUTABLE) -or -not [IO.Path]::IsPathRooted($env:TESSERACT_EXECUTABLE) -or -not (Test-Path -LiteralPath $env:TESSERACT_EXECUTABLE -PathType Leaf)) { throw 'OCR_CONFIG: set the absolute TESSERACT_EXECUTABLE path.' }
    if([string]::IsNullOrWhiteSpace($env:TESSERACT_DATA_DIRECTORY) -or -not [IO.Path]::IsPathRooted($env:TESSERACT_DATA_DIRECTORY) -or -not (Test-Path -LiteralPath (Join-Path $env:TESSERACT_DATA_DIRECTORY 'jpn.traineddata') -PathType Leaf)) { throw 'OCR_CONFIG: install Japanese jpn.traineddata and set TESSERACT_DATA_DIRECTORY.' }
    & $env:TESSERACT_EXECUTABLE '--tessdata-dir' $env:TESSERACT_DATA_DIRECTORY '--list-langs'
    if($LASTEXITCODE -ne 0) { throw 'OCR_CONFIG: Tesseract language check failed.' }
} else { Write-Host 'OCR_PROVIDER: napcat. Its Japanese language support/accuracy must be verified on this computer.' }
try {
    $status=Invoke-RestMethod 'http://127.0.0.1:8080/api/status' -TimeoutSec 5
    [ordered]@{ocrEnabled=$status.ocrEnabled;ocrProvider=$status.ocrProvider;lastAttempt=$status.ocr} | ConvertTo-Json -Depth 4
} catch { Write-Host 'JAVA_UNAVAILABLE: start the bot to view runtime OCR results.' }
Write-Host 'This check performs no scraping, image download or QQ sending.'
