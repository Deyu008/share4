$ErrorActionPreference = 'Stop'
# Regenerate the launcher icon assets for the faceted "S" mark.
#   .\tools\icon\build_icon.ps1
#
# Source artwork : tools/icon/s-mark-source.jpg
# Background plate: app/src/main/res/drawable/ic_launcher_bg.xml  (vector, source of truth)
# This script produces the raster layers that cannot be expressed as vectors:
#   - foreground (the S with alpha) per density
#   - monochrome layer (silhouette) per density
#   - design/icon-512.png  store listing image

$here   = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo   = (Resolve-Path (Join-Path $here '..\..')).Path
$src    = Join-Path $here 's-mark-source.jpg'
$work   = Join-Path $env:TEMP 'share4_iconwork'
$res    = Join-Path $repo 'app\src\main\res'
$design = Join-Path $repo 'design'

New-Item -ItemType Directory -Force -Path $work, $design | Out-Null

$code = Get-Content (Join-Path $here 'IconMark.cs') -Raw -Encoding UTF8
Add-Type -TypeDefinition $code -ReferencedAssemblies System.Drawing

# alpha ramp on the max channel so the dark navy facets survive the keying
$lo = 16
$hi = 70
# mark size as a fraction of the 108dp adaptive canvas (safe zone is 66/108)
$markFrac = 0.60

$fgMaster = Join-Path $work 'fg_master.png'
$moMaster = Join-Path $work 'mo_master.png'
$plateMaster = Join-Path $work 'plate_master.png'
Write-Output 'master:'
[IconMark]::ExtractMark($src, $fgMaster, 1024, $markFrac, $lo, $hi, $false, 1)
[IconMark]::ExtractMark($src, $moMaster, 1024, $markFrac, $lo, $hi, $true, 1)
[IconMark]::MakePlate($plateMaster, 1024)

# adaptive canvas = 108dp equivalent pixels
$dpi = [ordered]@{ 'mdpi' = 108; 'hdpi' = 162; 'xhdpi' = 216; 'xxhdpi' = 324; 'xxxhdpi' = 432 }

foreach ($bucket in $dpi.Keys) {
    $dir = Join-Path $res "mipmap-$bucket"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $s = $dpi[$bucket]
    [IconMark]::Resize($fgMaster, (Join-Path $dir 'ic_launcher_fg.png'), $s)
    [IconMark]::Resize($moMaster, (Join-Path $dir 'ic_launcher_mono.png'), $s)
    Write-Output "$bucket adaptive=$s"
}

# store listing 512
[IconMark]::ComposeStore($plateMaster, $fgMaster, (Join-Path $design 'icon-512.png'), 512)
Write-Output '512 done'
