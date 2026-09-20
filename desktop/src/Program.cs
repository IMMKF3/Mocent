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
    public const string APP_VERSION = "1.4.0";
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

            // IPC：后台线程等信号，把已有窗口调到前台（严格单实例：不重复开窗）
            Thread ipc = new Thread(delegate()
            {
                while (showEvent.WaitOne())
                {
                    MainForm target = latest;
                    if (target != null && target.IsHandleCreated)
                        target.BeginInvoke((Action)delegate
                        {
                            MainForm t2 = latest;
                            if (t2 == null || t2.IsDisposed) OpenWindow();
                            else if (!t2.Visible) t2.ShowFromTray();
                            else t2.Forefront_();
                        });
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

    /// <summary>把主窗口唤回前台（浮窗双击 / 托盘双击都走这里，不开新窗口）。</summary>
    public static void Forefront()
    {
        MainForm t = latest;
        if (t == null || t.IsDisposed) { OpenWindow(); return; }
        try
        {
            t.BeginInvoke((Action)delegate
            {
                MainForm t2 = latest;
                if (t2 == null || t2.IsDisposed) return;
                t2.ShowFromTray();
            });
        }
        catch { }
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
    Panel splash;
    PictureBox splashLogo;
    Label splashTitle;
    MiniForm mini;                  // 桌面浮窗（网页 postMessage 控制开关）
    double miniAlpha = 0.92;        // 浮窗不透明度（网页设置先于浮窗创建时也能记住）
    bool miniOn;                    // 网页「显示浮窗」开关：最小化时据此决定收托盘 + 弹浮窗
    bool closeToTray;   // ✕ 行为：true=收进托盘（设置里可选，网页 postMessage 同步）
    bool reallyExit;    // 托盘菜单"退出"置位，绕过托盘拦截
    bool firstTrayHide = true;
    NotifyIcon trayIcon;

    bool loaded;

    // 位置记忆：最小化时 Location 会被系统改写，提前存下正常态坐标供还原时复位。
    // （尺寸不记忆 —— 尺寸膨胀的修法见 CreateParams / WM_WINDOWPOSCHANGED）
    Point lastNormalLoc;
    bool locKnown, wasMinimized, restoringLoc;

    const int WM_NCCALCSIZE = 0x0083;
    const int WM_NCHITTEST = 0x0084;
    const int WM_WINDOWPOSCHANGED = 0x0047;
    const int WM_SYSCOMMAND = 0x0112;
    const int SC_MINIMIZE = 0xF020;
    const int SC_MAXIMIZE = 0xF030;
    const int SC_RESTORE = 0xF120;
    // WM_NCCALCSIZE 的返回值标志
    const int WVR_HREDRAW = 0x0100, WVR_VREDRAW = 0x0200;

    /* ---------- 抑制「边框厚度」估算 ----------
       WinForms 的 Form 有一处内部逻辑（.NET 8 源码 Form.cs:5139 RestoreWindowBoundsIfNecessary），
       在最小化→还原时把「最小化前记下的客户区尺寸」当窗口尺寸，调
       Control.SizeFromClientSizeInternal（Control.cs:11029）里的
       AdjustWindowRectExForControlDpi(ref rect, cp.Style, ...) 重新加上非客户区厚度。

       它读的是**我们 CreateParams 里注入的样式**：因为有 WS_THICKFRAME，
       AdjustWindowRectEx 就按「有边框窗口」补上 2*边框 + 标题栏，于是每还原一次
       窗口就比上一轮大一圈（长宽都变），反复最小化/还原单调膨胀。

       SizeFromClientSizeInternal 是 internal 的，**重写不了**；唯一能干预的就是
       在它读 CreateParams 的那一瞬间，把会参与边框计算的样式临时摘掉。
       窗口自身真实的样式位不受影响 —— CreateParams 只是个 getter，正常时机返回原样式。 */
    bool suppressBorderMetrics;
    const int WS_BORDER_ = 0x00800000, WS_DLGFRAME_ = 0x00400000,
              WS_CAPTION_ = 0x00C00000, WS_THICKFRAME_ = 0x00040000;
    const int BORDER_METRIC_STYLES = WS_BORDER_ | WS_DLGFRAME_ | WS_CAPTION_ | WS_THICKFRAME_;

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

        // 系统托盘图标（设置选"最小化到托盘"后显示，双击唤回）
        ContextMenuStrip trayMenu = new ContextMenuStrip();
        trayMenu.Items.Add("打开", null, delegate { ShowFromTray(); });
        trayMenu.Items.Add("退出", null, delegate { reallyExit = true; Close(); });
        trayIcon = new NotifyIcon {
            Icon = ExtractIcon(), Text = "摸薪 Mocent", Visible = false,
            ContextMenuStrip = trayMenu
        };
        trayIcon.DoubleClick += delegate { ShowFromTray(); };

        // 启动页：WebView2 初始化/页面加载期间的白屏遮挡
        splash = new Panel { Dock = DockStyle.Fill, BackColor = Color.FromArgb(0xFA, 0xF5, 0xEC) };
        splashLogo = new PictureBox { Size = new Size(64, 64), SizeMode = PictureBoxSizeMode.Zoom };
        string logoPath = Path.Combine(Path.GetDirectoryName(Application.ExecutablePath), "web", "icon-rounded.png");
        if (!File.Exists(logoPath))
            logoPath = Path.Combine(Path.GetDirectoryName(Application.ExecutablePath), "web", "icon-192.png");
        if (File.Exists(logoPath)) splashLogo.Image = Image.FromFile(logoPath);
        else splashLogo.Visible = false;
        splashTitle = new Label { Text = "摸薪 Mocent", AutoSize = true,
            Font = new Font("Microsoft YaHei UI", 16.5F, FontStyle.Bold),
            ForeColor = Color.FromArgb(0x43, 0x35, 0x2A) };
        splash.Controls.Add(splashLogo);
        splash.Controls.Add(splashTitle);
        splash.Resize += delegate { LayoutSplash(); };
        Controls.Add(splash);
        splash.BringToFront();

        Load += delegate { LayoutSplash(); Init_(); };
        // 无边框窗（WM_NCCALCSIZE 归零）最小化再还原时，系统会按自带的边框厚度
        // 偏移一次窗口位置 —— 记住正常态位置，还原后强制复位。
        Move += delegate { RememberLoc_(); };
        Resize += delegate { OnResized_(); };
        FormClosing += delegate(object s2, FormClosingEventArgs e2)
        {
            SaveBounds_();
            // 托盘模式：✕/任务栏/Alt+F4 都收进托盘；仅系统关机和托盘菜单"退出"真关闭
            if (closeToTray && !reallyExit && e2.CloseReason != CloseReason.WindowsShutDown)
            {
                e2.Cancel = true;
                HideToTrayWithTip();
            }
        };
        FormClosed += delegate
        {
            trayIcon.Visible = false; trayIcon.Dispose();
            if (mini != null && !mini.IsDisposed) mini.Close();
        };
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
            // WinForms 在最小化→还原时用 AdjustWindowRectEx 反算尺寸（见 suppressBorderMetrics 声明处）。
            // 摘掉边框类样式位后，AdjustWindowRectEx 算出的非客户区为 0，尺寸不再被撑大。
            if (suppressBorderMetrics) cp.Style &= ~BORDER_METRIC_STYLES;
            return cp;
        }
    }

    /* ---------- 临时诊断：窗口尺寸变化日志 ----------
       排查「最小化→还原窗口变大」时加的。设环境变量 MOCENT_WNDLOG=1 才会写日志，
       日志落在 %LOCALAPPDATA%\Mocent\wnd.log。确认问题解决后可以删掉这一段。 */
    static readonly bool wndLogOn = Environment.GetEnvironmentVariable("MOCENT_WNDLOG") == "1";
    static string WinSize_(Form f)
    {
        IntPtr h = f.Handle;
        RECT r;
        return GetWindowRect(h, out r)
            ? "win=" + (r.Right - r.Left) + "x" + (r.Bottom - r.Top)
            : "win=?";
    }
    static void WLog_(string s)
    {
        if (!wndLogOn) return;
        try
        {
            File.AppendAllText(
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                             "Mocent", "wnd.log"),
                DateTime.Now.ToString("HH:mm:ss.fff") + "  " + s + "\r\n");
        }
        catch { }
    }

    protected override void WndProc(ref Message m)
    {
        if (wndLogOn && (m.Msg == WM_WINDOWPOSCHANGED || m.Msg == WM_NCCALCSIZE ||
                         m.Msg == WM_SYSCOMMAND || m.Msg == 0x0005 /*WM_SIZE*/))
        {
            string pre = WinSize_(this) + " Size=" + Size.Width + "x" + Size.Height
                       + " Client=" + ClientSize.Width + "x" + ClientSize.Height
                       + " State=" + WindowState + " Suppress=" + suppressBorderMetrics;
            WLog_("IN  msg=0x" + m.Msg.ToString("X4") + " " + pre);
        }
        if (m.Msg == WM_WINDOWPOSCHANGED)
        {
            // 关键：WinForms 的 Form.WndProc 在 WM_WINDOWPOSCHANGED 分支里会调
            // WmWindowPosChanged → RestoreWindowBoundsIfNecessary → SizeFromClientSizeInternal
            // → AdjustWindowRectExForControlDpi(CreateParams.Style)，把客户区尺寸反算成窗口尺寸。
            // 必须在 base.WndProc 执行期间摘掉边框样式，否则每还原一次就补一圈边框厚度。
            bool prev = suppressBorderMetrics;
            suppressBorderMetrics = true;
            try { base.WndProc(ref m); }
            finally { suppressBorderMetrics = prev; }
            if (wndLogOn)
                WLog_("OUT msg=0x0047 (suppressed) " + WinSize_(this)
                    + " Size=" + Size.Width + "x" + Size.Height
                    + " Client=" + ClientSize.Width + "x" + ClientSize.Height
                    + " State=" + WindowState);
            return;
        }
        if (m.Msg == WM_NCCALCSIZE)
        {
            // 保留 WS_THICKFRAME 但把非客户区归零 → 客户区铺满整个窗口。
            //
            // 微软文档明确：wParam 为 TRUE 时「直接返回 0 而不处理 NCCALCSIZE_PARAMS 里的矩形，
            // 客户区就会缩放到整个窗口大小，从而移除窗口框架与标题栏」。这正是我们要的效果。
            // 之前这里返回了 WVR_ALIGNTOP|WVR_ALIGNLEFT，那两个标志的语义是
            // 「保留旧客户区内容并对齐到新位置」，跟窗口矩形无关，属于用错。
            //
            // 注：窗口越变越大的根因**不在这里**，而是 WinForms 的
            //     RestoreWindowBoundsIfNecessary + AdjustWindowRectEx（见 CreateParams 注释
            //     与上面 WM_WINDOWPOSCHANGED 的处理）。这里保持最朴素的 return 0 即可。
            m.Result = IntPtr.Zero;
            if (wndLogOn) WLog_("OUT msg=0x0083 (return 0)");
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
        if (m.Msg == WM_SYSCOMMAND)
        {
            int cmd = m.WParam.ToInt32() & 0xFFF0;
            if (cmd == SC_MAXIMIZE) { m.Result = IntPtr.Zero; return; }   // 无最大化

            /* 最小化分流：
               - 勾选了「显示浮窗」→ 收进托盘（任务栏图标消失）+ 弹出浮窗。
                 想恢复就双击浮窗或点托盘图标。
               - 没勾选 → 保持系统原生最小化（图标留在任务栏，点任务栏还原）。
               注意只拦 SC_MINIMIZE（点最小化按钮 / 任务栏右键最小化）。
               不要拦 SC_RESTORE、也**不要**拦 WM_SHOWWINDOW 里的 SW_MINIMIZE 路径，
               否则任务栏还原会失效。 */
            if (cmd == SC_MINIMIZE && miniOn)
            {
                HideToTray();
                ShowMini(true);
                m.Result = IntPtr.Zero;
                return;                        // 不走系统的 SC_MINIMIZE → 没有原生最小化过程
            }
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
            cv.NavigationCompleted += delegate
            {
                try
                {
                    BeginInvoke((Action)delegate { splash.Visible = false; });
                }
                catch { }
            };

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

    void LayoutSplash()
    {
        if (splash == null || splashTitle == null) return;
        int cx = splash.ClientSize.Width / 2;
        int blockH = splashLogo.Height + 14 + splashTitle.Height;
        int y = splash.ClientSize.Height / 2 - blockH / 2;
        splashLogo.Location = new Point(cx - splashLogo.Width / 2, y);
        splashTitle.Location = new Point(cx - splashTitle.Width / 2, y + splashLogo.Height + 14);
    }

    /* ---------- 收进托盘 ---------- */

    /// <summary>
    /// 收进托盘：窗口与任务栏图标一起消失，只剩托盘图标。
    /// 只做「藏 + 开托盘图标」这一件事，不弹气泡 —— 因为最小化到托盘是高频动作，
    /// 每次都弹气泡很烦。气泡只在关闭按钮走托盘时提示一次（见 HideToTrayWithTip）。
    /// </summary>
    void HideToTray()
    {
        SaveBounds_();                  // 先记下正常态位置，Hide 之后 Location 就不可信了
        Hide();
        trayIcon.Visible = true;
    }

    /// <summary>关闭按钮走「收进托盘」时调用：第一次给个气泡说明，之后静默。</summary>
    void HideToTrayWithTip()
    {
        HideToTray();
        if (firstTrayHide)
        {
            firstTrayHide = false;
            trayIcon.BalloonTipTitle = "摸薪 Mocent";
            trayIcon.BalloonTipText = "已最小化到托盘，双击托盘图标即可打开";
            trayIcon.ShowBalloonTip(2000);
        }
    }

    /* ---------- 位置记忆 ----------
       注：这里**不**再记忆/复位尺寸。
       曾经以为「最小化→还原窗口变大」可以用「记住 lastNormalSize 再复回去」修掉，
       实测无效 —— 因为复位代码跑在 Resize 事件里，而 WinForms 的尺寸修正在
       WM_WINDOWPOSCHANGED → base.WndProc → RestoreWindowBoundsIfNecessary 里面，
       比 Resize 派发得更晚；而且复位本身又触发一次 SetWindowPos，等于反复喂给
       那条错误链路更多输入，可能让膨胀更快。真正的修法在 CreateParams +
       WM_WINDOWPOSCHANGED 的 suppressBorderMetrics 处理上（见文件上方注释）。
       位置记忆是独立需求，保留：最小化时 Location 会被改成 -32000，需提前存下来。 */

    void RememberLoc_()
    {
        // 自己复位时不要再记一次，否则会把偏移后的值当成正确位置
        if (!restoringLoc && Visible && WindowState == FormWindowState.Normal)
        {
            lastNormalLoc = Location;
            locKnown = true;
        }
    }

    void OnResized_()
    {
        bool min = WindowState == FormWindowState.Minimized;
        // 还原瞬间把位置纠回来（系统对无边框窗会用最小化坐标覆盖 Location）
        if (!min && wasMinimized && locKnown && !restoringLoc)
        {
            restoringLoc = true;
            try
            {
                if (Location != lastNormalLoc) Location = lastNormalLoc;
            }
            finally { restoringLoc = false; }
        }
        if (min && locKnown && loaded) SaveLoc_();     // 顺手落盘，异常退出也不丢位置
        wasMinimized = min;
    }

    public void ShowFromTray()
    {
        if (!Visible) Show();
        if (WindowState == FormWindowState.Minimized) WindowState = FormWindowState.Normal;
        // 托盘唤回也显式复位一次位置（Show()/WindowState 切换都可能重新套用最小化坐标）
        if (locKnown && WindowState == FormWindowState.Normal)
        {
            restoringLoc = true;
            try
            {
                if (Location != lastNormalLoc) Location = lastNormalLoc;
            }
            finally { restoringLoc = false; }
        }
        Activate();
        trayIcon.Visible = false;
        /* 藏起来的窗口用 Show() 唤回时不一定抢得到前台（Windows 的前台窗口限制），
           兜一次 SetForegroundWindow。浮窗双击回主窗口靠的就是这个。 */
        try { SetForegroundWindow(Handle); } catch { }
    }

    void OnWebMessage(object sender, CoreWebView2WebMessageReceivedEventArgs e)
    {
        string msg = e.TryGetWebMessageAsString();
        if (msg == "min")
        {
            // 原生最小化：WindowState=Minimized 在无边框窗体上会破坏任务栏还原
            SendMessage(Handle, WM_SYSCOMMAND, (IntPtr)SC_MINIMIZE, IntPtr.Zero);
        }
        else if (msg == "close") Close();
        else if (msg.StartsWith("ca:")) closeToTray = msg.EndsWith("tray");
        else if (msg.StartsWith("mini:"))
        {
            // 记住「显示浮窗」开关状态：最小化时要据此判断走哪条路
            miniOn = msg.EndsWith("on");
            ShowMini(miniOn);
        }
        else if (msg.StartsWith("alpha:"))
        {
            double v;
            if (double.TryParse(msg.Substring(6), NumberStyles.Any, CultureInfo.InvariantCulture, out v))
                SetMiniOpacity(v / 100.0);
        }
        else if (msg.StartsWith("tick:"))
        {
            string[] p = msg.Substring(5).Split('|');
            if (p.Length >= 2) PushMini(p[0], p[1], p.Length > 2 ? p[2] : "");
        }
    }

    /* ---------- 桌面浮窗 ---------- */

    public void ShowMini(bool show)
    {
        if (show)
        {
            if (mini == null || mini.IsDisposed)
            {
                mini = new MiniForm(this);
                mini.SetOpacity_(miniAlpha);
            }
            mini.ShowMini_();
        }
        else if (mini != null && !mini.IsDisposed) mini.Hide();
    }

    public void SetMiniOpacity(double v)
    {
        miniAlpha = Math.Min(1.0, Math.Max(0.4, v));
        if (mini != null && !mini.IsDisposed) mini.SetOpacity_(miniAlpha);
    }

    public void PushMini(string money, string fish, string status)
    {
        if (mini != null && !mini.IsDisposed && mini.Visible) mini.Update_(money, fish, status);
    }

    /// <summary>浮窗被用户在右键菜单里关闭：把网页里的「显示浮窗」开关拨回去，两边状态保持一致。</summary>
    public void MiniClosedByUser()
    {
        miniOn = false;                    // 本地状态同步，避免下次最小化又误判成「已勾选」
        try
        {
            if (!loaded || web.CoreWebView2 == null) return;
            web.CoreWebView2.ExecuteScriptAsync(
                "(function(){var s=document.getElementById('miniOn');" +
                "if(s&&s.checked){s.checked=false;s.dispatchEvent(new Event('change'));}})();");
        }
        catch { }
    }

    /// <summary>
    /// 浮窗双击 → 回主窗口：从托盘/最小化状态还原，并把浮窗收起来。
    /// （既然回到主窗口看详情了，浮窗留着就是多余的）
    /// </summary>
    public void RestoreFromMini()
    {
        ShowFromTray();                                  // 含去最小化 + 位置复位 + 抢前台
        if (mini != null && !mini.IsDisposed) mini.Hide();
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

    // 启动尺寸按 540:1000 基准比例随屏幕工作区等比缩放（2K 满比例、1080p 略缩、
    // 低分屏不溢出），只记忆位置不记忆尺寸——否则上次拖过的怪比例会被还原
    static readonly Size LAUNCH_SIZE = new Size(540, 1000);

    void RestoreBounds_()
    {
        Rectangle wa = Screen.PrimaryScreen.WorkingArea;
        double scale = Math.Min(1.0, Math.Min((wa.Height - 48) / (double)LAUNCH_SIZE.Height,
                                              (wa.Width  - 48) / (double)LAUNCH_SIZE.Width));
        if (scale < 0) scale = 0.3;
        int w = Math.Max(400, (int)Math.Round(LAUNCH_SIZE.Width * scale));
        int h = Math.Min(wa.Height - 48, (int)Math.Round(w * LAUNCH_SIZE.Height / (double)LAUNCH_SIZE.Width));
        Size = new Size(w, h);
        try
        {
            string file = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Mocent", "window.json");
            if (!File.Exists(file)) { DefaultPlace(); return; }
            string json = File.ReadAllText(file, Encoding.UTF8);
            int x = Num_(json, "x"), y = Num_(json, "y");
            Location = new Point(
                Math.Max(wa.Left - Size.Width / 2, Math.Min(x, wa.Right - 60)),
                Math.Max(wa.Top, Math.Min(y, wa.Bottom - 60)));
        }
        catch { DefaultPlace(); }
    }

    void DefaultPlace()
    {
        Rectangle wa = Screen.PrimaryScreen.WorkingArea;
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
            StringBuilder sb = new StringBuilder();
            sb.Append("{\"x\":").Append(Location.X).Append(",\"y\":").Append(Location.Y).Append("}");
            File.WriteAllText(Path.Combine(DataRoot(), "window.json"), sb.ToString(), Encoding.UTF8);
        }
        catch { }
    }

    /** 最小化时把记住的正常态位置落盘（此时 Location 已是最小化坐标，不能用） */
    void SaveLoc_()
    {
        try
        {
            StringBuilder sb = new StringBuilder();
            sb.Append("{\"x\":").Append(lastNormalLoc.X).Append(",\"y\":").Append(lastNormalLoc.Y).Append("}");
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
    static extern IntPtr SendMessage(IntPtr hWnd, int msg, IntPtr wParam, IntPtr lParam);

    [DllImport("user32.dll")]
    static extern int GetSystemMetrics(int index);

    [DllImport("user32.dll")]
    static extern bool SetForegroundWindow(IntPtr h);

    [DllImport("user32.dll")]
    static extern bool ShowWindow(IntPtr h, int cmd);

    [DllImport("user32.dll")]
    static extern bool GetWindowRect(IntPtr h, out RECT r);

    const int SW_RESTORE = 9;

    /// <summary>把已有窗口还原并调到前台（重复启动时调用，不开新窗口）。</summary>
    public void Forefront_()
    {
        if (WindowState == FormWindowState.Minimized) ShowWindow(Handle, SW_RESTORE);
        else ShowWindow(Handle, 5); // SW_SHOW
        SetForegroundWindow(Handle);
        Activate();
    }

    [StructLayout(LayoutKind.Sequential)]
    struct RECT { public int Left, Top, Right, Bottom; }
}

/// <summary>
/// 桌面浮窗：无边框置顶小窗，只显示「今日已计薪」和「摸鱼已赚」。
/// 数据由主窗口 WebView2 每 500ms postMessage 推过来（tick:金额|摸鱼|状态），
/// 位置记在 %LOCALAPPDATA%\Mocent\mini.json，双击回到主窗口，右键调置顶/透明度/关闭。
/// </summary>
public class MiniForm : Form
{
    Label capLbl, moneyLbl, fishLbl;
    Panel dot;
    bool pinTop = true;
    MainForm ownerForm;

    public MiniForm(MainForm owner)
    {
        ownerForm = owner;
        FormBorderStyle = FormBorderStyle.None;
        StartPosition = FormStartPosition.Manual;
        ShowInTaskbar = false;
        TopMost = true;
        Size = new Size(256, 106);
        BackColor = Color.FromArgb(0xFF, 0xFD, 0xF9);
        Font = new Font("Microsoft YaHei UI", 9F);
        Opacity = 0.92;

        capLbl = new Label { Text = "今日已计薪", AutoSize = true,
            ForeColor = Color.FromArgb(0xA4, 0x91, 0x7C), Location = new Point(14, 11) };
        moneyLbl = new Label { Text = "¥0.00", AutoSize = true,
            ForeColor = Color.FromArgb(0x43, 0x35, 0x2A), Location = new Point(12, 27),
            Font = new Font("Microsoft YaHei UI", 20F, FontStyle.Bold) };
        fishLbl = new Label { Text = "摸鱼已赚 ¥0.00", AutoSize = true,
            ForeColor = Color.FromArgb(0x2F, 0xA3, 0x6B), Location = new Point(14, 74),
            Font = new Font("Microsoft YaHei UI", 10F) };
        dot = new Panel { Size = new Size(9, 9), Location = new Point(232, 14) };
        dot.BackColor = StatusColor("");
        Controls.Add(capLbl); Controls.Add(moneyLbl); Controls.Add(fishLbl); Controls.Add(dot);

        ContextMenuStrip menu = new ContextMenuStrip();
        ToolStripMenuItem pinItem = new ToolStripMenuItem("置顶显示");
        pinItem.Checked = true;
        pinItem.Click += delegate
        {
            pinTop = !pinTop; pinItem.Checked = pinTop; TopMost = pinTop;
        };
        menu.Items.Add(pinItem);
        menu.Items.Add(new ToolStripSeparator());
        AddOpacityItem(menu, 100); AddOpacityItem(menu, 90);
        AddOpacityItem(menu, 80); AddOpacityItem(menu, 70);
        menu.Items.Add(new ToolStripSeparator());
        ToolStripMenuItem closeItem = new ToolStripMenuItem("关闭浮窗");
        closeItem.Click += delegate
        {
            Hide();
            if (ownerForm != null) ownerForm.MiniClosedByUser();   // 同步网页开关，避免设置与实际不一致
        };
        menu.Items.Add(closeItem);
        ContextMenuStrip = menu;

        HookDrag(this);
        RestorePos();
    }

    void AddOpacityItem(ContextMenuStrip menu, int pct)
    {
        ToolStripMenuItem it = new ToolStripMenuItem("不透明度 " + pct + "%");
        it.Click += delegate { SetOpacity_(pct / 100.0); };
        menu.Items.Add(it);
    }

    /* ---------- 拖动 / 双击 ----------
       这里踩过一个坑：原来用「MouseDown → SendMessage(WM_NCLBUTTONDOWN, HTCAPTION)」
       把鼠标交给系统的窗口拖动循环。但系统一旦进入拖动循环，就会**吞掉后续的
       DoubleClick 消息** —— 于是注册的 `DoubleClick += Program.Forefront()` 永远不触发，
       表现为「双击浮窗没反应」。
       改法：自己实现拖动（记录按下点、MouseMove 时按屏幕坐标位移 SetWindowPos），
       不把控制权交给系统。这样 MouseDown/MouseMove/MouseUp/DoubleClick 都在正常的
       消息流里，双击能正常触发。
       细节：用 Cursor.Position 算位移而不是 e.Location，避免窗口移动后
       MouseMove 坐标基准跟着变导致抖动。 */
    bool dragging;
    Point dragStartCursor, dragStartWin;

    void HookDrag(Control c)
    {
        c.MouseDown += delegate(object s, MouseEventArgs e)
        {
            if (e.Button != MouseButtons.Left) return;
            dragging = true;
            dragStartCursor = Cursor.Position;
            dragStartWin = Location;
        };
        c.MouseMove += delegate(object s, MouseEventArgs e)
        {
            if (!dragging) return;
            Point cur = Cursor.Position;
            Location = new Point(dragStartWin.X + (cur.X - dragStartCursor.X),
                                 dragStartWin.Y + (cur.Y - dragStartCursor.Y));
        };
        c.MouseUp += delegate(object s, MouseEventArgs e)
        {
            if (!dragging) return;
            dragging = false;
            SavePos();                       // 松手即落盘，拖完不用等关闭
        };
        // 双击浮窗 = 回主窗口。直接问 ownerForm，不再绕静态的 Program.Forefront()
        c.DoubleClick += delegate { if (ownerForm != null) ownerForm.RestoreFromMini(); };
        foreach (Control ch in c.Controls) HookDrag(ch);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        base.OnPaint(e);
        using (Pen p = new Pen(Color.FromArgb(0xF0, 0xE4, 0xD2)))
            e.Graphics.DrawRectangle(p, 0, 0, Width - 1, Height - 1);
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        SavePos();
        base.OnFormClosing(e);
    }

    public void ShowMini_()
    {
        if (!Visible) Show();
        else { if (WindowState == FormWindowState.Minimized) WindowState = FormWindowState.Normal; }
    }

    public void SetOpacity_(double v) { Opacity = Math.Min(1.0, Math.Max(0.4, v)); }

    public void Update_(string money, string fish, string status)
    {
        if (moneyLbl.Text != money) moneyLbl.Text = money;
        string f = "摸鱼已赚 " + fish;
        if (fishLbl.Text != f) fishLbl.Text = f;
        Color c = StatusColor(status);
        if (dot.BackColor != c) dot.BackColor = c;
    }

    static Color StatusColor(string s)
    {
        switch (s)
        {
            case "working":
            case "overtime": return Color.FromArgb(0x2F, 0xA3, 0x6B);
            case "lunch": return Color.FromArgb(0xE6, 0xA2, 0x3C);
            case "before": return Color.FromArgb(0x8A, 0xA1, 0xC1);
            case "after": return Color.FromArgb(0x7D, 0x6B, 0xE0);
            case "weekend":
            case "holiday": return Color.FromArgb(0x5B, 0xB8, 0xA8);
            default: return Color.FromArgb(0xB6, 0xA4, 0x8D);
        }
    }

    /* ---------- 位置记忆 ---------- */

    string PosFile()
    {
        string la = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        return Path.Combine(la, "Mocent", "mini.json");
    }

    void RestorePos()
    {
        Rectangle wa = Screen.PrimaryScreen.WorkingArea;
        Location = new Point(wa.Right - Width - 24, wa.Bottom - Height - 24);
        try
        {
            string f = PosFile();
            if (!File.Exists(f)) return;
            string json = File.ReadAllText(f, Encoding.UTF8);
            int x = Num(json, "x"), y = Num(json, "y");
            Location = new Point(
                Math.Max(wa.Left - Width / 2, Math.Min(x, wa.Right - 60)),
                Math.Max(wa.Top, Math.Min(y, wa.Bottom - 40)));
        }
        catch { }
    }

    void SavePos()
    {
        try
        {
            string dir = Path.GetDirectoryName(PosFile());
            Directory.CreateDirectory(dir);
            File.WriteAllText(PosFile(),
                "{\"x\":" + Location.X + ",\"y\":" + Location.Y + "}", Encoding.UTF8);
        }
        catch { }
    }

    static int Num(string json, string key)
    {
        Match m = Regex.Match(json, "\"" + key + "\"\\s*:\\s*(-?\\d+)");
        int v;
        if (m.Success && int.TryParse(m.Groups[1].Value, NumberStyles.Integer, CultureInfo.InvariantCulture, out v)) return v;
        return 0;
    }
}
