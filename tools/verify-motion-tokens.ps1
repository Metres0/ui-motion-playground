# Motion token drift verification (v1.32)
# motion-tokens.md is the single source of truth; verifies the translations
# in both Android MotionTokens.kt files and the Web styles.css match it.
# ASCII-only on purpose: PowerShell 5.1 reads .ps1 as ANSI, UTF-8 without BOM
# would garble non-ASCII strings and break parsing.
#
# Usage: powershell -ExecutionPolicy Bypass -File tools\verify-motion-tokens.ps1
# Wired into android-rss via: gradlew verifyMotionTokens
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$md = Get-Content -Raw (Join-Path $root 'motion-tokens.md')
$rssKt = Get-Content -Raw (Join-Path $root 'android-rss\app\src\main\java\com\example\feedlite\MotionTokens.kt')
$composeKt = Get-Content -Raw (Join-Path $root 'android-compose\app\src\main\java\com\example\uiplayground\MotionTokens.kt')
$css = Get-Content -Raw (Join-Path $root 'web-view-transitions\styles.css')

$errors = New-Object System.Collections.Generic.List[string]

# 1) Durations: md table -> both Kotlin files + CSS
foreach ($m in [regex]::Matches($md, '\| `duration\.(\w+)` \| (\d+) ms \|')) {
    $name = $m.Groups[1].Value
    $val = $m.Groups[2].Value
    $ktName = $name.Substring(0, 1).ToUpper() + $name.Substring(1)
    foreach ($pair in @(@('rss', $rssKt), @('compose', $composeKt))) {
        $pattern = "const val $ktName = $val"
        if ($pair[1] -notmatch [regex]::Escape($pattern)) {
            $errors.Add("duration.$name = $val ms missing in $($pair[0]) MotionTokens.kt")
        }
    }
    $cssPattern = "--dur-$name" + ": $val" + "ms"
    if ($css -notmatch [regex]::Escape($cssPattern)) {
        $errors.Add("duration.$name = $val ms missing in styles.css (--dur-$name)")
    }
}

# 2) Easing: CSS variables + Compose curves must match md
$easeWeb = @{
    'emphasized' = 'cubic-bezier(0.2, 0, 0, 1)'
    'standard'   = 'cubic-bezier(0.4, 0, 0.2, 1)'
    'decelerate' = 'cubic-bezier(0, 0, 0.2, 1)'
    'accelerate' = 'cubic-bezier(0.4, 0, 1, 1)'
    'spring'     = 'cubic-bezier(0.34, 1.56, 0.64, 1)'
}
foreach ($k in $easeWeb.Keys) {
    $pattern = "--ease-$k" + ": " + $easeWeb[$k]
    if ($css -notmatch [regex]::Escape($pattern)) {
        $errors.Add("$pattern missing in styles.css")
    }
}
foreach ($pair in @(@('rss', $rssKt), @('compose', $composeKt))) {
    if ($pair[1] -notmatch 'CubicBezierEasing\(0\.2f, 0f, 0f, 1f\)') {
        $errors.Add("CubicBezierEasing(0.2f, 0f, 0f, 1f) missing in $($pair[0]) MotionTokens.kt")
    }
    if ($pair[1] -notmatch 'dampingRatio = 0\.8f' -or $pair[1] -notmatch 'StiffnessMedium') {
        $errors.Add("spring(0.8, 400) missing in $($pair[0]) MotionTokens.kt (expect dampingRatio=0.8f + StiffnessMedium=400)")
    }
}

# 3) Distances
$space = @{ 'micro' = 4; 'small' = 8; 'page' = 56 }
foreach ($s in $space.Keys) {
    $ktName = $s.Substring(0, 1).ToUpper() + $s.Substring(1)
    foreach ($pair in @(@('rss', $rssKt), @('compose', $composeKt))) {
        $pattern = "const val $ktName = $($space[$s])"
        if ($pair[1] -notmatch [regex]::Escape($pattern)) {
            $errors.Add("space.$s = $($space[$s]) missing in $($pair[0]) MotionTokens.kt")
        }
    }
    $cssPattern = "--space-$s" + ": $($space[$s])" + "px"
    if ($css -notmatch [regex]::Escape($cssPattern)) {
        $errors.Add("--space-$s = $($space[$s])px missing in styles.css")
    }
}

if ($errors.Count -gt 0) {
    Write-Output 'FAIL: motion token drift detected:'
    $errors | ForEach-Object { Write-Output "  - $_" }
    exit 1
}
Write-Output 'OK: motion tokens consistent (motion-tokens.md <-> MotionTokens.kt x2 <-> styles.css)'
