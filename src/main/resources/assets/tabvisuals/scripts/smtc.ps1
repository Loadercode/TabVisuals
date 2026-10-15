# TabVisuals SMTC bridge: prints one JSON object per line to stdout.
# state=none  -> no media session
# state=track -> new/changed track (full metadata)
# state=cover -> artwork for the current track appeared after its track event
# state=tick  -> position/status update for the current track
# state=error -> transient failure
# Arg 1: pid of the game process — the bridge exits when the game dies,
# otherwise an orphaned powershell.exe would outlive a crashed game and
# duplicate every transport command after a restart.
param([int]$ParentPid = 0)
$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTaskMethod = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.IsGenericMethodDefinition -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.GetGenericTypeDefinition().FullName -like 'Windows.Foundation.IAsyncOperation*'
} | Select-Object -First 1

function AwaitOp($op, [type]$resultType) {
    if (-not $op) { return $null }
    try {
        $task = $asTaskMethod.MakeGenericMethod($resultType).Invoke($null, @($op))
        [void]$task.Wait(8000)
        if (-not $task.IsCompleted) { return $null }
        return $task.Result
    } catch { return $null }
}

# Writes the media properties thumbnail to $coverPath, returns 'true'/'false'.
# No member of the WinRT stream RCW is reachable from PowerShell (Size reads as
# $null, Close throws), so the stream converts via reflection and emptiness is
# checked on the copied file instead.
function Save-Cover($p) {
    if (-not $p -or -not $p.Thumbnail) { return 'false' }
    $stream = AwaitOp $p.Thumbnail.OpenReadAsync() $streamType
    if (-not $stream) { return 'false' }
    $tmp = $coverPath + '.tmp'
    try {
        $net = $streamAsNetMethod.Invoke($null, @(, $stream))
        $fs = [System.IO.File]::Create($tmp)
        try { $net.CopyTo($fs) } finally { $fs.Close(); $net.Close() }
        if ((Get-Item -LiteralPath $tmp).Length -gt 0) {
            # -ErrorAction Stop: иначе при отказанном rename (файл открыт игрой)
            # команда «молча» продолжится и вернёт 'true', а под старой обложкой
            # останется картинка предыдущего трека
            Move-Item -Force -ErrorAction Stop -LiteralPath $tmp -Destination $coverPath
            return 'true'
        }
    } catch { }
    Remove-Item -Force -LiteralPath $tmp -ErrorAction SilentlyContinue
    return 'false'
}

$mgrType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
$propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties,Windows.Media.Control,ContentType=WindowsRuntime]
$streamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType,Windows.Storage.Streams,ContentType=WindowsRuntime]
# AsStreamForRead is called through reflection: PowerShell cannot convert the
# WinRT stream RCW to the IInputStream parameter itself (no visible members),
# while the CLR binds it with a plain interface cast (QueryInterface)
$streamAsNetMethod = [System.IO.WindowsRuntimeStreamExtensions].GetMethods() |
    Where-Object { $_.Name -eq 'AsStreamForRead' -and $_.GetParameters().Count -eq 1 } |
    Select-Object -First 1

$coverPath = Join-Path $env:TEMP 'tabvisuals_cover.jpg'
$cmdPath = Join-Path $PSScriptRoot 'media_cmd.txt'
$lastCmdStamp = [int64]-1
$lastKey = ''
$lastPosSent = [int64]-1
$status = ''
$coverPending = 'false'
$coverAttempts = 0
$errStreak = 0
$loopN = 0

while ($true) {
    $loopN++
    try {
        if ($ParentPid -gt 0 -and ($loopN % 10) -eq 0) {
            # игра умерла (краш/kill — родитель детей на Windows не убивает) —
            # уходим сами, иначе после рестарта игры работали бы ДВА моста и
            # каждый клик отрабатывал бы дважды
            if ($null -eq (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) { exit 0 }
        }

        $mgr = AwaitOp ($mgrType::RequestAsync()) $mgrType
        if (-not $mgr) { Start-Sleep -Milliseconds 2000; continue }
        $session = $mgr.GetCurrentSession()

        # media_cmd.txt: command line + unix-millis stamp written by the HUD media player
        if (Test-Path -LiteralPath $cmdPath) {
            $raw = $null
            try { $raw = Get-Content -LiteralPath $cmdPath -Raw } catch { }
            if ($null -eq $raw) {
                Remove-Item -LiteralPath $cmdPath -Force -ErrorAction SilentlyContinue
            } else {
                try {
                    $parts = $raw -split "`r?`n"
                    $cmd = $parts[0].Trim()
                    $stamp = [int64]0
                    if ($parts.Count -gt 1 -and $parts[1].Trim() -match '^\d+$') {
                        $stamp = [int64]$parts[1].Trim()
                    }
                    $age = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - $stamp
                    if ($stamp -gt $lastCmdStamp -and $age -ge 0 -and $age -lt 3000) {
                        $lastCmdStamp = $stamp
                        if ($session) {
                            switch ($cmd) {
                                'playPause' { [void]$session.TryPlayPauseAsync() }
                                'next'      { [void]$session.TrySkipNextAsync() }
                                'prev'      { [void]$session.TrySkipPreviousAsync() }
                            }
                        }
                    }
                    # удаляем только если файл не заменили новой командой за время
                    # обработки — иначе второй клик (next/next за полсекунды)
                    # терялся бы вместе с файлом
                    $raw2 = $null
                    try { $raw2 = Get-Content -LiteralPath $cmdPath -Raw } catch { }
                    if ($null -eq $raw2 -or $raw2 -ceq $raw) {
                        Remove-Item -LiteralPath $cmdPath -Force -ErrorAction SilentlyContinue
                    }
                } catch {
                    Remove-Item -LiteralPath $cmdPath -Force -ErrorAction SilentlyContinue
                }
            }
        }

        if (-not $session) {
            if ($lastKey -ne 'NONE') {
                $lastKey = 'NONE'
                $coverPending = 'false'
                $coverAttempts = 0
                Remove-Item -Force $coverPath -ErrorAction SilentlyContinue
                '{"state":"none"}'
                [Console]::Out.Flush()
            }
            Start-Sleep -Milliseconds 600
            continue
        }

        $props = AwaitOp $session.TryGetMediaPropertiesAsync() $propsType
        if (-not $props) { Start-Sleep -Milliseconds 600; continue }
        $info = $session.GetPlaybackInfo()
        $timeline = $session.GetTimelineProperties()

        $title = "$($props.Title)"
        $artist = "$($props.Artist)"
        $album = "$($props.AlbumTitle)"
        $app = "$($session.SourceAppUserModelId)"
        $st = "$($info.PlaybackStatus)"
        $posMs = [int64]$timeline.Position.TotalMilliseconds
        $endMs = [int64]$timeline.EndTime.TotalMilliseconds
        $key = "$app|$title|$artist|$album"

        if ($key -ne $lastKey) {
            $lastKey = $key
            $coverAttempts = 0
            $coverPending = 'false'
            $ok = Save-Cover $props
            if ($ok -ne 'true') {
                Remove-Item -Force $coverPath -ErrorAction SilentlyContinue
                # some apps publish the thumbnail a moment after the metadata —
                # keep retrying on ticks and announce late art with a cover event
                $coverPending = 'true'
            }

            $o = [ordered]@{
                state = 'track'
                app = $app; title = $title; artist = $artist; album = $album
                status = $st; posMs = $posMs; endMs = $endMs
                cover = $ok
            }
            ($o | ConvertTo-Json -Compress)
            [Console]::Out.Flush()
            $lastPosSent = $posMs
            $status = $st
        } else {
            if ($coverPending -eq 'true' -and $coverAttempts -lt 10) {
                $coverAttempts++
                if ((Save-Cover $props) -eq 'true') {
                    $coverPending = 'false'
                    '{"state":"cover"}'
                    [Console]::Out.Flush()
                }
            }
            if ([Math]::Abs($posMs - $lastPosSent) -ge 400 -or $st -ne $status) {
                $status = $st
                $lastPosSent = $posMs
                $o = [ordered]@{ state = 'tick'; status = $st; posMs = $posMs; endMs = $endMs }
                ($o | ConvertTo-Json -Compress)
                [Console]::Out.Flush()
            }
        }
        $errStreak = 0
    } catch {
        # подряд идущие ошибки не должны шуметь и жечь CPU — сообщаем первые
        # три, дальше растим паузу до 5 секунд
        $errStreak++
        if ($errStreak -le 3) {
            '{"state":"error"}'
            [Console]::Out.Flush()
        }
        Start-Sleep -Milliseconds ([Math]::Min(5000, 500 * $errStreak))
        continue
    }
    Start-Sleep -Milliseconds 500
}
