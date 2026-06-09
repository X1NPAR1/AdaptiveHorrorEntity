<#
.SYNOPSIS
  Converts the raw media in the repo root into Minecraft-compatible formats and places them in the
  common module's resource tree.

.DESCRIPTION
  Minecraft only decodes Ogg Vorbis audio (.ogg) and PNG textures (.png). The source media is .mp3 /
  .jpg / .jfif, which the engine cannot load directly. This script uses ffmpeg to convert them.

  Requires ffmpeg on PATH (https://ffmpeg.org/). Run once after adding/replacing source media:
      ./tools/convert-assets.ps1
#>

$ErrorActionPreference = "Stop"
$root      = Split-Path -Parent $PSScriptRoot
$assets    = Join-Path $root "common/src/main/resources/assets/adaptivehorror"
$soundsOut = Join-Path $assets "sounds"
$guiOut    = Join-Path $assets "textures/gui/jumpscare"

New-Item -ItemType Directory -Force -Path $soundsOut, $guiOut | Out-Null

if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
    throw "ffmpeg not found on PATH. Install it from https://ffmpeg.org/ and re-run."
}

# audio source (root) -> registered sound name (sounds.json / ModSounds)
$audioMap = @{
    "iseeyousoundeffect.mp3" = "iseeyou"
    "scarysounds.mp3"        = "scary_ambient"
    "jumpscaresound1.mp3"    = "jumpscare1"
    "jumpscaresound2.mp3"    = "jumpscare2"
    "jumpscaresound3.mp3"    = "jumpscare3"
    "jumpscaresound4.mp3"    = "jumpscare4"
    "120blocksound1.mp3"     = "travel1"
    "120blocksound2.mp3"     = "travel2"
    # Add 120blocksound3/4/5.mp3 here (-> travel3/4/5) and matching entries in sounds.json +
    # ModSounds.TRAVEL when you supply those files.
}

foreach ($src in $audioMap.Keys) {
    $in = Join-Path $root $src
    if (-not (Test-Path $in)) { Write-Warning "missing audio: $src (skipped)"; continue }
    $out = Join-Path $soundsOut ($audioMap[$src] + ".ogg")
    Write-Host "audio  $src -> $($audioMap[$src]).ogg"
    & ffmpeg -y -loglevel error -i $in -c:a libvorbis -q:a 5 $out
}

# image source (root) -> gui texture name. PNGs are re-encoded to guarantee a valid RGBA PNG.
$imageMap = @{
    "jumpscare1.png"   = "jumpscare1"
    "jumpscare2.jpg"   = "jumpscare2"
    "jumpscare3.jpg"   = "jumpscare3"
    "jumpscare4.jpg"   = "jumpscare4"
    "jumpscare5.jfif"  = "jumpscare5"
    "jumpscare6.jfif"  = "jumpscare6"
    "jumpscare7.jfif"  = "jumpscare7"
    "jumpscare8.jfif"  = "jumpscare8"
    "jumpscare120.jpg" = "jumpscare120"
}

foreach ($src in $imageMap.Keys) {
    $in = Join-Path $root $src
    if (-not (Test-Path $in)) { Write-Warning "missing image: $src (skipped)"; continue }
    $out = Join-Path $guiOut ($imageMap[$src] + ".png")
    Write-Host "image  $src -> $($imageMap[$src]).png (1024x1024)"
    # Normalise every jumpscare to a fixed 1024x1024 so the full-screen blit is deterministic
    # (the GUI blit samples a fixed texture size; mismatched sizes would crop the image).
    & ffmpeg -y -loglevel error -i $in -vf "scale=1024:1024" $out
}

# Optional: if the user drops their own stalker skins in the repo root, re-encode them into the
# entity texture folder (64x64 player-skin layout expected).
$entityOut = Join-Path $assets "textures/entity"
New-Item -ItemType Directory -Force -Path $entityOut | Out-Null
foreach ($name in @("stalker_white","stalker_black")) {
    $src = Get-ChildItem -Path $root -Filter "$name.*" -File -ErrorAction SilentlyContinue |
           Where-Object { $_.Extension -match '\.(png|jpg|jfif)$' } | Select-Object -First 1
    if ($src) {
        $out = Join-Path $entityOut "$name.png"
        Write-Host "skin   $($src.Name) -> $name.png"
        & ffmpeg -y -loglevel error -i $src.FullName $out
    }
}

Write-Host "Done. Converted assets written under $assets"
