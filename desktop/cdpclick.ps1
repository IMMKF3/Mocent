param([string]$Port = "9223", [int]$X = 0, [int]$Y = 0)
$ErrorActionPreference = "Stop"
$targets = Invoke-RestMethod -Uri ("http://127.0.0.1:" + $Port + "/json/list")
$page = $targets | Where-Object { $_.type -eq "page" -and $_.title -like "*Mocent*" } | Select-Object -First 1
if (-not $page) { Write-Output "NO TARGET"; exit 1 }
$ws = New-Object System.Net.WebSockets.ClientWebSocket
$ct = [System.Threading.CancellationToken]::None
$ws.ConnectAsync([Uri]$page.webSocketDebuggerUrl, $ct).Wait()
function Send($obj) {
    $msg = $obj | ConvertTo-Json -Compress -Depth 5
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
}
$id = 1
Send (@{id=$id; method="Input.dispatchMouseEvent"; params=@{type="mouseMoved"; x=$X; y=$Y}}); $id++
Send (@{id=$id; method="Input.dispatchMouseEvent"; params=@{type="mousePressed"; x=$X; y=$Y; clickCount=1; button="left"}}); $id++
Send (@{id=$id; method="Input.dispatchMouseEvent"; params=@{type="mouseReleased"; x=$X; y=$Y; clickCount=1; button="left"}}); $id++
Start-Sleep -Milliseconds 400
$ws.Dispose()
Write-Output ("clicked " + $X + "," + $Y)
