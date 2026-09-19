# CDP screenshot: PS 5.1 ClientWebSocket -> Page.captureScreenshot
param([string]$Port = "9223", [string]$Out = "dist\cdp_shot.png", [string]$TitleLike = "Mocent")
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Web.Extensions

$targets = Invoke-RestMethod -Uri ("http://127.0.0.1:" + $Port + "/json/list")
$page = $targets | Where-Object { $_.type -eq "page" -and $_.title -like "*$TitleLike*" } | Select-Object -First 1
if (-not $page) { Write-Output "NO TARGET"; exit 1 }

$ws = New-Object System.Net.WebSockets.ClientWebSocket
$ct = [System.Threading.CancellationToken]::None
$ws.ConnectAsync([Uri]$page.webSocketDebuggerUrl, $ct).Wait()

$msg = '{"id":1,"method":"Page.captureScreenshot","params":{"format":"png","captureBeyondViewport":false}}'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($msg)
$ws.SendAsync([ArraySegment[byte]]::new($bytes), [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $ct).Wait()

$ms = New-Object System.IO.MemoryStream
$buf = New-Object byte[] (65536)
while ($true) {
    $seg = [ArraySegment[byte]]::new($buf)
    $res = $ws.ReceiveAsync($seg, $ct).Result
    $ms.Write($buf, 0, $res.Count)
    if ($res.EndOfMessage) { break }
}
$resp = [System.Text.Encoding]::UTF8.GetString($ms.ToArray())
$start = $resp.IndexOf('"data":"') + 8
$end = $resp.LastIndexOf('"}')
$b64 = $resp.Substring($start, $end - $start)
[IO.File]::WriteAllBytes((Join-Path (Get-Location) $Out), [Convert]::FromBase64String($b64))
$ws.Dispose()
Write-Output ("saved " + $Out + " " + (Get-Item $Out).Length + " bytes")
