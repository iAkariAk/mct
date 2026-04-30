$ext = Get-Content "D:/ModProjects/mct/extractions_d.json" -Raw | ConvertFrom-Json
$rep = Get-Content "D:/ModProjects/mct/replacements_d.json" -Raw | ConvertFrom-Json

Write-Host "=== 基本统计 ==="
$extTotal = 0; $ext | % { $extTotal += $_.extractions.Count }
$repTotal = 0; $rep | % { $repTotal += $_.replacements.Count }
Write-Host "提取组: $($ext.Count)  替换组: $($rep.Count)"
Write-Host "提取条: $extTotal  替换条: $repTotal  差: $($extTotal - $repTotal)"

Write-Host "`n=== 替换缺失详情（前10个） ==="
$missing = 0
for ($i = 0; $i -lt [Math]::Min($ext.Count, $rep.Count); $i++) {
    $eg = $ext[$i]
    $rg = $rep[$i]
    $gap = $eg.extractions.Count - $rg.replacements.Count
    if ($gap -gt 0) {
        $missing += $gap
        if ($missing -le 10) {
            Write-Host "  文件: $($eg.path)  缺 $gap 条"
            for ($j = 0; $j -lt $eg.extractions.Count; $j++) {
                $e = $eg.extractions[$j]
                if ($j -ge $rg.replacements.Count) {
                    Write-Host "    缺失[$j]: [$($e.content.Substring(0,[Math]::Min(60,$e.content.Length)))]"
                }
            }
        }
    }
}
Write-Host "总缺失: $missing"

Write-Host "`n=== MCJson 替换是否破坏JSON ==="
$bad = 0
foreach ($g in $rep) {
    foreach ($r in $g.replacements) {
        if ($r.type -ne "MCJson") { continue }
        if ($r.replacement -match '^\s*\{' -or $r.replacement -match '^\s*\[') {
            try { $null = $r.replacement | ConvertFrom-Json -ErrorAction Stop }
            catch {
                if ($bad -lt 10) {
                    Write-Host "  JSON损坏: $($g.path)"
                    Write-Host "    内容: $($r.replacement.Substring(0,[Math]::Min(100,$r.replacement.Length)))"
                }
                $bad++
            }
        }
    }
}
Write-Host "JSON损坏数: $bad"

Write-Host "`n=== MCFunction 替换文本中的换行符检查 ==="
$newline = 0
foreach ($g in $rep) {
    foreach ($r in $g.replacements) {
        if ($r.type -ne "MCFunction") { continue }
        if ($r.replacement -match "`n" -or $r.replacement -match "`r") {
            $newline++
            if ($newline -le 5) {
                Write-Host "  文件: $($g.path)"
                Write-Host "    含真实换行: [$($r.replacement.Substring(0,[Math]::Min(80,$r.replacement.Length)))]"
            }
        }
    }
}
Write-Host "含真实换行符: $newline"

Write-Host "`n=== 译文与原文长度差异过大的检查 ==="
$lenDiff = 0
for ($i = 0; $i -lt [Math]::Min($ext.Count, $rep.Count); $i++) {
    $eg = $ext[$i]
    $rg = $rep[$i]
    for ($j = 0; $j -lt [Math]::Min($eg.extractions.Count, $rg.replacements.Count); $j++) {
        $oe = $eg.extractions[$j]
        $re = $rg.replacements[$j]
        $diff = [Math]::Abs($oe.content.Length - $re.replacement.Length)
        if ($diff -gt 100) {
            $lenDiff++
            if ($lenDiff -le 5) {
                Write-Host "  文件: $($eg.path)"
                Write-Host "    原文($($oe.content.Length)): $($oe.content.Substring(0,[Math]::Min(60,$oe.content.Length)))..."
                Write-Host "    译文($($re.replacement.Length)): $($re.replacement.Substring(0,[Math]::Min(60,$re.replacement.Length)))..."
            }
        }
    }
}
Write-Host "长度差异>100: $lenDiff"

Write-Host "`n=== 翻译样例（翻过的） ==="
$n = 0
for ($i = 0; $i -lt [Math]::Min($ext.Count, $rep.Count); $i++) {
    $eg = $ext[$i]
    $rg = $rep[$i]
    for ($j = 0; $j -lt [Math]::Min($eg.extractions.Count, $rg.replacements.Count); $j++) {
        $o = $eg.extractions[$j].content
        $t = $rg.replacements[$j].replacement
        if ($o -ne $t -and $o.Length -gt 3 -and $n -lt 20) {
            $n++
            Write-Host ""
            Write-Host "  EN: $($o.Substring(0,[Math]::Min(80,$o.Length)))"
            Write-Host "  ZH: $($t.Substring(0,[Math]::Min(80,$t.Length)))"
        }
    }
}
