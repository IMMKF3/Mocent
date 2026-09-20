# 摸薪 Mocent 桌面端构建脚本（Git Bash / Windows）
# 用法: bash desktop/build.sh        → dist/Mocent/（可直接运行）+ dist/Mocent-v*-windows-x64.zip
#       bash desktop/build.sh msi    → 追加生成安装包（WiX3，位置 .tools/wix3/wix 或系统安装）
#
# 依赖：系统 csc.exe（.NET Framework 4.x 自带）+ WebView2 SDK（.tools/webview2，见下方路径）
#       下载 SDK: curl -o wv2.nupkg https://api.nuget.org/v3-flatcontainer/microsoft.web.webview2/1.0.2903.40/microsoft.web.webview2.1.0.2903.40.nupkg && unzip 到 .tools/webview2/pkg
set -e
cd "$(dirname "$0")/.."

# 本机 Git Bash 可能缺 coreutils（dirname/head/grep 报 command not found），
# 优先用系统 csc 编译；csc 不可用时回退到 dotnet SDK 编译（产物同样可用）。
CSC="/c/Windows/Microsoft.NET/Framework64/v4.0.30319/csc.exe"
DOTNET="/c/Program Files/dotnet/dotnet.exe"
WV2=".tools/webview2/pkg"
OUT="dist"

# 版本号提取：不依赖 grep -oP（部分环境没有），改用 sed
VER=$(sed -n 's/.*APP_VERSION = "\([^"]*\)".*/\1/p' desktop/src/Program.cs | sed -n '1p')
[ -z "$VER" ] && { echo "!! 未能从 Program.cs 提取 APP_VERSION"; exit 1; }
# 版本号可能带空格（如预发布期的 "1.4.0 Beta 1"）：文件名用下划线版，MSI 的 Version 只认纯数字段。
# 正式版号（如 "1.4.0"）下这两步是恒等变换，不留痕迹，所以统一走同一套逻辑、不分支。
VER_SAFE=$(echo "$VER" | tr ' ' '_')
VER_NUM=$(echo "$VER" | cut -d' ' -f1)

rm -rf "$OUT/Mocent"
mkdir -p "$OUT/Mocent"

if [ -x "$CSC" ]; then
    echo "== 1/3 编译 Mocent.exe（$VER，csc / .NET Framework）=="
    "$CSC" -nologo -target:winexe -platform:x64 -optimize+ \
        -out:"$(cygpath -w "$OUT/Mocent/Mocent.exe")" \
        -win32icon:desktop/mocent.ico \
        -win32manifest:desktop/app.manifest \
        -r:"$(cygpath -w "$WV2/lib/net462/Microsoft.Web.WebView2.Core.dll")" \
        -r:"$(cygpath -w "$WV2/lib/net462/Microsoft.Web.WebView2.WinForms.dll")" \
        "$(cygpath -w desktop/src/Program.cs)"
else
    echo "== 1/3 csc 不可用，回退 dotnet SDK 编译（$VER）=="
    [ -x "$DOTNET" ] || { echo "!! 既没有 csc.exe 也没有 dotnet，无法编译"; exit 1; }
    "$DOTNET" build desktop/Mocent.csproj -c Release -v q --nologo
    cp "desktop/bin/Release/net8.0-windows/Mocent.exe" "$OUT/Mocent/Mocent.exe"
fi

echo "== 2/3 组装运行文件 =="
cp "$WV2/lib/net462/Microsoft.Web.WebView2.Core.dll"      "$OUT/Mocent/"
cp "$WV2/lib/net462/Microsoft.Web.WebView2.WinForms.dll"  "$OUT/Mocent/"
cp "$WV2/runtimes/win-x64/native/WebView2Loader.dll"      "$OUT/Mocent/"
cp -r web "$OUT/Mocent/web"

echo "== 3/3 压缩便携版 =="
cd "$OUT"
rm -f "Mocent-v$VER_SAFE-windows-x64.zip"
powershell -NoProfile -Command "Compress-Archive -Path Mocent -DestinationPath 'Mocent-v$VER_SAFE-windows-x64.zip' -Force"
cd ..

if [ "$1" = "msi" ]; then
    # WiX 定位顺序：环境变量 > 系统安装 > 本机 .tools（gitignore，不入库）
    WIXBIN=""
    [ -n "$WIX" ] && [ -f "$WIX/candle.exe" ] && WIXBIN="$WIX"
    if [ -z "$WIXBIN" ]; then
        SYSWIX=$(ls -d "/c/Program Files (x86)/WiX Toolset"*/bin 2>/dev/null | sed -n '1p')
        [ -n "$SYSWIX" ] && WIXBIN="$SYSWIX"
    fi
    [ -z "$WIXBIN" ] && [ -f ".tools/wix3/wix/candle.exe" ] && WIXBIN="$(pwd)/.tools/wix3/wix"
    if [ -z "$WIXBIN" ]; then
        echo "!! 未找到 WiX Toolset，跳过 MSI"
        echo "   获取方式：https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip"
        echo "   解压到 .tools/wix3/wix/ 即可（或用 WIX=/path/to/wix 指定）"
        exit 0
    fi
    # 安装包选项：perMachine（默认，可选目录 + 全机可用） / perUser
    SCOPE="${2:-perMachine}"
    # Win64：只有装到 Program Files 才需要标记 64 位组件（ICE80 会校验一致性）
    # REG_ROOT：快捷键组件的 KeyPath 必须跟随安装范围
    #           （装到用户目录→HKCU；装到 Program Files→HKLM，否则 ICE38/43/57 报错）
    # PLATFORM：Package 的 Platform 属性，x64 版本必须显式声明，否则 ICE80 报
    #           "64 bit component but Template Summary Property does not contain Intel64"
    if [ "$SCOPE" = "perUser" ]; then
        DIR_ROOT="LocalAppDataFolder"
        SCOPE_ATTR="perUser"
        PLATFORM="x86"
        WIN64=""
        REG_ROOT="HKCU"
    else
        DIR_ROOT="ProgramFiles64Folder"
        SCOPE_ATTR="perMachine"
        PLATFORM="x64"
        WIN64=' Win64="yes"'
        REG_ROOT="HKLM"
    fi
    echo "== 4/4 生成 MSI 安装包（WiX: $WIXBIN，安装范围: $SCOPE）=="

    # WiX 的 WixUI_InstallDir 流程要求定义 WixUILicenseRtf，且必须是 RTF 格式。
    # 仓库里没有现成 rtf，这里按需生成一份（已存在则不覆盖，方便自定义）。
    LIC="$OUT/license.rtf"
    if [ ! -f "$LIC" ]; then
        cat > "$LIC" <<'RTF'
{\rtf1\ansi\ansicpg936\deff0{\fonttbl{\f0\fnil\fcharset134 Microsoft YaHei;}}
\viewkind4\uc1\pard\qc\b\f0\fs32 摸薪 Mocent 最终用户许可协议\b0\fs20\par
\par
\pard\ql\fs18
\par
1. 授权范围\par
本软件为个人免费软件。您可以在个人设备上自由安装、使用、复制与分发本软件，无需支付任何费用。\par
\par
2. 使用限制\par
您不得将本软件用于任何违反中华人民共和国法律法规的活动；不得删除或修改本软件中的版权声明与署名信息。\par
\par
3. 数据与隐私\par
本软件为纯本地运行工具，所有工资、社保、个税等计算数据均仅在您的设备本地存储与处理，\par
不会上传至任何服务器，也不包含任何统计、埋点或遥测代码。\par
\par
4. 免责声明\par
本软件的社保、个税计算结果仅供参考，不构成任何税务、财务或法律建议。\par
实际应缴金额请以当地社保经办机构、税务主管部门的正式口径为准。\par
因使用本软件产生的任何直接或间接损失，作者不承担责任。\par
\par
5. 开源许可\par
本软件基于仓库中声明的开源许可协议发布，详见发行包内的 LICENSE 文件。\par
\par
\pard\qc\fs18 点击「下一步」即表示您已阅读并同意上述条款。\par
}
RTF
    fi
    LIC_W=$(cygpath -w "$PWD/$LIC")

    EXE_W=$(cygpath -w "$PWD/$OUT/Mocent/Mocent.exe")
    CORE_W=$(cygpath -w "$PWD/$OUT/Mocent/Microsoft.Web.WebView2.Core.dll")
    FORMS_W=$(cygpath -w "$PWD/$OUT/Mocent/Microsoft.Web.WebView2.WinForms.dll")
    LOADER_W=$(cygpath -w "$PWD/$OUT/Mocent/WebView2Loader.dll")
    ICO_W=$(cygpath -w "$PWD/desktop/mocent.ico")
    WEB_W=$(cygpath -w "$PWD/$OUT/Mocent/web")
    cat > "$OUT/mocent.wxs" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<!-- 摸薪 Mocent 安装包定义（由 desktop/build.sh 生成，请勿手改）
     三项交互式安装选项：
       1) 自定义安装目录        —— WixUI_InstallDir 的 InstallDirDlg + WIXUI_INSTALLDIR
       2) 创建桌面快捷方式      —— DesktopShortcut 功能（取消勾选即不装）
       3) 创建开始菜单快捷方式  —— StartMenuShortcut 功能（取消勾选即不装）
     后两项通过 CustomizeDlg 的功能树复选框呈现，默认都勾选。 -->
<Wix xmlns="http://schemas.microsoft.com/wix/2006/wi">
  <Product Id="*" Name="摸薪 Mocent" Language="2052" Codepage="65001" Version="$VER_NUM" Manufacturer="IMMKF3" UpgradeCode="C7A5E690-1B2C-4D8E-9F0A-3E4D5C6B7A81">
    <Package InstallerVersion="500" Compressed="yes" InstallScope="$SCOPE_ATTR" Platform="$PLATFORM"
             Description="摸薪 Mocent 桌面版 - 摸鱼的时候，工资也在涨"/>

    <!-- 允许降级安装（便于测试旧版本）。
         注意 MajorUpgrade 没有 Maximum 属性；要限制版本需另外写 UpgradeVersion，
         这里不需要，ICE61 只是告警不影响功能。 -->
    <MajorUpgrade AllowDowngrades="yes" Schedule="afterInstallValidate"/>
    <MediaTemplate EmbedCab="yes"/>

    <!-- 让用户能选择安装位置：属性名必须与下面的 INSTALLDIR 目录 Id 一致 -->
    <Property Id="WIXUI_INSTALLDIR" Value="INSTALLDIR"/>
    <Property Id="ARPCOMMENTS" Value="摸鱼的时候，工资也在涨"/>
    <Property Id="ARPPRODUCTICON" Value="appico"/>

    <!-- 注：TextStyle 不是 Product 的子元素，见文件末尾的 Fragment -->

    <Directory Id="TARGETDIR" Name="SourceDir">
      <Directory Id="$DIR_ROOT">
        <Directory Id="CompanyDir" Name="摸薪">
          <Directory Id="INSTALLDIR" Name="Mocent">
            <Directory Id="WebDir" Name="web"/>
          </Directory>
        </Directory>
      </Directory>
      <Directory Id="ProgramMenuFolder">
        <Directory Id="MenuDir" Name="摸薪"/>
      </Directory>
      <Directory Id="DesktopFolder"/>
    </Directory>

    <!-- 开始菜单快捷方式：独立组件，由 StartMenuShortcut 功能控制装与不装 -->
    <DirectoryRef Id="MenuDir">
      <Component Id="MenuSc" Guid="9F3B7C21-5A4E-4D68-8B2F-1C0D9E7A6B54"$WIN64>
        <Shortcut Id="MocentSc" Name="摸薪 Mocent" Description="摸鱼的时候，工资也在涨"
                  Target="[INSTALLDIR]Mocent.exe" WorkingDirectory="INSTALLDIR" Icon="appico"/>
        <RemoveFolder Id="RmMenuDir" On="uninstall"/>
        <RegistryValue Root="HKCU" Key="Software\Mocent" Name="shortcut_menu" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
    </DirectoryRef>

    <!-- 桌面快捷方式：独立组件，由 DesktopShortcut 功能控制装与不装 -->
    <DirectoryRef Id="DesktopFolder">
      <Component Id="DeskSc" Guid="E4A90B11-98F7-4A15-39ED-81A6BC18A03F"$WIN64>
        <Shortcut Id="MocentDeskSc" Name="摸薪 Mocent" Description="摸鱼的时候，工资也在涨"
                  Target="[INSTALLDIR]Mocent.exe" WorkingDirectory="INSTALLDIR" Icon="appico"/>
        <RegistryValue Root="HKCU" Key="Software\Mocent" Name="shortcut_desktop" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
    </DirectoryRef>

    <DirectoryRef Id="INSTALLDIR">
      <Component Id="Exe" Guid="2E8D4F16-7C3B-4A59-9D21-6B8A0F5C4E73"$WIN64>
        <File Id="MocentExe" Source="$EXE_W"/>
        <RemoveFile Id="RmAppFiles" Directory="INSTALLDIR" Name="*" On="uninstall"/>
        <RemoveFolder Id="RmAppDir" Directory="INSTALLDIR" On="uninstall"/>
        <RemoveFolder Id="RmCompanyDir" Directory="CompanyDir" On="uninstall"/>
        <RemoveFile Id="RmWebFiles" Directory="WebDir" Name="*" On="uninstall"/>
        <RemoveFolder Id="RmWebDir" Directory="WebDir" On="uninstall"/>
        <RegistryValue Root="$REG_ROOT" Key="Software\Mocent" Name="exe" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="Core" Guid="3F9E5027-8D4C-4B6A-AE32-7C9B106D5F84"$WIN64>
        <File Id="CoreDll" Source="$CORE_W"/>
        <RegistryValue Root="$REG_ROOT" Key="Software\Mocent" Name="core" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="Forms" Guid="4A0F6138-9E5D-4C7B-BF43-8D0C217E6095"$WIN64>
        <File Id="FormsDll" Source="$FORMS_W"/>
        <RegistryValue Root="$REG_ROOT" Key="Software\Mocent" Name="forms" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="Loader" Guid="5B107249-0F6E-4D8C-A054-9E1D328F71A6"$WIN64>
        <File Id="LoaderDll" Source="$LOADER_W"/>
        <RegistryValue Root="$REG_ROOT" Key="Software\Mocent" Name="loader" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
    </DirectoryRef>

    <DirectoryRef Id="WebDir">
      <Component Id="WebIdx" Guid="6C21835A-107F-4E9D-B165-0F2E439082B7"$WIN64>
        <File Id="WebIndex" Source="$WEB_W/index.html"/>
        <RegistryValue Root="$REG_ROOT" Key="Software\Mocent" Name="web_index" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="WebMan" Guid="7D32946B-2180-4FAE-C276-1A3F54A193C8"$WIN64>
        <File Id="WebManifest" Source="$WEB_W/manifest.webmanifest"/>
        <RegistryValue Root="$REG_ROOT" Key="Software\Mocent" Name="web_manifest" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="WebReg" Guid="8E43A57C-3291-40BF-D387-2B4065B2A4D9"$WIN64>
        <File Id="WebRegions" Source="$WEB_W/regions.json"/>
        <RegistryValue Root="$REG_ROOT" Key="Software\Mocent" Name="web_regions" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="Ico192" Guid="9F54B68D-43A2-41C0-E498-3C5176C3B5EA"$WIN64>
        <File Id="Icon192" Source="$WEB_W/icon-192.png"/>
        <RegistryValue Root="$REG_ROOT" Key="Software\Mocent" Name="icon_192" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="Ico512" Guid="A065C79E-54B3-42D1-F5A9-4D6287D4C6FB"$WIN64>
        <File Id="Icon512" Source="$WEB_W/icon-512.png"/>
        <RegistryValue Root="$REG_ROOT" Key="Software\Mocent" Name="icon_512" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="Ico180" Guid="B176D8AF-65C4-43E2-06BA-5E7398E5D70C"$WIN64>
        <File Id="Icon180" Source="$WEB_W/icon-180.png"/>
        <RegistryValue Root="$REG_ROOT" Key="Software\Mocent" Name="icon_180" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="IcoDark" Guid="C287E9B0-76D5-44F3-17CB-6F84A9F6E81D"$WIN64>
        <File Id="IconDark" Source="$WEB_W/icon-dark-192.png"/>
        <RegistryValue Root="$REG_ROOT" Key="Software\Mocent" Name="icon_dark" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="IcoRound" Guid="D398FAEE-87E6-4504-28DC-7095AB07F92E"$WIN64>
        <File Id="IconRound" Source="$WEB_W/icon-rounded.png"/>
        <RegistryValue Root="$REG_ROOT" Key="Software\Mocent" Name="icon_round" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
    </DirectoryRef>

    <Icon Id="appico" SourceFile="$ICO_W"/>

    <!-- 必需组件：主程序 + 全部 Web 资源，无条件安装 -->
    <Feature Id="Main" Title="摸薪 Mocent 主程序" Description="核心程序与界面资源（必需）"
             Level="1" Absent="disallow" AllowAdvertise="no" Display="expand" InstallDefault="local">
      <ComponentRef Id="Exe"/><ComponentRef Id="Core"/><ComponentRef Id="Forms"/><ComponentRef Id="Loader"/>
      <ComponentRef Id="WebIdx"/><ComponentRef Id="WebMan"/><ComponentRef Id="WebReg"/>
      <ComponentRef Id="Ico192"/><ComponentRef Id="Ico512"/><ComponentRef Id="Ico180"/><ComponentRef Id="IcoDark"/><ComponentRef Id="IcoRound"/>
      <Feature Id="DesktopShortcut" Title="创建桌面快捷方式" Description="在桌面放置「摸薪 Mocent」图标"
               Level="1" Absent="allow" AllowAdvertise="no" InstallDefault="local" TypicalDefault="install">
        <ComponentRef Id="DeskSc"/>
      </Feature>
      <Feature Id="StartMenuShortcut" Title="创建开始菜单快捷方式" Description="在开始菜单「摸薪」文件夹中创建快捷方式"
               Level="1" Absent="allow" AllowAdvertise="no" InstallDefault="local" TypicalDefault="install">
        <ComponentRef Id="MenuSc"/>
      </Feature>
    </Feature>

    <!-- ============================================================
         界面流程：欢迎 → 许可 → 功能选择 → 安装目录 → 确认 → 进度 → 完成

         为什么不用 WixUI_InstallDir？
           本机 WiX 3.14.1 内置的 VerifyReadyDlg 引用了 TextStyle「WixUI_Font_Emphasized」，
           但 3.14 的源码 schema 已经把 TextStyle 元素删掉了（candle 直接报
           CNDL0005 unexpected child element 'TextStyle'；.wxl 里也只允许 String/UI），
           所以只要 UIRef 了 WixUI_InstallDir 就一定撞 ICE31，且无解。
           WixUI_FeatureTree 不含 VerifyReadyDlg.Font_Emphasized 引用，可以正常链接。
           因此以 FeatureTree 为底座（它自带功能复选页 CustomizeDlg），
           再把「安装目录」页 InstallDirDlg 及配套的 BrowseDlg 跳转手工接进流程。
         ============================================================ -->
    <UI Id="MocentUI">
      <UIRef Id="WixUI_FeatureTree"/>
      <UIRef Id="WixUI_ErrorProgressText"/>
      <!-- 引入 InstallDirDlg / BrowseDlg / InvalidDirDlg 等对话框定义 -->
      <DialogRef Id="InstallDirDlg"/>
      <DialogRef Id="BrowseDlg"/>
      <DialogRef Id="InvalidDirDlg"/>

      <!-- 更改目录按钮：把 [WIXUI_INSTALLDIR] 交给浏览对话框（缺这条会报 ICE17 Do Nothing button） -->
      <Publish Dialog="InstallDirDlg" Control="ChangeFolder" Property="_BrowseProperty" Value="[WIXUI_INSTALLDIR]" Order="1">1</Publish>
      <Publish Dialog="InstallDirDlg" Control="ChangeFolder" Event="SpawnDialog" Value="BrowseDlg" Order="2">1</Publish>

      <!-- 浏览对话框确定后回写路径 -->
      <Publish Dialog="BrowseDlg" Control="OK" Event="SetTargetPath" Value="[WIXUI_INSTALLDIR]" Order="1">1</Publish>
      <Publish Dialog="BrowseDlg" Control="OK" Event="DoAction" Value="WixUIValidatePath" Order="2">NOT WIXUI_DONTVALIDATEPATH</Publish>
      <Publish Dialog="BrowseDlg" Control="OK" Event="SpawnDialog" Value="InvalidDirDlg" Order="3">NOT WIXUI_DONTVALIDATEPATH AND WIXUI_INSTALLDIR_VALID&lt;&gt;"1"</Publish>
      <Publish Dialog="BrowseDlg" Control="OK" Event="EndDialog" Value="Return" Order="4">WIXUI_DONTVALIDATEPATH OR WIXUI_INSTALLDIR_VALID="1"</Publish>

      <!-- 功能选择页「下一步」：去安装目录页（FeatureTree 原行为是直接去 VerifyReadyDlg，Order=2 覆盖它） -->
      <Publish Dialog="CustomizeDlg" Control="Next" Event="NewDialog" Value="InstallDirDlg" Order="2">1</Publish>

      <!-- 安装目录页：确定路径后去确认页；「上一步」回功能选择页 -->
      <Publish Dialog="InstallDirDlg" Control="Next" Event="SetTargetPath" Value="[WIXUI_INSTALLDIR]" Order="1">1</Publish>
      <Publish Dialog="InstallDirDlg" Control="Next" Event="DoAction" Value="WixUIValidatePath" Order="2">NOT WIXUI_DONTVALIDATEPATH</Publish>
      <Publish Dialog="InstallDirDlg" Control="Next" Event="SpawnDialog" Value="InvalidDirDlg" Order="3">NOT WIXUI_DONTVALIDATEPATH AND WIXUI_INSTALLDIR_VALID&lt;&gt;"1"</Publish>
      <Publish Dialog="InstallDirDlg" Control="Next" Event="NewDialog" Value="VerifyReadyDlg" Order="4">WIXUI_DONTVALIDATEPATH OR WIXUI_INSTALLDIR_VALID="1"</Publish>
      <Publish Dialog="InstallDirDlg" Control="Back" Event="NewDialog" Value="CustomizeDlg" Order="1">NOT Installed</Publish>

      <!-- 确认页「上一步」：回安装目录页（覆盖 FeatureTree 内置的 → CustomizeDlg） -->
      <Publish Dialog="VerifyReadyDlg" Control="Back" Event="NewDialog" Value="InstallDirDlg" Order="1">NOT Installed</Publish>
    </UI>

    <!-- 许可协议文本（LicenseAgreementDlg 需要，必须是 Product 的子元素） -->
    <WixVariable Id="WixUILicenseRtf" Value="$LIC_W"/>
  </Product>
</Wix>
EOF

    # 中文化文案必须放在独立的 .wxl 里，用 light -loc 引入（WiX3 不支持内联 WixLocalization）
    cat > "$OUT/mocent.zh-CN.wxl" <<'WXL'
<?xml version="1.0" encoding="utf-8"?>
<WixLocalization Culture="zh-CN" Codepage="936" xmlns="http://schemas.microsoft.com/wix/2006/localization">
  <String Id="CustomizeDlg_Title">{\WixUI_Font_Title}安装选项</String>
  <String Id="CustomizeDlgDescription">勾选或取消下面的安装选项，然后点击「下一步」。</String>
  <String Id="CustomizeDlgText">点击左侧图标可切换各项的安装方式；取消勾选的项目不会被安装。</String>
  <String Id="CustomizeDlgBannerBitmap">WixUI_Bmp_Banner</String>
  <String Id="CustomizeDlgItemDescription">[ItemDescription]</String>
  <String Id="CustomizeDlgItemSize">[ItemSize]</String>
  <String Id="CustomizeDlgLocation">[ItemLocation]</String>
  <String Id="InstallDirDlg_Title">{\WixUI_Font_Title}选择安装位置</String>
  <String Id="InstallDirDlgDescription">安装程序将把摸薪 Mocent 安装到下面的文件夹。若要更换位置，请点击「更改」。</String>
  <String Id="InstallDirDlgFolderLabel">将摸薪 Mocent 安装到：</String>
  <String Id="InstallDirDlgChange">&amp;更改...</String>
  <String Id="CustomizeDlgReset">重新(&amp;S)设置</String>
  <String Id="DiskCostDlg_Title">{\WixUI_Font_Title}磁盘空间要求</String>
  <String Id="FeaturesDlg_Title">{\WixUI_Font_Title}安装选项</String>
  <String Id="FeaturesDlgDescription">请选择要安装的项目。</String>
  <String Id="LicenseAgreementDlgTitle">{\WixUI_Font_Title}最终用户许可协议</String>
  <String Id="LicenseAgreementDlgDescription">请阅读下面的许可协议。</String>
  <String Id="VerifyReadyDlgInstallTitle">{\WixUI_Font_Title}准备安装摸薪 Mocent</String>
  <String Id="VerifyReadyDlgInstallText">点击「安装」开始安装。若要检查或更改设置，请点击「上一步」。</String>
  <String Id="VerifyReadyDlgInstall">{\WixUI_Font_Title}安装(&amp;I)</String>
  <String Id="ExitDialogTitle">{\WixUI_Font_Bigger}已完成摸薪 Mocent 安装向导</String>
  <String Id="ExitDialogDescription">点击「完成」退出安装程序。</String>
  <String Id="ProgressDlg_Title">{\WixUI_Font_Title}正在安装摸薪 Mocent</String>
  <String Id="ProgressDlgTextInstalling">安装程序正在安装摸薪 Mocent。请稍候。</String>
  <String Id="WelcomeDlgTitle">{\WixUI_Font_Bigger}欢迎使用摸薪 Mocent 安装向导</String>
  <String Id="WelcomeDlgDescription">安装向导将引导您完成摸薪 Mocent 的安装。点击「下一步」继续。</String>
</WixLocalization>
WXL
    # 必须转成绝对路径：light 的工作目录在 dist/ 内，相对路径会被二次拼接成 dist/dist/...
    WXL_W=$(cygpath -w "$PWD/$OUT/mocent.zh-CN.wxl")

    # 每个 .wxs 必须单独编译成 .wixobj（candle 不支持多源文件共用一个 -o）
    (cd "$OUT" && "$WIXBIN/candle.exe" -nologo -ext WixUIExtension mocent.wxs -o mocent.wixobj) || {
        echo "!! candle 编译失败"; exit 1; }
    (cd "$OUT" && "$WIXBIN/light.exe" -nologo mocent.wixobj \
        -o "Mocent-v$VER_SAFE-windows-x64-installer.msi" \
        -ext WixUIExtension -ext WixUtilExtension -cultures:zh-CN -loc "$WXL_W" -b "$(cygpath -w "$PWD/$OUT")") || {
        echo "!! light 链接失败"; exit 1; }
    # 清理所有中间产物。
    # 注意：light -o 会按输出名自动生成同名的 .wixpdb 调试库（Mocent-v*.wixpdb），
    #       名字不是 mocent.wixpdb，必须按实际名字删，否则 dist/ 会残留调试符号。
    rm -f "$OUT"/mocent.wxs "$OUT"/mocent.wixobj \
          "$OUT"/mocent.fonts.wxs "$OUT"/mocent.fonts.wixobj \
          "$OUT/Mocent-v$VER_SAFE-windows-x64-installer.wixpdb"
    echo "完成: $OUT/Mocent-v$VER_SAFE-windows-x64-installer.msi"
fi
