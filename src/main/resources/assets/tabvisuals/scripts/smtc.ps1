# TabVisuals SMTC bridge: prints one JSON object per line to stdout.
# state=none  -> no media session
# state=track -> new/changed track (full metadata)
# state=tick  -> position/status update for the current track
# state=error -> transient failure
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

$mgrType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
$propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties,Windows.Media.Control,ContentType=WindowsRuntime]
$streamType = [Windows.Storage.IRandomAccessStreamWithContentType,Windows.Storage,ContentType=WindowsRuntime]

$coverPath = Join-Path $env:TEMP 'tabvisuals_cover.jpg'
$cmdPath = Join-Path $PSScriptRoot 'media_cmd.txt'
$lastCmdStamp = [int64]-1
$lastKey = ''
$lastPosSent = [int64]-1
$status = ''

while ($true) {
    try {
        $mgr = AwaitOp ($mgrType::RequestAsync()) $mgrType
        if (-not $mgr) { Start-Sleep -Milliseconds 2000; continue }
        $session = $mgr.GetCurrentSession()

        # media_cmd.txt: command line + unix-millis stamp written by the HUD media player
        if (Test-Path -LiteralPath $cmdPath) {
            try {
                $raw = Get-Content -LiteralPath $cmdPath -Raw
                $parts = $raw -split "`r?`n"
                $cmd = $parts[0].Trim()
                $stamp = if ($parts.Count -gt 1) { [int64]$parts[1].Trim() } else { [int64]0 }
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
            } catch { }
            Remove-Item -LiteralPath $cmdPath -Force -ErrorAction SilentlyContinue
        }

        if (-not $session) {
            if ($lastKey -ne 'NONE') {
                $lastKey = 'NONE'
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
            $ok = 'false'
            try {
                if ($props.Thumbnail) {
                    $stream = AwaitOp $props.Thumbnail.OpenReadAsync() $streamType
                    if ($stream -and $stream.Size -gt 0) {
                        $net = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
                        $tmp = $coverPath + '.tmp'
                        $fs = [System.IO.File]::Create($tmp)
                        $net.CopyTo($fs)
                        $fs.Flush(); $fs.Close(); $net.Close(); $stream.Close()
                        Move-Item -Force $tmp $coverPath
                        $ok = 'true'
                    }
                }
            } catch { Remove-Item -Force ($coverPath + '.tmp') -ErrorAction SilentlyContinue }
            if ($ok -ne 'true') { Remove-Item -Force $coverPath -ErrorAction SilentlyContinue }

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
            if ([Math]::Abs($posMs - $lastPosSent) -ge 400 -or $st -ne $status) {
                $status = $st
                $lastPosSent = $posMs
                $o = [ordered]@{ state = 'tick'; status = $st; posMs = $posMs; endMs = $endMs }
                ($o | ConvertTo-Json -Compress)
                [Console]::Out.Flush()
            }
        }
    } catch {
        '{"state":"error"}'
        [Console]::Out.Flush()
    }
    Start-Sleep -Milliseconds 500
}
