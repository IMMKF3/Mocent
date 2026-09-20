Add-Type -TypeDefinition @'
using System;using System.Runtime.InteropServices;
using System.Text;
public class W43 {
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr l);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] public static extern int GetWindowTextW(IntPtr h, StringBuilder sb, int max);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  public struct RECT { public int L; public int T; public int R; public int B; }
  public static System.Collections.Generic.List<string> Floats(uint target) {
    var res = new System.Collections.Generic.List<string>();
    EnumWindows(delegate(IntPtr h, IntPtr l) {
      uint pid; GetWindowThreadProcessId(h, out pid);
      if (pid == target && IsWindowVisible(h)) {
        StringBuilder sb = new StringBuilder(128); GetWindowTextW(h, sb, 128);
        RECT r; GetWindowRect(h, out r);
        res.Add("[" + sb.ToString() + "] " + (r.R-r.L) + "x" + (r.B-r.T) + " @ " + r.L + "," + r.T);
      }
      return true;
    }, IntPtr.Zero);
    return res;
  }
}
'@
[W43]::SetProcessDPIAware() | Out-Null
$p = Get-Process Mocent | Select-Object -First 1
Write-Output ('main visible: ' + [W43]::IsWindowVisible($p.MainWindowHandle))
[W43]::Floats([uint32]$p.Id) | ForEach-Object { Write-Output $_ }
