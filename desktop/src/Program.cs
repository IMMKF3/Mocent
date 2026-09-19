// 摸薪 Mocent 桌面壳（Windows，WebView2）
// 无边框窗口：网页顶栏即标题栏（-webkit-app-region: drag 原生拖动/双击最大化），
// 右上角三个自绘窗口按钮通过 postMessage 与本壳通信。
// 纯 .NET Framework 4.x + 官方 WebView2 SDK，零第三方依赖；页面经虚拟域名 mocent.local 直接映射本地 web 目录。
//
// C#5 语法约束：系统 csc.exe（.NET 4.0 编译器）不支持字符串插值、?. 、out var 等新语法。

using System;
using System.Drawing;
using System.IO;
using System.Globalization;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

internal static class Program
{
    public const string APP_VERSION = "1.3.0";
    const string APP_NAME = "摸薪 Mocent";
    const string MUTEX_NAME = "Local\\Mocent.SingleInstance";
    const string SHOW_EVENT_NAME = "Local\\Mocent.OpenWindow";
    public const string VIRTUAL_HOST = "mocent.local";

    static ApplicationContext appContext;
    static int openWindows;
    static MainForm latest;   // 用于把“再开一窗”的信号封送回 UI 线程

    [STAThread]
    static void Main()
    {
        bool createdNew;
        using (Mutex mutex = new Mutex(true, MUTEX_NAME, out createdNew))
        using (EventWaitHandle showEvent = new EventWaitHandle(false, EventResetMode.AutoReset, SHOW_EVENT_NAME))
        {
            if (!createdNew)
            {
                // 已有实例：通知它再开一个窗口
                showEvent.Set();
                return;
            }

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.ThreadException += delegate(object s, System.Threading.ThreadExceptionEventArgs e)
            {
                LogCrash(e.Exception);
            };
            AppDomain.CurrentDomain.UnhandledException += delegate(object s, UnhandledExceptionEventArgs e)
            {
                LogCrash(e.ExceptionObject as Exception);
            };

            // IPC：后台线程等信号，通过最新窗体 BeginInvoke 封送回 UI 线程
            Thread ipc = new Thread(delegate()
            {
                while (showEvent.WaitOne())
                {
                    MainForm target = latest;
                    if (target != null && target.IsHandleCreated)
                        target.BeginInvoke((Action)delegate { OpenWindow(); });
                }
            });
            ipc.IsBackground = true;
            ipc.Start();

            appContext = new ApplicationContext();
            OpenWindow();
            Application.Run(appContext);
        }
    }

    static void OpenWindow()
    {
        MainForm f = new MainForm();
        f.FormClosed += delegate
        {
            openWindows--;
            if (openWindows == 0) appContext.ExitThread();
        };
        openWindows++;
        latest = f;
        f.Show();
    }

    static void LogCrash(Exception ex)
    {
        try
        {
            string la = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string dir = Path.Combine(la, "Mocent");
            Directory.CreateDirectory(dir);
            File.AppendAllText(Path.Combine(dir, "crash.log"),
                DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + " " + ex + "\r\n");
        }
        catch { }
    }
}

public class MainForm : Form
{
    static readonly CoreWebView2Environment[] envHolder = new CoreWebView2Environment[1];

    WebView2 web;
    bool loaded;

    const int WM_NCCALCSIZE = 0x0083;
    const int WM_NCHITTEST = 0x0084;
    const int HTCLIENT = 1, HTLEFT = 10, HTRIGHT = 11, HTTOP = 12,
              HTTOPLEFT = 13, HTTOPRIGHT = 14, HTBOTTOM = 15,
              HTBOTTOMLEFT = 16, HTBOTTOMRIGHT = 17;
    const int RESIZE_BORDER = 8;

    public MainForm()
    {
        Text = "摸薪 Mocent";
        StartPosition = FormStartPosition.Manual;
        FormBorderStyle = FormBorderStyle.None;
        BackColor = Color.FromArgb(0xFA, 0xF5, 0xEC);
        Font = new Font("Microsoft YaHei UI", 9F);
        MinimumSize = new Size(400, 600);
        Icon = ExtractIcon();
        RestoreBounds_();

        web = new WebView2();
        web.Dock = DockStyle.Fill;
        Controls.Add(web);

        Load += delegate { Init_(); };
        FormClosing += delegate { SaveBounds_(); };
    }

    /* ---------- 窗口框架：无边框但可拖拽、可调大小、支持贴靠 ---------- */

    protected override CreateParams CreateParams
    {
        get
        {
            CreateParams cp = base.CreateParams;
            cp.Style |= 0x00040000  // WS_THICKFRAME：可调大小 + 系统阴影
                      | 0x00020000  // WS_MINIMIZEBOX
                      | 0x00080000; // WS_SYSMENU：Alt+Space 系统菜单、任务栏右键（无最大化）
            return cp;
        }
    }

    protected override void WndProc(ref Message m)
    {
        if (m.Msg == WM_NCCALCSIZE)
        {
            // 保留 WS_THICKFRAME 但把非客户区归零 → 客户区铺满整个窗口（无最大化，无需内缩补偿）
            m.Result = IntPtr.Zero;
            return;
        }
        if (m.Msg == WM_NCHITTEST && WindowState != FormWindowState.Maximized)
        {
            // 8px 边缘热区 → 系统调整大小（顶栏拖动由网页 app-region 提供，不在此处理）
            int x = (short)((long)m.LParam & 0xFFFF);
            int y = (short)(((long)m.LParam >> 16) & 0xFFFF);
            Point p = PointToClient(new Point(x, y));
            Size s = ClientSize;
            int ht = HTCLIENT;
            if (p.X <= RESIZE_BORDER && p.Y <= RESIZE_BORDER) ht = HTTOPLEFT;
            else if (p.X >= s.Width - RESIZE_BORDER && p.Y <= RESIZE_BORDER) ht = HTTOPRIGHT;
            else if (p.X <= RESIZE_BORDER && p.Y >= s.Height - RESIZE_BORDER) ht = HTBOTTOMLEFT;
            else if (p.X >= s.Width - RESIZE_BORDER && p.Y >= s.Height - RESIZE_BORDER) ht = HTBOTTOMRIGHT;
            else if (p.X <= RESIZE_BORDER) ht = HTLEFT;
            else if (p.X >= s.Width - RESIZE_BORDER) ht = HTRIGHT;
            else if (p.Y <= RESIZE_BORDER) ht = HTTOP;
            else if (p.Y >= s.Height - RESIZE_BORDER) ht = HTBOTTOM;
            if (ht != HTCLIENT) { m.Result = (IntPtr)ht; return; }
        }
        base.WndProc(ref m);
    }

    /* ---------- WebView2 ---------- */

    async void Init_()
    {
        try
        {
            if (envHolder[0] == null)
            {
                string udf = Path.Combine(DataRoot(), "WebView2");
                // 可选调试端口：设置 MOCENT_CDP=9223 时开启远程调试（日常使用不设置）
                string cdp = Environment.GetEnvironmentVariable("MOCENT_CDP");
                if (!string.IsNullOrEmpty(cdp))
                {
                    CoreWebView2EnvironmentOptions opts = new CoreWebView2EnvironmentOptions();
                    opts.AdditionalBrowserArguments = "--remote-debugging-port=" + cdp;
                    envHolder[0] = await CoreWebView2Environment.CreateAsync(null, udf, opts);
                }
                else
                {
                    envHolder[0] = await CoreWebView2Environment.CreateAsync(null, udf);
                }
            }
            await web.EnsureCoreWebView2Async(envHolder[0]);

            string exeDir = Path.GetDirectoryName(Application.ExecutablePath);
            string webDir = Path.Combine(exeDir, "web");
            if (!Directory.Exists(webDir) && File.Exists(Path.Combine(exeDir, "index.html")))
            {
                webDir = exeDir;   // 兼容平铺布局（web 文件与 exe 同目录）
            }
            if (!Directory.Exists(webDir))
            {
                throw new DirectoryNotFoundException(
                    "未找到资源文件夹 web（应在 " + webDir + "）。便携版请完整解压整个 Mocent 文件夹后再运行，不要单独拷贝 exe。");
            }

            CoreWebView2 cv = web.CoreWebView2;
            cv.Settings.IsNonClientRegionSupportEnabled = true;   // 顶栏 app-region:drag 原生拖动
            cv.Settings.AreDefaultContextMenusEnabled = false;
            cv.Settings.AreDevToolsEnabled = false;
            cv.Settings.IsZoomControlEnabled = false;
            cv.Settings.IsStatusBarEnabled = false;
            cv.AddScriptToExecuteOnDocumentCreatedAsync(
                "document.documentElement.classList.add('app-shell');");
            cv.WebMessageReceived += OnWebMessage;

            cv.SetVirtualHostNameToFolderMapping(Program.VIRTUAL_HOST, webDir,
                CoreWebView2HostResourceAccessKind.Allow);
            web.Source = new Uri("http://" + Program.VIRTUAL_HOST + "/index.html");
            loaded = true;
        }
        catch (Exception ex)
        {
            MessageBox.Show(this,
                "摸薪 Mocent 需要 Microsoft Edge WebView2 运行时（Windows 10/11 一般自带）。\r\n\r\n" +
                "未能初始化：" + ex.Message + "\r\n\r\n" +
                "可到 https://developer.microsoft.com/microsoft-edge/webview2/ 下载安装。",
                "摸薪 Mocent", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            Close();
        }
    }

    void OnWebMessage(object sender, CoreWebView2WebMessageReceivedEventArgs e)
    {
        string msg = e.TryGetWebMessageAsString();
        if (msg == "min") WindowState = FormWindowState.Minimized;
        else if (msg == "close") Close();
    }

    /* ---------- 路径与窗口位置记忆 ---------- */

    string AppRoot()
    {
        return Path.GetDirectoryName(Application.ExecutablePath);
    }

    static string dataRootCache;
    string DataRoot()
    {
        if (dataRootCache != null) return dataRootCache;
        string la = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        dataRootCache = Path.Combine(la, "Mocent");
        try { Directory.CreateDirectory(dataRootCache); } catch { }
        return dataRootCache;
    }

    void RestoreBounds_()
    {
        try
        {
            string file = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Mocent", "window.json");
            if (!File.Exists(file)) { DefaultPlace(); return; }
            string json = File.ReadAllText(file, Encoding.UTF8);
            int x = Num_(json, "x"), y = Num_(json, "y"), w = Num_(json, "w"), h = Num_(json, "h");
            Rectangle wa = Screen.PrimaryScreen.WorkingArea;
            if (w >= MinimumSize.Width && h >= MinimumSize.Height)
            {
                Location = new Point(
                    Math.Max(wa.Left - w / 2, Math.Min(x, wa.Right - 60)),
                    Math.Max(wa.Top, Math.Min(y, wa.Bottom - 60)));
                Size = new Size(w, h);
            }
            else DefaultPlace();
        }
        catch { DefaultPlace(); }
    }

    void DefaultPlace()
    {
        Rectangle wa = Screen.PrimaryScreen.WorkingArea;
        Size = new Size(Math.Min(540, wa.Width), Math.Min(920, wa.Height));
        Location = new Point(wa.Left + (wa.Width - Size.Width) / 2, wa.Top + Math.Max(0, (wa.Height - Size.Height) / 3));
    }

    static int Num_(string json, string key)
    {
        Match m = Regex.Match(json, "\"" + key + "\"\\s*:\\s*(-?\\d+)");
        int v;
        if (m.Success && int.TryParse(m.Groups[1].Value, NumberStyles.Integer, CultureInfo.InvariantCulture, out v)) return v;
        return 0;
    }

    void SaveBounds_()
    {
        if (IsDisposed || !loaded) return;
        try
        {
            if (WindowState != FormWindowState.Normal) return;
            Rectangle r = Bounds;
            StringBuilder sb = new StringBuilder();
            sb.Append("{\"x\":").Append(r.X).Append(",\"y\":").Append(r.Y)
              .Append(",\"w\":").Append(r.Width).Append(",\"h\":").Append(r.Height).Append("}");
            File.WriteAllText(Path.Combine(DataRoot(), "window.json"), sb.ToString(), Encoding.UTF8);
        }
        catch { }
    }

    Icon ExtractIcon()
    {
        try { return Icon.ExtractAssociatedIcon(Application.ExecutablePath); }
        catch { return SystemIcons.Application; }
    }

    [DllImport("user32.dll")]
    static extern int GetSystemMetrics(int index);

    [StructLayout(LayoutKind.Sequential)]
    struct RECT { public int Left, Top, Right, Bottom; }
}
