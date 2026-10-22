$ErrorActionPreference = 'Stop'
# Regenerate the launcher icon set from the AI master artwork.
#   .\tools\icon\build_icon.ps1
# Requires Windows PowerShell + System.Drawing (no extra packages).

$here   = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo   = (Resolve-Path (Join-Path $here '..\..')).Path
$src    = Join-Path $here 'icon-source.jpg'
$work   = Join-Path $env:TEMP 'share4_iconwork'
$res    = Join-Path $repo 'app\src\main\res'
$design = Join-Path $repo 'design'

New-Item -ItemType Directory -Force -Path $work, $design | Out-Null

$code = Get-Content (Join-Path $here 'IconTool.cs') -Raw -Encoding UTF8
Add-Type -TypeDefinition $code -ReferencedAssemblies System.Drawing

# alpha ramp on the 0..255 luminance: below lo = transparent, above hi = opaque
$lo = 45
$hi = 120
# glyph occupies this fraction of the 108dp adaptive canvas (safe zone is 66/108)
$markFrac = 0.60

$fgMaster = Join-Path $work 'fg_master.png'
$bgMaster = Join-Path $work 'bg_master.png'
Write-Output 'master:'
[IconTool]::ExtractMark($src, $fgMaster, 1024, $markFrac, $lo, $hi, $false)
[IconTool]::MakeBackground($bgMaster, 1024)
[IconTool]::ExtractMark($src, (Join-Path $work 'mo_master.png'), 1024, $markFrac, $lo, $hi, $true)
Copy-Item $fgMaster (Join-Path $design 'icon-foreground.png') -Force
Copy-Item $bgMaster (Join-Path $design 'icon-background.png') -Force
Copy-Item (Join-Path $work 'mo_master.png') (Join-Path $design 'icon-monochrome.png') -Force

# adaptive canvas = 108dp equivalent pixels
$dpi = [ordered]@{ 'mdpi' = 108; 'hdpi' = 162; 'xhdpi' = 216; 'xxhdpi' = 324; 'xxxhdpi' = 432 }
# legacy icon = 48dp
$legacy = [ordered]@{ 'mdpi' = 48; 'hdpi' = 72; 'xhdpi' = 96; 'xxhdpi' = 144; 'xxxhdpi' = 192 }

foreach ($bucket in $dpi.Keys) {
    $dir = Join-Path $res "mipmap-$bucket"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $s = $dpi[$bucket]
    $fgt = Join-Path $work "fg_$bucket.png"
    $bgt = Join-Path $work "bg_$bucket.png"
    [IconTool]::Resize($fgMaster, $fgt, $s)
    [IconTool]::MakeBackground($bgt, $s)
    [IconTool]::Resize((Join-Path $work 'mo_master.png'), (Join-Path $work "mo_$bucket.png"), $s)
    Copy-Item $fgt (Join-Path $dir 'ic_launcher_fg.png') -Force
    Copy-Item $bgt (Join-Path $dir 'ic_launcher_bg.png') -Force
    Copy-Item (Join-Path $work "mo_$bucket.png") (Join-Path $dir 'ic_launcher_mono.png') -Force

    $ls = $legacy[$bucket]
    [IconTool]::ComposeLegacy($bgt, $fgt, (Join-Path $dir 'ic_launcher.png'), $ls, 0.22, 1.15, $false)
    [IconTool]::ComposeLegacy($bgt, $fgt, (Join-Path $dir 'ic_launcher_round.png'), $ls, 0, 1.15, $true)
    Write-Output "$bucket adaptive=$s legacy=$ls"
}

# store listing 512
[IconTool]::ComposeLegacy($bgMaster, $fgMaster, (Join-Path $design 'icon-512.png'), 512, 0.22, 1.15, $false)
Write-Output '512 done'
