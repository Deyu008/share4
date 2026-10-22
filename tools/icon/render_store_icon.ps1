$ErrorActionPreference = 'Stop'
# Render the store listing icon (512x512) from the same geometry as the
# app's vector adaptive icon (res/drawable/ic_launcher_*.xml is the source of truth).
#   .\tools\icon\render_store_icon.ps1

$here   = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo   = (Resolve-Path (Join-Path $here '..\..')).Path
$design = Join-Path $repo 'design'

New-Item -ItemType Directory -Force -Path $design | Out-Null

$code = Get-Content (Join-Path $here 'IconGraph.cs') -Raw -Encoding UTF8
Add-Type -TypeDefinition $code -ReferencedAssemblies System.Drawing

[IconGraph]::Render((Join-Path $design 'icon-512.png'), 512, 0.22)
Write-Output "wrote $design\icon-512.png"
