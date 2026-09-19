# 摸薪 Mocent 桌面端构建脚本（Git Bash / Windows）
# 用法: bash desktop/build.sh        → dist/Mocent/（可直接运行）+ dist/Mocent-v*-windows-x64.zip
#       bash desktop/build.sh msi    → 追加生成安装包（WiX3，位置 .tools/wix3/wix 或系统安装）
#
# 依赖：系统 csc.exe（.NET Framework 4.x 自带）+ WebView2 SDK（.tools/webview2，见下方路径）
#       下载 SDK: curl -o wv2.nupkg https://api.nuget.org/v3-flatcontainer/microsoft.web.webview2/1.0.2903.40/microsoft.web.webview2.1.0.2903.40.nupkg && unzip 到 .tools/webview2/pkg
set -e
cd "$(dirname "$0")/.."

CSC="/c/Windows/Microsoft.NET/Framework64/v4.0.30319/csc.exe"
WV2=".tools/webview2/pkg"
OUT="dist"
VER=$(grep -oP 'APP_VERSION = "\K[0-9.]+' desktop/src/Program.cs | head -1)

rm -rf "$OUT/Mocent"
mkdir -p "$OUT/Mocent"

echo "== 1/3 编译 Mocent.exe（$VER）=="
"$CSC" -nologo -target:winexe -platform:x64 -optimize+ \
    -out:"$(cygpath -w "$OUT/Mocent/Mocent.exe")" \
    -win32icon:desktop/mocent.ico \
    -win32manifest:desktop/app.manifest \
    -r:"$(cygpath -w "$WV2/lib/net462/Microsoft.Web.WebView2.Core.dll")" \
    -r:"$(cygpath -w "$WV2/lib/net462/Microsoft.Web.WebView2.WinForms.dll")" \
    "$(cygpath -w desktop/src/Program.cs)"

echo "== 2/3 组装运行文件 =="
cp "$WV2/lib/net462/Microsoft.Web.WebView2.Core.dll"      "$OUT/Mocent/"
cp "$WV2/lib/net462/Microsoft.Web.WebView2.WinForms.dll"  "$OUT/Mocent/"
cp "$WV2/runtimes/win-x64/native/WebView2Loader.dll"      "$OUT/Mocent/"
cp -r web "$OUT/Mocent/web"

echo "== 3/3 压缩便携版 =="
cd "$OUT"
rm -f "Mocent-v$VER-windows-x64.zip"
powershell -NoProfile -Command "Compress-Archive -Path Mocent -DestinationPath 'Mocent-v$VER-windows-x64.zip' -Force"
cd ..

if [ "$1" = "msi" ]; then
    WIXBIN=$(ls -d "/c/Program Files (x86)/WiX Toolset"*/bin 2>/dev/null | head -1)
    [ -z "$WIXBIN" ] && [ -f ".tools/wix3/wix/candle.exe" ] && WIXBIN="$(pwd)/.tools/wix3/wix"
    if [ -z "$WIXBIN" ]; then echo "!! 未找到 WiX Toolset，跳过 MSI"; exit 0; fi
    echo "== 生成 MSI 安装包（WiX: $WIXBIN）=="
    EXE_W=$(cygpath -w "$PWD/$OUT/Mocent/Mocent.exe")
    CORE_W=$(cygpath -w "$PWD/$OUT/Mocent/Microsoft.Web.WebView2.Core.dll")
    FORMS_W=$(cygpath -w "$PWD/$OUT/Mocent/Microsoft.Web.WebView2.WinForms.dll")
    LOADER_W=$(cygpath -w "$PWD/$OUT/Mocent/WebView2Loader.dll")
    ICO_W=$(cygpath -w "$PWD/desktop/mocent.ico")
    WEB_W=$(cygpath -w "$PWD/$OUT/Mocent/web")
    cat > "$OUT/mocent.wxs" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<Wix xmlns="http://schemas.microsoft.com/wix/2006/wi">
  <Product Id="*" Name="摸薪 Mocent" Language="2052" Codepage="65001" Version="$VER" Manufacturer="IMMKF3" UpgradeCode="C7A5E690-1B2C-4D8E-9F0A-3E4D5C6B7A81">
    <Package InstallerVersion="200" Compressed="yes" InstallScope="perUser" Description="摸薪 Mocent 桌面版"/>
    <MajorUpgrade AllowDowngrades="yes"/>
    <MediaTemplate EmbedCab="yes"/>
    <Directory Id="TARGETDIR" Name="SourceDir">
      <Directory Id="LocalAppDataFolder">
        <Directory Id="AppRoot" Name="Mocent">
          <Directory Id="INSTALLDIR" Name="app">
            <Directory Id="WebDir" Name="web"/>
          </Directory>
        </Directory>
      </Directory>
      <Directory Id="ProgramMenuFolder">
        <Directory Id="MenuDir" Name="摸薪">
          <Component Id="MenuSc" Guid="9F3B7C21-5A4E-4D68-8B2F-1C0D9E7A6B54">
            <Shortcut Id="MocentSc" Name="摸薪 Mocent" Description="摸鱼的时候，工资也在涨" Target="[INSTALLDIR]Mocent.exe" WorkingDirectory="INSTALLDIR" Icon="appico"/>
            <RemoveFolder Id="RmMenuDir" On="uninstall"/>
            <RegistryValue Root="HKCU" Key="Software\Mocent" Name="shortcut" Type="integer" Value="1" KeyPath="yes"/>
          </Component>
        </Directory>
      </Directory>
    </Directory>
    <DirectoryRef Id="INSTALLDIR">
      <Component Id="Exe" Guid="2E8D4F16-7C3B-4A59-9D21-6B8A0F5C4E73">
        <File Id="MocentExe" Source="$EXE_W"/>
        <RemoveFile Id="RmAppFiles" Directory="INSTALLDIR" Name="*" On="uninstall"/>
        <RemoveFolder Id="RmAppDir" Directory="INSTALLDIR" On="uninstall"/>
        <RemoveFolder Id="RmAppRoot" Directory="AppRoot" On="uninstall"/>
        <RemoveFile Id="RmWebFiles" Directory="WebDir" Name="*" On="uninstall"/>
        <RemoveFolder Id="RmWebDir" Directory="WebDir" On="uninstall"/>
        <RegistryValue Root="HKCU" Key="Software\Mocent" Name="exe" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="Core" Guid="3F9E5027-8D4C-4B6A-AE32-7C9B106D5F84">
        <File Id="CoreDll" Source="$CORE_W"/>
        <RegistryValue Root="HKCU" Key="Software\Mocent" Name="core" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="Forms" Guid="4A0F6138-9E5D-4C7B-BF43-8D0C217E6095">
        <File Id="FormsDll" Source="$FORMS_W"/>
        <RegistryValue Root="HKCU" Key="Software\Mocent" Name="forms" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="Loader" Guid="5B107249-0F6E-4D8C-A054-9E1D328F71A6">
        <File Id="LoaderDll" Source="$LOADER_W"/>
        <RegistryValue Root="HKCU" Key="Software\Mocent" Name="loader" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
    </DirectoryRef>
    <DirectoryRef Id="WebDir">
      <Component Id="WebIdx" Guid="6C21835A-107F-4E9D-B165-0F2E439082B7">
        <File Id="WebIndex" Source="$WEB_W/index.html"/>
        <RegistryValue Root="HKCU" Key="Software\Mocent" Name="web_index" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="WebMan" Guid="7D32946B-2180-4FAE-C276-1A3F54A193C8">
        <File Id="WebManifest" Source="$WEB_W/manifest.webmanifest"/>
        <RegistryValue Root="HKCU" Key="Software\Mocent" Name="web_manifest" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="WebReg" Guid="8E43A57C-3291-40BF-D387-2B4065B2A4D9">
        <File Id="WebRegions" Source="$WEB_W/regions.json"/>
        <RegistryValue Root="HKCU" Key="Software\Mocent" Name="web_regions" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="Ico192" Guid="9F54B68D-43A2-41C0-E498-3C5176C3B5EA">
        <File Id="Icon192" Source="$WEB_W/icon-192.png"/>
        <RegistryValue Root="HKCU" Key="Software\Mocent" Name="icon_192" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="Ico512" Guid="A065C79E-54B3-42D1-F5A9-4D6287D4C6FB">
        <File Id="Icon512" Source="$WEB_W/icon-512.png"/>
        <RegistryValue Root="HKCU" Key="Software\Mocent" Name="icon_512" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="Ico180" Guid="B176D8AF-65C4-43E2-06BA-5E7398E5D70C">
        <File Id="Icon180" Source="$WEB_W/icon-180.png"/>
        <RegistryValue Root="HKCU" Key="Software\Mocent" Name="icon_180" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
      <Component Id="IcoDark" Guid="C287E9B0-76D5-44F3-17CB-6F84A9F6E81D">
        <File Id="IconDark" Source="$WEB_W/icon-dark-192.png"/>
        <RegistryValue Root="HKCU" Key="Software\Mocent" Name="icon_dark" Type="integer" Value="1" KeyPath="yes"/>
      </Component>
    </DirectoryRef>
    <Icon Id="appico" SourceFile="$ICO_W"/>
    <Feature Id="Default" Level="1">
      <ComponentRef Id="MenuSc"/><ComponentRef Id="Exe"/><ComponentRef Id="Core"/><ComponentRef Id="Forms"/>
      <ComponentRef Id="Loader"/><ComponentRef Id="WebIdx"/><ComponentRef Id="WebMan"/><ComponentRef Id="WebReg"/>
      <ComponentRef Id="Ico192"/><ComponentRef Id="Ico512"/><ComponentRef Id="Ico180"/><ComponentRef Id="IcoDark"/>
    </Feature>
  </Product>
</Wix>
EOF
    (cd "$OUT" && "$WIXBIN/candle.exe" -nologo mocent.wxs -o mocent.wixobj) &&     (cd "$OUT" && "$WIXBIN/light.exe" -nologo mocent.wixobj -o "Mocent-v$VER-windows-x64-installer.msi" -ext WixUtilExtension)
    rm -f "$OUT/mocent.wxs" "$OUT/mocent.wixobj" "$OUT/mocent.wixpdb"
    echo "完成: $OUT/Mocent-v$VER-windows-x64-installer.msi"
fi
