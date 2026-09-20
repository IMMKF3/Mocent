# 调研报告：WinForms 无边框窗（`WS_THICKFRAME` 无 `WS_CAPTION`）最小化还原后窗口持续变大

> 调研日期：2026-09-20
> 范围：仅调研，未修改任何项目文件。
> 说明：本报告在 `D:/!GitHubProjects/Mocent/` 下新增了一个 Markdown 文件用于交付结论，未触碰任何代码。

---

## 0. 结论速览（TL;DR）

| 问题 | 结论 | 依据等级 |
|---|---|---|
| 窗口为何每次还原都变大？ | **主因不在 `WM_NCCALCSIZE` 的返回值，而在 WinForms 自身的 `Form.SizeFromClientSizeInternal()` → `AdjustWindowRectExForControlDpi()`**。它在 `WM_WINDOWPOSCHANGED` 处理链里被调用，而 `AdjustWindowRectEx` **只认窗口样式（`WS_THICKFRAME` 等）、完全不认你在 `WndProc` 里对 `WM_NCCALCSIZE` 的处理**。你既然在 `CreateParams` 里加了 `WS_THICKFRAME`，WinForms 每次还原都会「客户区尺寸 + 系统边框厚度」补偿一次，于是每轮膨胀一个边框厚度。 | **官方源码级（dotnet/winforms 主分支）** |
| 我的目的是「客户区 = 整个窗口、窗口矩形不变」，`WM_NCCALCSIZE` 应返回 0 还是 `WVR_*`？ | **返回 0**（`wParam == TRUE` 时）。`WVR_ALIGN*` 的语义是**像素保留/对齐策略**，不是「我要多大」。当 `rgrc[0]` 被你改成非默认值时，`WVR_ALIGN*` 会影响旧客户区像素相对新位置的贴靠方向；但**没有任何官方文档说明 `WVR_ALIGN*` 会让系统去移动或改变窗口矩形本身**。 | **官方文档 + 官方源码（Chromium/Electron 实测行为）** |
| 错误返回 `WVR_ALIGNTOP \| WVR_ALIGNLEFT` 是否恰好导致每次还原变大？ | **找不到确凿依据**。MSDN 明确写「返回零 ⇒ 旧客户区保留并与新客户区左上角对齐」——也就是说 **`ALIGNTOP+ALIGNLEFT` 在语义上等价于返回 0 的默认对齐**，它不太可能是膨胀的元凶。真正基于 `WVR_*` 的已知坑是 **`WVR_REDRAW` + 非客户区存在时 Windows 会把客户区像素和子窗口错误偏移**（Chromium 源码注释明确记录，见 §3）。 | **官方文档 + 官方源码注释（有权威依据）；「ALIGNTOP 导致膨胀」无依据** |
| Chrome/Electron 怎么做的？ | 两者都**返回 0 或 `WVR_REDRAW`（互斥）**，且关键在于**「先改 `rgrc[0]`，再返回标志」**；从不用 `WVR_ALIGN*`。Electron **额外**在 `WM_GETMINMAXINFO` 里用 `SetWindowPlacement` 回灌 `rcNormalPosition` 来对抗还原错位，并在 `SetWindowPlacement` 期间**让 Chromium 接管 `WM_NCCALCSIZE`**（注释原文：否则窗口尺寸会错）。 | **官方源码级** |
| 不用 `WS_THICKFRAME` 有没有替代路线？ | 有，但**代价明确**：`WM_NCHITTEST` 返回 `HTLEFT/...` **本身不依赖** `WS_THICKFRAME`（它只是告诉系统"用户点了边框"，系统据此进入 `SC_SIZE` 循环），但你**必须自己处理 `WM_SIZING` 或接受默认缩放**；更关键的是 `WS_THICKFRAME` 缺失会导致 **系统投影阴影、Win11 自动圆角、贴靠布局（Snap）、窗口开合动画**等一连串能力丢失（微软文档与实测均有明确说明）。 | **官方文档 + 多个独立实测来源** |

**一句话诊断你的场景**：你踩的是 **WinForms `AdjustWindowRectEx` 与自定义 `WM_NCCALCSIZE` 之间的语义冲突**（一个经典 WinForms 陷阱），**不是** `WVR_ALIGN*` 用错。

---

## 1. 问题成因：两条独立成因链

这个「最小化/最大化还原后窗口越来越大」的现象在无边框窗口社群中**是一个反复出现的问题**，但实际上有**两条不同的成因链**，症状相似、修复方式完全不同。必须先把它们分开。

### 成因链 A（尤其针对 WinForms）：`AdjustWindowRectEx` 与 `WM_NCCALCSIZE` 冲突

**这是你的场景最可能的成因。**

#### A.1 权威依据（官方源码）

Stack Overflow 上关于「Custom window frame with DWM」的经典问题中，**Filip Navara**（.NET/WinForms 领域资深贡献者）给出的回答明确把它定性为 **Windows Forms 的 bug**：

> "This is actually a bug in Windows Forms and there's a workaround. In function `Form.SizeFromClientSize(int, int)` the `AdjustWindowRectEx` function is used to translate the size and it always uses the default measurements and **can't be overridden**. This function is called from two places:
> - `RestoreWindowBoundsIfNecessary` in `WM_WINDOWPOSCHANGED` window message handler
> - `SetClientSizeCore`"

来源：https://stackoverflow.com/questions/20085317/custom-window-frame-with-dwm-how-to-handle-wm-nccalcsize-correctly

**我把这条社区结论拿到了当前 .NET 官方源码里逐条验证，全部成立：**

1. **`WM_WINDOWPOSCHANGED` 确实触发 `RestoreWindowBoundsIfNecessary()`**
   `dotnet/winforms` `Form.cs`（main 分支）：
   ```csharp
   private void WmWindowPosChanged(ref Message m)
   {
       UpdateWindowState();
       base.WndProc(ref m);
       RestoreWindowBoundsIfNecessary();   // ← 就在这里
   }
   ```
   来源：https://github.com/dotnet/winforms/blob/main/src/System.Windows.Forms/System/Windows/Forms/Form.cs （`WmWindowPosChanged`）

2. **`RestoreWindowBoundsIfNecessary()` 里会做「客户区 → 窗口尺寸」换算**
   ```csharp
   Size restoredSize = _restoredWindowBounds.Size;
   if ((_restoredWindowBoundsSpecified & BoundsSpecified.Size) != 0)
   {
       restoredSize = SizeFromClientSizeInternal(restoredSize);   // ← 关键调用
   }
   ```

3. **`SizeFromClientSizeInternal` 底层就是 `AdjustWindowRectEx...`**
   `dotnet/winforms` `Control.cs`（main 分支）：
   ```csharp
   internal Size SizeFromClientSizeInternal(Size size)
   {
       RECT rect = new(size);
       CreateParams cp = CreateParams;
       AdjustWindowRectExForControlDpi(ref rect, (WINDOW_STYLE)cp.Style, false, (WINDOW_EX_STYLE)cp.ExStyle);
       return rect.Size;
   }
   ```
   来源：https://github.com/dotnet/winforms/blob/main/src/System.Windows.Forms/System/Windows/Forms/Control.cs （`SizeFromClientSizeInternal`）

4. **`Form.ComputeWindowSize` 也是同一套 `AdjustWindowRectExForControlDpi`**
   `Form.cs`：
   ```csharp
   private Size ComputeWindowSize(Size clientSize, WINDOW_STYLE style, WINDOW_EX_STYLE exStyle)
   {
       RECT result = new(clientSize);
       AdjustWindowRectExForControlDpi(ref result, style, false, exStyle);
       return result.Size;
   }
   ```

#### A.2 为什么这必然导致「每轮膨胀一个边框」

- `AdjustWindowRectEx` 的行为**只取决于传入的窗口样式**。传入的 `style` 来自你的 `CreateParams` —— 而你在里面塞了 `WS_THICKFRAME`。
- 于是它每次都按「可调整边框窗口」把尺寸**放大一个 `SM_CXSIZEFRAME + SM_CXPADDEDBORDER` 的厚度**（Win10/11 上通常是 8px 级别）。
- 你在 `WndProc` 里对 `WM_NCCALCSIZE` 返回 0 / 改 `rgrc`，**完全不影响**这条路径 —— 因为 `AdjustWindowRectEx` 是**用户态纯计算函数**，它不会去问你的 `WM_NCCALCSIZE` 处理结果，它只看样式位。
- 于是：**窗口矩形（由 WinForms 换算得出）与「客户区 = 整个窗口」的设定（由你 `WM_NCCALCSIZE` 维持）互相打架**，每次「最小化→还原」都走一遍 `WM_WINDOWPOSCHANGED` → 换算 → 偏大一个边框。

> 这就是为什么**把 `WS_THICKFRAME` 加进 `CreateParams` 反而制造了这个 bug**：它让 `AdjustWindowRectEx` 认为「这个窗口有边框」，从而开始做正数补偿。

#### A.3 官方/权威依据 vs 社区经验

- **官方源码依据（强）**：`Form.cs` 的 `WmWindowPosChanged` / `RestoreWindowBoundsIfNecessary`，`Control.cs` 的 `SizeFromClientSizeInternal`，都是 main 分支可核对代码。
- **社区经验（中）**：SO 上 Filip Navara 把它称为「WinForms bug」，并给出 `CreateParams` 临时清空边框位的 hack。这条在 2022 年镜像页可完整读到，但 SO 原文页面被 Cloudflare 拦截，本次以镜像页为准。
- 本次**未能**找到微软官方 issue/文档承认这是一个「bug」（即：微软文档层面没有把它列为 known issue）。**准确表述是：这是 WinForms 实现细节与自定义 `WM_NCCALCSIZE` 之间未做协调所导致的必然行为，官方源码可证，但官方未将其标记为 bug。**

### 成因链 B（通用 Win32）：`WM_NCCALCSIZE` / `WVR_*` 处理不当

这条链**不是 WinForms 专属**，Chrome/Electron/原生 Win32 都会遇到。见 §2、§3。

**重要判断**：你描述的「加了 `WS_THICKFRAME`、没有 `WS_CAPTION`、Dock=Fill WebView2、反复最小化/还原越来越大」症状，与**成因链 A** 高度吻合（尤其是「每轮固定增量」且量级接近系统边框厚度这一点）。建议先在成因链 A 上验证。

---

## 2. `WM_NCCALCSIZE` 返回值的**确切语义**（以微软官方文档为准）

来源（官方，权威）：https://learn.microsoft.com/en-us/windows/win32/winmsg/wm-nccalcsize

### 2.1 文档原文（关键段落）

> "If the `wParam` parameter is FALSE, the application should return zero.
> If `wParam` is TRUE, the application should return zero or a combination of the following values.
> **If `wParam` is TRUE and an application returns zero, the old client area is preserved and is aligned with the upper-left corner of the new client area.**"

> "When `wParam` is TRUE, simply returning 0 without processing the `NCCALCSIZE_PARAMS` rectangles will cause the client area to resize to the size of the window, including the window frame. This will remove the window frame and caption items from your window, leaving only the client area displayed."

### 2.2 各标志的确切语义（逐条对照官方表格）

| 标志 | 值 | 官方描述（中文版官方页同义） |
|---|---|---|
| `WVR_ALIGNTOP` | `0x0010` | "Specifies that the client area of the window is to be preserved and aligned with the top of the new position of the window." |
| `WVR_ALIGNLEFT` | `0x0020` | "…preserved and aligned with the left side of the new position of the window." |
| `WVR_ALIGNBOTTOM` | `0x0040` | "…preserved and aligned with the bottom of the new position of the window." |
| `WVR_ALIGNRIGHT` | `0x0080` | "…preserved and aligned with the right side of the new position of the window." |
| `WVR_HREDRAW` | `0x0100` | "…causes the window to be completely redrawn if the client rectangle changes size horizontally."（类似 `CS_HREDRAW`） |
| `WVR_VREDRAW` | `0x0200` | "…causes the window to be completely redrawn if the client rectangle changes size vertically."（类似 `CS_VREDRAW`） |
| `WVR_REDRAW` | `0x0300` | "This value causes the entire window to be redrawn. It is a combination of `WVR_HREDRAW` and `WVR_VREDRAW` values." |
| `WVR_VALIDRECTS` | `0x0400` | "…the rectangles specified by the `rgrc[1]` and `rgrc[2]` members … contain valid destination and source area rectangles … **This flag cannot be combined with any other flags.**" |

中文官方页（同源，便于对照）：https://learn.microsoft.com/zh-cn/windows/win32/winmsg/wm-nccalcsize

### 2.3 「文档里对这个返回值整体是怎么描述的？」

官方对返回值的**整体定位**是：

> 它是**「旧客户区像素如何被保留 / 重绘」的策略开关**，配合 `NCCALCSIZE_PARAMS.rgrc[1]`（目标矩形）与 `rgrc[2]`（源矩形）一起，决定「把旧窗口图像的哪一块拷贝到新窗口的哪一块」。

关键点（官方 `NCCALCSIZE_PARAMS` 文档，权威）：https://learn.microsoft.com/en-us/windows/win32/api/winuser/ns-winuser-nccalcsize_params

> 进入时：`rgrc[0]` = 建议的新窗口矩形；`rgrc[1]` = 移动/改大小前的窗口矩形；`rgrc[2]` = 移动/改大小前的**客户区**矩形。
> 返回时：`rgrc[0]` = **新客户区矩形**；`rgrc[1]` = 有效目标矩形；`rgrc[2]` = 有效源矩形。

**也就是说：真正决定「客户区长什么样」的是你写回 `rgrc[0]` 的内容，不是返回值。** 返回值只描述「像素怎么搬」。

### 2.4 明确回答你的三个提问

**Q：我的目的是「客户区 = 整个窗口、窗口矩形完全不变」，应该返回 0 还是某个 `WVR_*` 组合？**

**A：返回 0。**

理由（有官方依据）：
1. 官方 Remarks 明确：「`wParam` 为 TRUE 时，直接返回 0 而不处理 `NCCALCSIZE_PARAMS` 矩形，会使客户区扩展到整个窗口（含边框），移除窗口边框和标题，只留客户区」——这正是你要的「客户区 = 整个窗口」。
2. 官方还明确：「返回 0 ⇒ 旧客户区被保留并与新客户区的左上角对齐」——即返回 0 **本身就已经是左上对齐语义**，无需再叠 `WVR_ALIGNTOP | WVR_ALIGNLEFT`。
3. 若你需要**保持窗口矩形不变**，那是在 `rgrc[0]` 上不动（或按需修正）来实现的，与返回值无关。

**Q：`WVR_ALIGN*` 是否会导致系统去移动或调整窗口大小？**

**A：查不到"会导致系统移动/调整窗口矩形"的官方依据。找不到。**

- 官方对 `WVR_ALIGN*` 的全部描述都限定在 **"the client area of the window is to be preserved and aligned"**——主语是 **client area（客户区）**，不是 **window（窗口矩形）**。
- 官方对 `WM_NCCALCSIZE` 的整体描述是「控制窗口**客户区**的内容/尺寸」，从未提及该消息或其返回值可以移动/改变窗口矩形。
- 本次调研**未找到任何**官方文档、官方博客（含 Raymond Chen）或 Chromium/Electron 源码注释，声称 `WVR_ALIGN*` 会移动或调整窗口本体尺寸。**明确结论：无依据。**

**Q：如果错误返回 `WVR_ALIGNTOP | WVR_ALIGNLEFT`，是否恰好导致窗口每次还原都变大？**

**A：找不到确凿依据。而且从文档看，这个假设不成立。**

依据：官方明确「返回 **0** ⇒ 旧客户区保留并与新客户区**左上角**对齐」。`WVR_ALIGNTOP | WVR_ALIGNLEFT` 表达的正是**同一个左上角对齐语义**。所以：

- 若你的 `rgrc[0]` 处理是「客户区 = 整个窗口」，那么返回 `WVR_ALIGNTOP | WVR_ALIGNLEFT` 与返回 `0` 在该语义上**应当等价**，不会额外放大窗口。
- **因此 `ALIGNTOP | ALIGNLEFT` 不是膨胀元凶。** 这个假设在官方文档层面站不住。
- 反过来说，**真正有权威依据的 `WVR_*` 陷阱是 `WVR_REDRAW`**，见 §3。

> 诚实声明：我**没有**找到「`WVR_ALIGNTOP`/`WVR_ALIGNLEFT` 用错导致窗口膨胀」的任何案例、讨论或 issue。§4 会详述这次检索的边界。

---

## 3. Chrome / Electron 系的实际做法（含代码来源）

这是本次调研**证据最硬**的部分——直接读官方源码。

### 3.1 Chromium：`HWNDMessageHandler::OnNCCalcSize`

来源（官方源码）：https://source.chromium.org/chromium/chromium/src/+/main:ui/views/win/hwnd_message_handler.cc
（可对照 raw：https://chromium.googlesource.com/chromium/src/+/main/ui/views/win/hwnd_message_handler.cc）

**核心骨架（`OnNCCalcSize`，约 2524–2625 行）：**

```cpp
LRESULT HWNDMessageHandler::OnNCCalcSize(BOOL mode, LPARAM l_param) {
  // Let User32 handle the first nccalcsize for captioned windows
  // so it updates its internal structures (specifically caption-present)
  // Without this Tile & Cascade windows won't work.
  // See http://code.google.com/p/chromium/issues/detail?id=900
  if (is_first_nccalc_) {
    is_first_nccalc_ = false;
    if (::GetWindowLong(hwnd(), GWL_STYLE) & WS_CAPTION) {
      SetMsgHandled(FALSE);
      return 0;
    }
  }

  RECT* client_rect =
      mode ? &(reinterpret_cast<NCCALCSIZE_PARAMS*>(l_param)->rgrc[0])
           : reinterpret_cast<RECT*>(l_param);

  HMONITOR monitor = ::MonitorFromWindow(hwnd(), MONITOR_DEFAULTTONULL);
  if (!monitor) {
    // We might end up here if the window was previously minimized and the
    // user clicks on the taskbar button to restore it in the previous
    // position. In that case WM_NCCALCSIZE is sent before the window
    // coordinates are restored to their previous values, so our (left,top)
    // would probably be (-32000,-32000) like all minimized windows. ...
    monitor = MonitorFromRect(client_rect, MONITOR_DEFAULTTONULL);
    if (!monitor) {
      return 0;
    }
  }

  gfx::Insets insets;
  bool got_insets = GetClientAreaInsets(&insets, monitor);
  if (!got_insets && !IsFullscreen() && !(mode && !delegate_->HasFrame())) {
    SetMsgHandled(FALSE);
    return 0;
  }

  // ★ 先改 rgrc[0]（客户区矩形），加/减 insets
  client_rect->left  += insets.left();
  client_rect->top   += insets.top();
  client_rect->bottom -= insets.bottom();
  client_rect->right -= insets.right();
  if (IsMaximized()) {
    // ... 自动隐藏任务栏边缘补偿 ...
    // We cannot return WVR_REDRAW when there is nonclient area, or Windows
    // exhibits bugs where client pixels and child HWNDs are mispositioned by
    // the width/height of the upper-left nonclient area.
    return 0;                       // ★ 最大化时返回 0
  }

  // If the window bounds change, we're going to relayout and repaint anyway.
  // Returning WVR_REDRAW avoids an extra paint before that of the old client
  // pixels in the (now wrong) location, ...
  if (insets.left() == 0 || insets.top() == 0) {
    return 0;                       // ★ 左/上 insets 为 0 时返回 0
  }
  return mode ? WVR_REDRAW : 0;     // ★ 仅此时用 WVR_REDRAW，绝不用 WVR_ALIGN*
}
```

**结论性观察（源码级）：**

1. **Chromium 从不用 `WVR_ALIGN*`。** 它只用 `0` 或 `WVR_REDRAW`，二选一。
2. **它明确记录了 `WVR_REDRAW` + 非客户区并存时的 Windows bug**（注释原文见上）。这是唯一有权威依据的 `WVR_*` 陷阱，**不是 `ALIGNTOP`**。
3. **它显式处理了「最小化后从任务栏还原」这个场景**（`MonitorFromWindow` 失败 → 回退到 `MonitorFromRect(client_rect)`），注释原文描述了「最小化窗口坐标为 `(-32000,-32000)`，`WM_NCCALCSIZE` 早于坐标恢复被发出」的时序问题。**这正是与你描述的还原场景强相关的一段。**
4. **`GetClientAreaInsets` 的关键逻辑**（约 1758–1785 行）：
   ```cpp
   bool HWNDMessageHandler::GetClientAreaInsets(gfx::Insets* insets, HMONITOR monitor) const {
     int frame_thickness = ui::GetResizableFrameThicknessFromMonitorInPixels(
         monitor, ::GetWindowLong(hwnd(), GWL_STYLE) & WS_CAPTION);
     if (delegate_->GetClientAreaInsets(insets, frame_thickness)) return true;
     DCHECK(insets->IsEmpty());
     if (!delegate_->HasNonClientView() || HasSystemFrame()) return false;

     if (IsMaximized()) {
       // Windows automatically adds a standard width border to all sides when a
       // window is maximized.
       if (!delegate_->HasFrame()) {
         frame_thickness -= 1;      // ★ 无边框窗口最大化时，insets 减 1
       }
       *insets = gfx::Insets(frame_thickness);
       return true;
     }
     *insets = gfx::Insets();       // ★ 非最大化：insets 为空 ⇒ 客户区 = 整个窗口
     return true;
   }
   ```
   注意 `frame_thickness` 来自 `SM_CXSIZEFRAME + SM_CXPADDEDBORDER` 一类系统度量（对应微软 `GetSystemMetrics` 文档：`SM_CXSIZEFRAME` = 可调整边框厚度；`SM_CXPADDEDBORDER` = 带标题窗口的边框附加 padding）。
   来源：https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-getsystemmetrics

### 3.2 Chromium 的其他补偿点

**`OnGetMinMaxInfo`**（官方源码，约 2157–2183 行）：它**不改** `WM_NCCALCSIZE` 回来的东西，而是通过:
```cpp
if (delegate_->WidgetSizeIsClientSize()) {
  InflateClientSizeConstraintsInPixels(hwnd(), min_window_size, max_window_size);  // ★ 客户端↔窗口尺寸换算
}
minmax_info->ptMinTrackSize.x = min_window_size.width();
...
```
即：**Chromium 把「客户区尺寸约束」显式换算成「窗口尺寸约束」**。这恰好是 WinForms 里 `AdjustWindowRectEx` 想干而干错的那件事——区别在于 **Chromium 的换算与实际 border 状态一致，WinForms 的换算盲目按样式位来**。

**`OnWindowPosChanging`**（官方源码，约 3109+ 行）：处理最大化边界的重算（`expected_maximized_bounds` / `incorrect_maximized_bounds`），用于多显示器切换后修正最大化窗口尺寸。**它是针对「最大化」而非「最小化还原」的补偿。**

**`OnSize`**（约 2968–2978 行）：
```cpp
if (DidMinimizedChange(last_size_param_, param) && IsTopLevelWindow(hwnd())) {
  delegate_->HandleWindowMinimizedOrRestored(param != SIZE_MINIMIZED);
}
last_size_param_ = param;
RedrawWindow(...);
ResetWindowRegion(false, true);
```
即：**显式检测「最小化状态变化」并通知上层**——而不是靠返回值。

### 3.3 Electron：额外的一层「还原纠错」（极关键）

**① `GetClientAreaInsets` / `WM_NCCALCSIZE` 的几何定义**
来自 Electron PR #46703（官方仓库）的分析，描述现状与改动：
> "For frameless windows, Electron needs to explicitly define the non-client area geometry, typically during the `WM_NCCALCSIZE` message. … it uses `GetClientAreaInsets` … The primary change is in `GetClientAreaInsets`. Previously, insets (using `GetSystemMetrics(SM_CXSIZEFRAME) + GetSystemMetrics(SM_CXPADDEDBORDER)`) were only applied when the frameless window was maximized. Now, when the window is not maximized, insets are applied to the left, bottom, and right sides (`gfx::Insets::TLBR(0, thickness, thickness, thickness)`). The top inset remains 0 … These insets are used during the `WM_NCCALCSIZE` message processing."

来源：https://github.com/electron/electron/pull/46703

**② `WM_GETMINMAXINFO` 里用 `SetWindowPlacement` 回灌 `rcNormalPosition`（直接对应你的症状）**
Electron 官方源码 `native_window_views_win.cc`：

```cpp
// Avoid side effects when calling SetWindowPlacement.
if (is_setting_window_placement_) {
  // Let Chromium handle the WM_NCCALCSIZE message otherwise the window size
  // would be wrong.
  // See https://github.com/electron/electron/issues/22393 for more.
  if (message == WM_NCCALCSIZE)
    return false;                        // ★ 让 Chromium 接管，否则尺寸会错
  *result = DefWindowProc(GetAcceleratedWidget(), message, w_param, l_param);
  return true;
}
```
以及 `WM_GETMINMAXINFO` 分支：
```cpp
case WM_GETMINMAXINFO: {
  WINDOWPLACEMENT wp; wp.length = sizeof(WINDOWPLACEMENT);
  // We do this to work around a Windows bug, where the minimized Window
  // would report that the closest display to it is not the one that it was
  // previously on (but the leftmost one instead). We restore the position
  // of the window during the restore operation, ...
  if (!last_normal_placement_bounds_.IsEmpty() &&
      (IsVisible() || IsMinimized()) &&
      GetWindowPlacement(GetAcceleratedWidget(), &wp)) {
    wp.rcNormalPosition = last_normal_placement_bounds_.ToRECT();   // ★ 回灌正常位置
    // When calling SetWindowPlacement, Chromium would do window messages
    // handling. ... We work around the crash by prevent Chromium from handling
    // window messages until the SetWindowPlacement call is done.
    // See https://github.com/electron/electron/issues/21614 for more.
    is_setting_window_placement_ = true;
    SetWindowPlacement(GetAcceleratedWidget(), &wp);               // ★ 主动设定
    is_setting_window_placement_ = false;
    last_normal_placement_bounds_ = gfx::Rect();
  }
  return false;
}
```
来源：https://github.com/electron/electron/blob/main/shell/browser/native_window_views_win.cc

**③ 官方 issue 明确记载「最小化还原尺寸变大」是本问题的历史症结**
- electron/electron#22393 — *"Frameless minimum sized window is not restored to previous size after minimize and restore"*
  - 症状（官方 issue 原文）：
    > "- Window is minimized
    >  - Window is restored
    >  - Window is **slightly higher and wider than before the minimization**"
  - 影响版本：7.1.10 – 9.0.0-beta.3（最后一个好版本 7.1.9）
  - 来源：https://github.com/electron/electron/issues/22393
- electron/electron#22440 — 同类症状的 maximize/unmaximize 版本，并**串联了 6 个相关 issue**（#13533、#7951、#15702、#19816、#22393、#2498）。这直接证明这是一个**反复回归的经典问题**。
  来源：https://github.com/electron/electron/issues/22440
- 另外有开发者在 7.3.3 仍复现、升级到 10.4.7 后消失的实测记录（社区来源，可信度中）：
  https://blog.csdn.net/chenqk_123/article/details/118417587

**④ 关于 Electron「窗口 resize 时旧帧残留」的一次公开复盘**
Electron 官方博客有一篇专门讲 Windows 窗口 resize 行为的文章（涉及 Chromium 图形栈、ANGLE/D3D11），可作为「这条路很坑」的旁证（但不是本问题的直接修复）：
https://www.electronjs.org/blog/tech-talk-window-resize-behavior

### 3.4 Chrome/Electron 系结论归纳

| 手段 | Chrome/Chromium | Electron | 你的场景可否照搬 |
|---|---|---|---|
| `WM_NCCALCSIZE` 返回 `WVR_ALIGN*` | **从不使用** | **不使用** | 不要用 |
| `WM_NCCALCSIZE` 返回 `0` / `WVR_REDRAW` | 用，且二选一 | 交给 Chromium | 建议返回 `0` |
| 先改 `rgrc[0]` 再返回标志 | **是核心手法** | 同 | **必须照搬这一顺序** |
| `WM_GETMINMAXINFO` 补偿 | 用于最大化边界 | 用于还原纠错（`SetWindowPlacement`） | 可选，还原类问题需要 |
| `WM_WINDOWPOSCHANGING` 补偿 | 用于最大化/贴靠 | 本文件未处理 | 视情况 |
| `WM_SIZE` 里检测最小化状态变化 | **有**（`DidMinimizedChange`） | 有 | 可用于「检测并纠正」 |
| 显式处理「最小化还原时坐标还是 -32000」的时序 | **有专门注释与回退** | 有 | 高危点，需注意 |

---

## 4. 「`WVR_ALIGNUP` / `WVR_ALIGNLEFT` 用错导致窗口膨胀」的检索结果

**结论：找不到确凿依据。明确说找不到。**

我按以下方向做了多轮检索：

1. `"WM_NCCALCSIZE" return "WVR_ALIGNTOP" OR "WVR_ALIGNLEFT" window grows larger each restore`
   → **只返回 MSDN 官方文档本身**，无任何讨论、issue 或案例。
2. `"WVR_ALIGNUP" OR "WVR_NOCOPYBITS" WM_NCCALCSIZE legacy old name`
   → **检索不到任何有效结果**。这两个符号名在本次检索范围内**没有任何可靠来源**。
   - 需要说明：`WVR_ALIGNUP` 这个拼写**不是**微软 `winuser.h` 中定义的名字。官方名称是 `WVR_ALIGNTOP`（见 §2.2）。本次检索**无法证实** `WVR_ALIGNUP` 是一个真实存在过的历史别名。**这一点我明确标注为「未找到依据」**（既没找到它存在，也没找到它不存在）。
   - 至于 `WVR_NOCOPYBITS`：同样**未找到任何可靠来源**。微软文档中与「不拷贝旧位图」相关的正式值是 `WVR_REDRAW`（它「导致整个窗口重绘」，从而避免拷贝旧客户区）。**因此如果你看到的资料把「不拷贝」叫 `WVR_NOCOPYBITS`，那更可能是非官方的描述性称呼，而非官方符号。**
3. Chromium/Electron 源码全量 grep `WVR_` → **只有 `WVR_REDRAW` 出现一次**，`WVR_ALIGN*` 一次都没有。这是很强的反证：如果 `WVR_ALIGN*` 是主流无边框实现的必需品或常见陷阱，Chromium 不会完全不用。

**有权威依据的相关陷阱（替代上述假设）**：`WVR_REDRAW` 与「存在非客户区」并存时，Windows 会出现「客户区像素与子 HWND 被上-左非客户区宽高错位」的 bug，见 §3.1 Chromium 源码注释原文。**如果你确实在某处错误地返回了 `WVR_REDRAW`，那才是有据可查的错位来源** —— 但它的症状是"错位/残留"，不是"每轮稳定变大"。

---

## 5. 替代路线调研：不用 `WS_THICKFRAME` 如何实现可调整大小

### 5.1 三条常见路线及其对 `WS_THICKFRAME` 的依赖

| 路线 | 做法 | 依赖 `WS_THICKFRAME`？ | 说明 |
|---|---|---|---|
| **路线 1**：加 `WS_THICKFRAME` + `WM_NCCALCSIZE` 正确返回 `0` | 保留系统 resize 能力，但在 `WM_NCCALCSIZE` 里把客户区扩到整个窗口，不画边框 | **依赖**（这正是 Chrome/Electron 路线） | 官方文档默认路线的自定义版；能保住绝大多数系统能力 |
| **路线 2**：`WM_NCHITTEST` 返回 `HTLEFT/HTRIGHT/HTTOP/HTBOTTOM/HT...` | 自己判断鼠标位置，返回 `HT*` 让系统进入缩放循环 | **不依赖**（但需自己处理后续） | 见 §5.2 的详细辨析 |
| **路线 3**：完全手动 resize | 自己捕获鼠标、自己 `SetWindowPos` | **不依赖** | 完全脱离系统能力，代价最大 |

### 5.2 关键辨析：`WM_NCHITTEST` 返回 `HT*` 是否仍依赖 `WS_THICKFRAME`？

**结论：不依赖 `WS_THICKFRAME`，但"依赖"的是一个更隐蔽的前提。**

依据链：

1. **官方文档（权威）**：`WM_NCHITTEST` 的返回值语义是「告诉系统鼠标落在窗口的哪个部分」。
   来源：https://learn.microsoft.com/en-us/windows/win32/inputdev/wm-nchittest
   `HTLEFT`/`HTRIGHT`/`HTTOP`/`HTTOPLEFT`/… 的官方描述都是「**在窗口左边框/上边框/…**」。系统收到这些值后会进入**窗口调整大小循环**（发送 `WM_SIZING`，最终 `WM_SIZE`）。

2. **`WS_THICKFRAME` 的官方定义（权威）**：
   来源：https://learn.microsoft.com/en-us/windows/win32/winmsg/window-styles
   > `WS_THICKFRAME` `0x00040000L` — "The window has a sizing border. Same as the `WS_SIZEBOX` style."

   注意：定义里说的是「**有** sizing border」。**它描述的是"窗口有没有边框"，而不是"`HTLEFT` 能不能生效"**。从官方文档看，`WM_NCHITTEST` 的 `HT*` 处理与 `WS_THICKFRAME` 之间**没有文档化的硬依赖**。

3. **实践中的关键点（社区经验 + MS 论坛回复，可信度中）**：
   - 微软 Learn Q&A 中，MS 外部工程师回复「要保留 Snap 需要 `WS_THICKFRAME | WS_MAXIMIZEBOX`」；社区高信誉用户 `Castorix31` 进一步指出：
     > "The `WS_THICKFRAME | WS_MAXIMIZEBOX` styles must be set for Snap. To remove caption + frame, with DWM … or by handling `WM_NCCALCSIZE` + `WM_NCHITTEST`"
     来源：https://learn.microsoft.com/en-us/answers/questions/2120539/
   - 这条是**唯一涉及官方平台的权威表述**，它把「Snap 需要 `WS_THICKFRAME`」与「去掉边框用 `WM_NCCALCSIZE` + `WM_NCHITTEST`」**并列**，暗示无边框窗口里 `WM_NCHITTEST` 是**替代/补充**手段。

4. **坐标系统的坑（重要，社区经验）**：使用路线 2 时，你必须确保返回 `HT*` 的**判定区域**与你在 `WM_NCCALCSIZE` 里定义的客户区一致。若 `WM_NCCALCSIZE` 让「客户区 = 整个窗口」，那么窗口边缘那几像素**本来属于客户区**，系统默认会返回 `HTCLIENT` —— **你就必须在 `WM_NCHITTEST` 里主动覆盖成 `HTLEFT` 等**，否则边缘完全无法缩放。这正是路线 2 与路线 1 必须配合的原因。
   参考实现（含完整 `WndProc` 结构、`HT*` 判定）：https://melmanm.github.io/misc/2024/01/10/article13-winforms-custom-window.html

### 5.3 失去 `WS_THICKFRAME` 会丢什么？

| 能力 | 是否依赖 `WS_THICKFRAME` | 依据 |
|---|---|---|
| **系统投影阴影（DWM drop shadow）** | 间接相关。阴影来自 DWM 对**有框架**窗口的处理；完全无边框/无区域定义时 DWM 不画阴影 | 官方文档（圆角文档）指出「设置 `WS_THICKFRAME` 和 `WS_CAPTION`、或提供 1 像素非客户区边框」属于"能被圆角"的类别；实测文章指出无 `WS_THICKFRAME` 时无开合动画与阴影 |
| **Win11 自动圆角** | **是，强依赖之一**。官方明确列出「Lack of frame styles（缺少框架样式）」会导致窗口**不被自动圆角** | 官方：https://github.com/MicrosoftDocs/windows-dev-docs/blob/docs/hub/apps/desktop/modernize/ui/apply-rounded-corners.md （"Apps that provide enough information … such as setting the `WS_THICKFRAME` and `WS_CAPTION` window styles or providing a 1-pixel non-client area border"） |
| **贴靠布局 / Aero Snap（Win+←/→/↑/↓、鼠标贴边）** | **是**。`WS_THICKFRAME` 负责鼠标拖边缩放；`WS_SYSMENU` 负责 Win+←/→；`WS_MAXIMIZEBOX` 负责 Win+↑ 与上边贴靠；`WS_MINIMIZEBOX` 负责 Win+↓ | 实测文章明确列出（社区经验，可信度高，且与 MS Q&A 的 `WS_THICKFRAME \| WS_MAXIMIZEBOX` 表述一致）：https://kubyshkin.name/posts/win32-window-custom-title-bar-caption |
| **窗口开合动画** | 相关 | 同上实测文章：「the window no longer had an opening or closing animation」 |
| **Tile / Cascade 窗口** | 相关 | Chromium 源码注释间接印证（`WS_CAPTION` 与 caption-present 内部状态；见 `is_first_nccalc_` 注释） |
| **拖动尺寸光标在边框外侧出现** | `WS_THICKFRAME` 提供非客户区几何后，系统可在客户区**外侧**也命中缩放 | Electron PR #46703 详述：之前只在最大化时应用 insets，导致非最大化时窗口**外侧**无几何信息、缩放光标只在窗口内侧出现；改动后对 left/bottom/right 应用 insets |

### 5.4 一个重要的实测提醒（对 Win11 尤其相关）

社区实测明确指出：**不要调用 `DwmExtendFrameIntoClientArea`** 来做现代无边框窗口。原文：

> "the good news is that you do not need to call that DWM API to extend the frame into the client area as there is now no visible frame. In fact, **if you do call `DwmExtendFrameIntoClientArea` you will end up with ugly white borders around the window**."

来源：https://kubyshkin.name/posts/win32-window-custom-title-bar-caption

（注意：这条与现代 Win10/11 的「保持 `WS_THICKFRAME`、去边框、不调 DWM」路线一致；它主要适用于 Win10 1703+ 之后。老资料里 `DwmExtendFrameIntoClientArea` + `MARGINS` 的做法在 Win11 上容易出白边。）
另外该文还指出：**标题栏顶部那 1 像素边框，Chrome / Edge / IntelliJ 都是"假画"的**（把最上像素调暗），并非真 border —— 说明这条路上确实没有干净的系统方案。

---

## 6. 针对你项目的可执行建议（仅建议，未改动代码）

按证据强度排序：

1. **先验证成因链 A（最高优先级）**
   在你项目里临时把 `CreateParams` 中追加的 `WS_THICKFRAME` 去掉，看「最小化→还原」是否还膨胀。
   - **若不再膨胀** → 确认是 `AdjustWindowRectEx` 冲突（成因链 A）。
   - 预期结果：去掉后会同时失去拖边缩放与阴影 —— 这就是本问题的核心权衡。

2. **若确认是成因链 A，标准解法（有官方源码依据的社区方案）**
   即 SO 上 Filip Navara 的方案：在 `CreateParams` 里用「只有在 `WM_WINDOWPOSCHANGED` / `SetClientSizeCore` 期间才临时清空边框位」的 hack，让 `AdjustWindowRectEx` 按「无边框」计算：
   ```csharp
   private bool createParamsHack;
   protected override CreateParams CreateParams
   {
       get
       {
           CreateParams cp = base.CreateParams;
           if (createParamsHack)
               cp.Style &= ~(int)(WS_BORDER | WS_CAPTION | WS_DLGFRAME | WS_THICKFRAME);
           return cp;
       }
   }
   // WndProc 里：
   if (m.Msg == WM_WINDOWPOSCHANGED) { createParamsHack = true; base.WndProc(ref m); createParamsHack = false; }
   // 并覆写 SetClientSizeCore 同样加锁。
   ```
   另可覆写 `SizeFromClientSize(Size)` 返回正确值（原答说「非严格必须」）。
   来源：https://stackoverflow.com/questions/20085317/custom-window-frame-with-dwm-how-to-handle-wm-nccalcsize-correctly
   ⚠️ **这属于社区方案，微软未官方背书。** 且它在 .NET 的当前实现里是否仍完全适用，需要你在目标 .NET 版本上实测（因为现代 WinForms 用的是 `SizeFromClientSizeInternal` + `AdjustWindowRectExForControlDpi`，间接依赖 `CreateParams`，hack 思路依然成立）。

3. **`WM_NCCALCSIZE` 一律返回 `0`**（`wParam == TRUE` 时），**不要返回 `WVR_ALIGN*`**。
   如需自定义，只改 `rgrc[0]`（客户区矩形），照搬 Chromium 的「先改 `rgrc[0]`，再决定返回标志」顺序。

4. **参照 Electron 增加还原纠错**：在 `WM_GETMINMAXINFO` 中用 `GetWindowPlacement` / `SetWindowPlacement` 回灌 `rcNormalPosition`；并在做这件事期间**把 `WM_NCCALCSIZE` 交给默认/WebView2 宿主处理**（Electron 注释原文：否则窗口尺寸会错）。
   来源：https://github.com/electron/electron/blob/main/shell/browser/native_window_views_win.cc

5. **`WM_SIZING` / `WM_WINDOWPOSCHANGING` 不是首选**：Chromium 里它们服务于最大化边界与贴靠，**不解决最小化还原膨胀**。别把力气花在这里。

---

## 7. 依据分级汇总

### 有官方 / 权威依据的结论

| # | 结论 | 来源 |
|---|---|---|
| 1 | `WM_NCCALCSIZE` 返回值是「旧客户区像素保留/重绘」策略；返回 0 ⇒ 旧客户区保留并与新客户区**左上角**对齐；返回 0 使客户区扩展到整个窗口（无边框） | MS Learn `WM_NCCALCSIZE`：https://learn.microsoft.com/en-us/windows/win32/winmsg/wm-nccalcsize |
| 2 | `rgrc[0]`（进入=建议窗口矩形，返回=新客户区矩形）、`rgrc[1]`=目标矩形、`rgrc[2]`=源矩形 | MS Learn `NCCALCSIZE_PARAMS`：https://learn.microsoft.com/en-us/windows/win32/api/winuser/ns-winuser-nccalcsize_params |
| 3 | `WVR_ALIGNTOP/LEFT/BOTTOM/RIGHT` 的官方描述主语是 **client area（客户区）**，官方**从未**说它会移动/改变窗口矩形 | 同上 |
| 4 | `WVR_VALIDRECTS` **不能与任何其他标志组合** | 同上 |
| 5 | `WM_NCHITTEST` 返回值语义（`HTLEFT` 等 = 窗口边框命中） | MS Learn：https://learn.microsoft.com/en-us/windows/win32/inputdev/wm-nchittest |
| 6 | `WS_THICKFRAME` = "The window has a sizing border. Same as `WS_SIZEBOX`." | MS Learn：https://learn.microsoft.com/en-us/windows/win32/winmsg/window-styles |
| 7 | 贴靠（Snap）需要 `WS_THICKFRAME \| WS_MAXIMIZEBOX`；去边框用 `WM_NCCALCSIZE` + `WM_NCHITTEST` | MS Learn Q&A（含 MS 外部工程师回复）：https://learn.microsoft.com/en-us/answers/questions/2120539/ |
| 8 | Win11 自动圆角**需要框架样式**（`WS_THICKFRAME`/`WS_CAPTION` 或 1 像素非客户区边框）；缺少框架样式会**不被圆角** | 微软官方文档：https://github.com/MicrosoftDocs/windows-dev-docs/blob/docs/hub/apps/desktop/modernize/ui/apply-rounded-corners.md |
| 9 | `SM_CXSIZEFRAME` = 可调整边框厚度；`SM_CXPADDEDBORDER` = 带标题窗口的边框附加 padding | MS Learn：https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-getsystemmetrics |
| 10 | Chromium：`WM_NCCALCSIZE` 只用 `0` 或 `WVR_REDRAW`，**从不用 `WVR_ALIGN*`**；`WVR_REDRAW` + 非客户区并存会导致客户区像素/子 HWND 错位（源码注释） | Chromium `hwnd_message_handler.cc`：https://source.chromium.org/chromium/chromium/src/+/main:ui/views/win/hwnd_message_handler.cc |
| 11 | Chromium 显式处理「最小化后还原时坐标为 -32000」的时序问题 | 同上（`OnNCCalcSize` 中 `MonitorFromWindow` 回退注释） |
| 12 | Electron 在 `SetWindowPlacement` 期间**让 Chromium 接管 `WM_NCCALCSIZE`**，否则窗口尺寸会错；并在 `WM_GETMINMAXINFO` 用 `SetWindowPlacement` 纠错 | Electron `native_window_views_win.cc`：https://github.com/electron/electron/blob/main/shell/browser/native_window_views_win.cc |
| 13 | Electron `GetClientAreaInsets` 用 `SM_CXSIZEFRAME + SM_CXPADDEDBORDER` 定义 insets，非最大化时仅对 left/right/bottom 生效、top 为 0 | Electron PR #46703：https://github.com/electron/electron/pull/46703 |
| 14 | WinForms `Form.WmWindowPosChanged` → `RestoreWindowBoundsIfNecessary` → `SizeFromClientSizeInternal` → `AdjustWindowRectExForControlDpi` | `dotnet/winforms` `Form.cs` / `Control.cs`（main）：https://github.com/dotnet/winforms/blob/main/src/System.Windows.Forms/System/Windows/Forms/Form.cs 、https://github.com/dotnet/winforms/blob/main/src/System.Windows.Forms/System/Windows/Forms/Control.cs |
| 15 | Raymond Chen 对 `WM_NCCALCSIZE` 的定位：用于确定**客户区在窗口矩形中的位置**；简单形式是「窗口矩形 ↔ 客户区矩形」换算 | The Old New Thing：https://devblogs.microsoft.com/oldnewthing/20030911-00/?p=42553 |
| 16 | `GetWindowPlacement` 返回**工作区坐标**、`SetWindowPos` 用**屏幕坐标**，是历史上窗口"creep"（位置漂移）的经典成因 | The Old New Thing：https://devblogs.microsoft.com/oldnewthing/20030912-00/?p=42543 （注：该文原本想讲 `WM_NCCALCSIZE` 深入语义，但其续篇在本次检索中未定位到；此处引用的是其关于坐标系统陷阱的权威论述） |

### 社区经验、无官方权威依据

| # | 结论 | 来源 | 备注 |
|---|---|---|---|
| a | 「WinForms 的 `AddjustWindowRectEx` 换算无法覆写 → 无边框窗口最大化/还原变大」被定性为 **WinForms bug**，并给出 `CreateParams` hack | SO 20085317（经 2022 镜像页读取）：https://kiwix.presumenothing.net/content/stackoverflow.com_en_all_nopic_2022-07/questions/20085317/… | 作者为 .NET 领域高信誉用户 Filip Navara；**微软官方未承认**为 bug |
| b | Electron「最小化还原后窗口略大/略高」是持续多年的反复回归问题 | electron#22393 / #22440 / #13533 / #7951 / #15702 / #19816 | 症状描述与官方 issue 一致；**修复状态随版本反复**，10.x 曾消失 |
| c | Electron 7.3.3 复现、升级 10.4.7 后消失 | https://blog.csdn.net/chenqk_123/article/details/118417587 | 单一开发者实测 |
| d | `WS_THICKFRAME` → 缩放；`WS_SYSMENU` → Win+←/→；`WS_MAXIMIZEBOX` → Win+↑；`WS_MINIMIZEBOX` → Win+↓；缺失会丢开合动画 | https://kubyshkin.name/posts/win32-window-custom-title-bar-caption | 独立实测文章，与官方 Q&A 表述一致，可信度较高 |
| e | 无边框窗口应**避免**调用 `DwmExtendFrameIntoClientArea`（Win10 1703+ 之后），否则窗口周围出现白边 | 同上 | 独立实测 |
| f | Chrome/Edge/IntelliJ 的顶部 1 像素边框是「假画」的（调暗最上像素） | 同上 | 作者通过对比 Chrome 实际像素得出 |
| g | `WM_NCHITTEST` + `HT*` 的无边框缩放实现细节（判定区域、光标） | https://melmanm.github.io/misc/2024/01/10/article13-winforms-custom-window.html | WinForms 场景可参考 |
| h | 用 `WM_NCCALCSIZE` 返回 0 + 处理 `WM_ERASEBKGND`/`WM_NCPAINT`/`WM_NCACTIVATE` 来消除闪烁抖动 | https://blog.csdn.net/g3030/article/details/104617476 | 中文社区，实测向 |
| i | 无边框窗口「最小化/最大化后不能拖边缩放」往往是还原后 `WM_NCHITTEST` 恒返回 `HTCLIENT`、或 `WS_THICKFRAME` 被意外清除 | https://ask.csdn.net/questions/9331424 | 注：该文含明显 AI 生成痕迹（"四、解决层：三层协同修复方案"等），**仅作线索，勿作依据** |

### 明确「未找到依据」

| 检索项 | 结果 |
|---|---|
| `WVR_ALIGNTOP`/`WVR_ALIGNLEFT` 用错导致窗口膨胀的案例 | **未找到任何**案例、issue 或讨论。且官方文档说明其语义等价于返回 0 的左上对齐，**该假设不成立** |
| `WVR_ALIGN*` 会导致系统移动/改变窗口矩形 | **未找到任何**官方或权威依据 |
| `WVR_ALIGNUP` 这个符号名 | **未找到任何**可靠来源证实其存在。官方名称是 `WVR_ALIGNTOP` |
| `WVR_NOCOPYBITS` 这个符号名 | **未找到任何**可靠来源。官方中对应「不拷贝、整窗重绘」语义的是 `WVR_REDRAW` |
| Raymond Chen 专门论述 `WM_NCCALCSIZE` 返回值/`WVR_*` 的第二篇文章 | 本次检索只定位到 `20030911-00`（Scrollbars part 10，讲简单形式与客户区矩形换算），**未能定位**其续篇。上表中关于返回值的权威论述均来自 MS Learn 官方文档 |
| kEngage / Notepad-- / Windows Terminal 的 `WM_NCCALCSIZE` 具体实现 | 本次检索**未覆盖**这三个具体项目（时间/检索预算集中在 Chrome/Electron 与 WinForms 源码）。如需，可另开一轮 |

---

## 8. 对你的问题的直接回答（逐条）

**1. 这个经典问题的成因与标准解法？**
成因链有两条：(A) **WinForms 专属** —— `Form` 在 `WM_WINDOWPOSCHANGED` 里用 `AdjustWindowRectEx` 做「客户区→窗口」换算，而该函数只看 `CreateParams` 的样式位（你加了 `WS_THICKFRAME`），完全无视你对 `WM_NCCALCSIZE` 的处理，于是每轮补偿一个边框厚度。(B) **通用 Win32** —— `WM_NCCALCSIZE` / 返回值 / 还原时序处理不当。Chrome/Electron 的解法是：只改 `rgrc[0]`、返回 `0` 或 `WVR_REDRAW`、永不使用 `WVR_ALIGN*`，并在 `WM_GETMINMAXINFO` 里做还原纠错（Electron）。**你的症状与 (A) 高度吻合。**

**2. Chrome/Electron 怎么处理的？**
`WM_NCCALCSIZE`：先改 `rgrc[0]`，再返回 `0`（最大化、insets 左/上为 0 时）或 `WVR_REDRAW`（其余情况），**从不返回 `WVR_ALIGN*`**。`WM_GETMINMAXINFO`：Chromium 把客户区尺寸约束显式换算为窗口约束；Electron 额外用 `GetWindowPlacement`/`SetWindowPlacement` 回灌 `rcNormalPosition` 来修「还原后尺寸/位置错」。`WM_WINDOWPOSCHANGING`：用于最大化边界纠错。`WM_SIZE`：显式检测最小化状态变化并通知上层。**它们没有用任何 `WVR_ALIGN*` 补偿。**

**3. `WVR_*` 的确切语义？返回 0 还是 `WVR_*`？`WVR_ALIGN*` 会移动窗口吗？`ALIGNTOP|ALIGNLEFT` 会导致膨胀吗？**
语义见 §2.2 表格（官方原文逐条）。返回 **0**。**查不到 `WVR_ALIGN*` 会移动/改变窗口矩形的任何依据**。**查不到 `ALIGNTOP|ALIGNLEFT` 导致膨胀的任何依据**，且官方文档表明它与返回 0 的「左上对齐」语义等价，**该假设不成立**。

**4. 是否有 `WVR_ALIGNUP`/`WVR_ALIGNLEFT` 用错的膨胀案例？**
**找不到。** 且 `WVR_ALIGNUP` / `WVR_NOCOPYBITS` 这两个符号名在本次检索中**找不到任何可靠来源**（官方名是 `WVR_ALIGNTOP`；「不拷贝」语义对应官方 `WVR_REDRAW`）。

**5. 不用 `WS_THICKFRAME` 的替代路线？是否依赖？会丢什么？**
三条路线见 §5.1。`WM_NCHITTEST` 返回 `HT*` **不依赖** `WS_THICKFRAME`（官方文档未建立此依赖），但你必须自己覆盖 `WM_NCHITTEST` 的边缘判定，并可能需自行处理 `WM_SIZING`。失去 `WS_THICKFRAME` 会丢：**系统投影阴影、Win11 自动圆角（官方明确列为「缺少框架样式」的情形）、贴靠布局（Snap/Win+方向键）、窗口开合动画、以及客户区外侧的缩放光标命中**。其中 **Win11 圆角与贴靠有官方依据**（§5.3）。

---

## 附：本次调研的检索关键词（供复核）

- `WinForms borderless WS_THICKFRAME window grows on minimize restore WM_NCCALCSIZE`
- `WM_NCCALCSIZE WVR_ALIGNTOP WVR_ALIGNLEFT return value window resize msdn`
- `C# frameless window bigger after minimize restore WS_THICKFRAME resize border`
- `Chromium views HWNDMessageHandler WM_NCCALCSIZE WVR_ALIGNTOP window grow restore bug`
- `"WM_NCCALCSIZE" return "WVR_ALIGNTOP" OR "WVR_ALIGNLEFT" window grows larger each restore`
- `Raymond Chen The Old New Thing WM_NCCALCSIZE non-client area client area`
- `WS_THICKFRAME required WM_NCHITTEST HTLEFT resize hit test without thickframe not work`
- `DwmExtendFrameIntoClientArea CS_DROPSHADOW shadow WS_THICKFRAME required Win11 rounded corners snap layouts`
- `WPF WindowChrome ResizeBorderThickness frameless window grows on restore github issue`
- `Electron frameless window Windows WM_NCCALCSIZE bug window grows minimize restore github electron issue`
- `"WVR_ALIGNUP" OR "WVR_NOCOPYBITS" WM_NCCALCSIZE legacy old name Windows 3.1 undocumented`
- `WinForms FormBorderStyle.None WS_THICKFRAME window size increases after minimize restore AdjustWindowRectEx SizeFromClientSize bug`

**源码核验清单（均已在本次调研中实际下载并逐行阅读）**
- `chromium/ui/views/win/hwnd_message_handler.cc`（`OnNCCalcSize` L2524–2625；`GetClientAreaInsets` L1758–1785；`OnGetMinMaxInfo` L2157–2183；`OnWindowPosChanging` L3109+；`OnSize` L2968–2978）
- `electron/shell/browser/native_window_views_win.cc`（`PreHandleMSG` 中 `WM_NCCALCSIZE` 与 `WM_GETMINMAXINFO` 分支）
- `dotnet/winforms` `Form.cs`（`WmWindowPosChanged`、`RestoreWindowBoundsIfNecessary`、`ComputeWindowSize`）、`Control.cs`（`SizeFromClientSizeInternal`、`SizeFromClientSize`、`SetClientSizeCore`）
