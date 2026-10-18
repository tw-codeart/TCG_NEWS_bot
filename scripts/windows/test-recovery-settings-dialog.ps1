$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
. (Join-Path $PSScriptRoot 'panel-lib.ps1')
. (Join-Path $PSScriptRoot 'panel-recovery-settings.ps1')
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('TCG-Dialog-Test-'+[Guid]::NewGuid().ToString('N'))
$null=New-Item -ItemType Directory -Path $fixture
function Get-PanelSupervisorState {return 'STOPPED'}
$script:originalNew=(Get-Command New-PanelRecoverySettingsView).ScriptBlock
$script:dialogPath=$fixture
function New-PanelRecoverySettingsView {
    $dialogForTest=& $script:originalNew
    $dialogForTest.form.Opacity=0
    $script:dialogForTest=$dialogForTest
    $dialogForTest.form.Add_Shown({
        $script:dialogForTest.enable.Checked=$true
        $script:dialogForTest.directory.Text=$script:dialogPath
        $script:dialogForTest.account.Text='123456789'
        $null=$script:dialogForTest.form.BeginInvoke([Action]{
            $bitmap=New-Object Drawing.Bitmap($script:dialogForTest.form.Width,$script:dialogForTest.form.Height)
            try{$script:dialogForTest.form.DrawToBitmap($bitmap,(New-Object Drawing.Rectangle(0,0,$bitmap.Width,$bitmap.Height)));$bitmap.Save((Join-Path ([IO.Path]::GetTempPath()) 'tcg-recovery-settings-test-preview.png'),[Drawing.Imaging.ImageFormat]::Png)}finally{$bitmap.Dispose()}
            $script:dialogForTest.save.PerformClick()
        })
    })
    return $dialogForTest
}
$owner=New-Object Windows.Forms.Form;$owner.Opacity=0
$timer=New-Object Windows.Forms.Timer;$timer.Interval=8000
$timer.Add_Tick({$timer.Stop();if($script:dialogForTest){$script:dialogForTest.form.Close()}})
try {
    foreach($name in @('node.exe','index.js')){[IO.File]::WriteAllText((Join-Path $fixture $name),'synthetic placeholder')}
    [IO.File]::WriteAllText((Join-Path $fixture 'config.env'),'QQ_ADAPTER=napcat')
    $timer.Start()
    $result=Show-PanelRecoverySettings $fixture $owner
    if([string]$result -ne 'OK'){throw 'Actual save event failed'}
    $settings=[IO.File]::ReadAllText((Join-Path $fixture 'operations.local.json'))|ConvertFrom-Json
    if(-not $settings.napcatAutoRecover -or $settings.napcatQuickLoginAccount -ne '123456789'){throw 'Saved settings do not match inputs'}
    Write-Host 'PASS: actual modal dialog save event persists chosen Node Shell path/account without real QQ activity.'
}finally{
    $timer.Stop();$timer.Dispose();$owner.Dispose()
    $resolved=[IO.Path]::GetFullPath($fixture)
    if((Split-Path -Parent $resolved) -eq ([IO.Path]::GetTempPath().TrimEnd('\')) -and (Split-Path -Leaf $resolved) -match '^TCG-Dialog-Test-[a-f0-9]{32}$'){Remove-Item -LiteralPath $resolved -Recurse -Force}
}
