param([string]$Port = "9223", [string]$Expr = "1")
$ErrorActionPreference = "Stop"
$targets = Invoke-RestMethod -Uri ("http://127.0.0.1:" + $Port + "/json/list")
$page = $targets | Where-Object { $_.type -eq "page" -and $_.title -like "*Mocent*" } | Select-Object -First 1
if (-not $page) { Write-Output "NO TARGET"; exit 1 }
$ws = New-Object System.Net.WebSockets.ClientWebSocket
$ct = [System.Threading.CancellationToken]::None
$ws.ConnectAsync([Uri]$page.webSocketDebuggerUrl, $ct).Wait()
$json = $Expr.Replace('"', '\"')
$msg = '{"id":1,"method":"Runtime.evaluate","params":{"expression":"' + $json + '","returnByValue":true}}'
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
Write-Output ([System.Text.Encoding]::UTF8.GetString($ms.ToArray()))
$ws.Dispose()
