# 列出 MSI 里真实存在的表与行数（用 WiX DTF 托管 API，不走 COM 实例化）
#
# 为什么需要这个：light 生成的 MSI 用 `dark.exe` 反编译时**不会输出 Dialog 表**
# （Dialog 行数恒为 0，连纯 WixUI_FeatureTree 基线也一样），看着像 MSI 坏了，其实
# 是 dark.exe 的工具缺陷。要确认 UI 流程、快捷方式、Feature 树是否真的进了 MSI，
# 用这个脚本查 DTF 可见的表最靠谱。
#
# 前置：.tools/wix3/wix/Microsoft.Deployment.WindowsInstaller.dll 存在（见 desktop/build.sh 的 WiX 定位逻辑）
# 用法：powershell -ExecutionPolicy Bypass -File desktop/dump-msi-tables.ps1 -MsiPath dist/Mocent-v*-installer.msi

param(
  [Parameter(Mandatory=$true)][string]$MsiPath
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot  = Split-Path -Parent $scriptDir
$dtfDir    = Join-Path $repoRoot '.tools\wix3\wix'

$dtfDll = Join-Path $dtfDir 'Microsoft.Deployment.WindowsInstaller.dll'
if (-not (Test-Path $dtfDll)) {
  Write-Error "缺少 $dtfDll —— 先按 desktop/build.sh 里的说明解压 wix314-binaries.zip 到 .tools/wix3/wix/"
  exit 1
}
Add-Type -Path $dtfDll -ErrorAction Stop

$db = New-Object Microsoft.Deployment.WindowsInstaller.Database($MsiPath, [Microsoft.Deployment.WindowsInstaller.DatabaseOpenMode]::ReadOnly)

Write-Output ("=== " + $MsiPath + " ===")
$allTables = @()
foreach ($t in $db.Tables) { $allTables += $t.Name }
Write-Output ("TABLE_COUNT=" + $allTables.Count)
Write-Output ("TABLES=" + ($allTables -join ', '))
Write-Output ""

# 与安装向导 / 快捷方式 / 功能树相关的关键表
foreach ($name in @('Dialog', 'Control', 'ControlEvent', 'UIText', 'TextStyle',
                    'InstallUISequence', 'AdminUISequence', 'WixUI', 'WixSimpleReference',
                    'Feature', 'FeatureComponents', 'Component', 'Shortcut',
                    'Directory', 'Registry', 'Property')) {
  if ($allTables -contains $name) {
    $view = $db.OpenView("SELECT * FROM ``$name``")
    $view.Execute()
    $n = 0
    while ($null -ne $view.Fetch()) { $n++ }
    $view.Close()
    Write-Output ("ROWS $name = $n")
  } else {
    Write-Output ("ROWS $name = TABLE_ABSENT")
  }
}

Write-Output ""
Write-Output "=== 对话框 id 列表（验证安装向导流程）==="
if ($allTables -contains 'Dialog') {
  $view = $db.OpenView('SELECT `Dialog` FROM `Dialog`')
  $view.Execute()
  while ($null -ne ($rec = $view.Fetch())) { Write-Output ("  " + $rec.GetString(1)) }
  $view.Close()
}

Write-Output ""
Write-Output "=== 快捷方式（验证桌面 / 开始菜单）==="
if ($allTables -contains 'Shortcut') {
  $view = $db.OpenView('SELECT `Shortcut`, `Directory_`, `Target` FROM `Shortcut`')
  $view.Execute()
  while ($null -ne ($rec = $view.Fetch())) {
    Write-Output ("  " + $rec.GetString(1) + "  dir=" + $rec.GetString(2) + "  target=" + $rec.GetString(3))
  }
  $view.Close()
}

Write-Output ""
Write-Output "=== Feature 树（验证可选快捷方式功能）==="
if ($allTables -contains 'Feature') {
  $view = $db.OpenView('SELECT `Feature`, `Feature_Parent`, `Title`, `Level` FROM `Feature`')
  $view.Execute()
  while ($null -ne ($rec = $view.Fetch())) {
    $parent = if ($rec.IsNull(2)) { '(root)' } else { $rec.GetString(2) }
    Write-Output ("  " + $rec.GetString(1) + "  parent=" + $parent + "  title=" + $rec.GetString(3) + "  level=" + $rec.GetString(4))
  }
  $view.Close()
}

$db.Dispose()
Write-Output ""
Write-Output "完成。"
