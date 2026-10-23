# OpenList TV

[English](README.md) | 简体中文

独立的 OpenList 文件浏览器与播放器，面向 Android TV 设计，同时支持手机横屏触摸操作。
最低 Android 6.0 / API 23，具有电视和普通 Android 桌面入口。与 OpenList 及各云盘厂商无隶属关系。

- [Android 与云盘兼容性](docs/COMPATIBILITY.md)
- [安全、兼容性与界面审查及修复记录](docs/REVIEW-2026-10-23.md)
- [自动构建](https://github.com/yukunwu/openlist-player/actions/workflows/android.yml)
- [版本下载](https://github.com/yukunwu/openlist-player/releases)

## 语言

应用支持简体中文和英文，跟随 Android 系统语言；其他语言回退为英文。手机扫码填写页面跟随浏览器语言。
Media3 自带播放控件使用其内置翻译。云盘文件名、服务器返回的内容不会被翻译。

## 功能

- 手动填写 OpenList 地址、用户名、密码与可选两步验证码，也可访客浏览。没有内置服务器或账号，登录表单默认留空。
- 手机扫描一次性局域网二维码填写配置，发送后必须在电视确认，再点击登录；不需要安装配套手机应用。
- 使用 OpenList API 分页浏览目录、获取 `raw_url` 播放地址；不是 WebDAV 客户端，也不是网页套壳。
- 点击视频默认使用内置 Media3 播放器；长按文件行或长按遥控器确定键，选择内部或外部播放器。
- 播放信息栏显示视频/音频信息；右下角菜单提供原始比例、4:3、16:9、拉伸铺满和播放速度。
- 手机在空白画面左侧竖滑调节当前窗口亮度，右侧竖滑调节媒体音量，无需长按，滑动不弹出播放控制栏。
- 手机画面锁定仅显示图标，2.5 秒后隐藏；触碰画面或按返回键暂时显示锁图标，再点击可见图标解锁。
- 电视遥控器确定键播放/暂停，左右快退/快进 10 秒，上下进入控制栏导航。返回依次关闭菜单、隐藏控制栏、退出播放。
- 没有广告、遥测、内置云盘账号或 NAS 转码功能。84dp 窄侧栏、中性黑灰半透明界面、白色焦点与红色进度。

## 登录与隐私

用户名、密码和验证码仅用于填写与登录请求，不持久化密码。登录成功后保存服务器地址，使用 Android Keystore
和 AES-GCM 加密保存授权 token，并绑定对应服务器。再次打开应用可以恢复会话；有效期由 OpenList 决定，
目前没有自动刷新 token。退出账号删除本地 token，不删除云盘文件，也不调用服务端撤销接口。

应用禁用备份与设备迁移；登录和配对窗口禁止截图。推荐使用只读影视账号，优先使用 HTTPS。
应用允许显式配置 HTTP 地址，不仅限于局域网；HTTP 不加密。HTTPS 不忽略证书验证。
已 root 或被入侵的设备、恶意输入法不在本应用的安全保证范围内。

## 播放策略与限制

`raw_url` 可能是云盘/CDN 直链，也可能是 OpenList 配置的代理地址。应用不强制直链，不自动修改服务端设置。
显示的播放来源只是初始主机名，不能证明后续跳转或流量没有经过代理。

- **移动云盘**：用户确认直链播放，另有此前网页/WebDAV 的 CDN 跳转与 Range 验证。
- **天翼云盘**：用户确认可播放；具体直链或代理路径尚未核实。
- **其他云盘**：尚未逐一验证。挂载成功不代表 App 一定可播，仍受鉴权、限速、URL 有效期及解码能力限制。
- **需额外请求头的源**：请使用 OpenList 已有的 Web Proxy 配置，由服务器处理 Cookie、Referer 等；客户端不调用需要管理员权限的链接接口。
- **HLS/DASH**：已集成对应模块，清单、分片和密钥必须通过 HTTP(S) 可访问；没有配置 DRM 许可证服务。
- **跳转**：内部播放器跨源不附带原源额外请求头，拒绝 HTTPS 降级 HTTP。OpenList 登录 token 不自动发给云盘。
- **拖动**：使用 HTTP Range 并校验偏移与长度；源忽略 Range 时需读取并跳过前面字节，大文件可能缓慢、耗流量。
- **过期链接**：返回文件库重新打开可获取新地址；播放器“重试”只重试当前 URL。
- **外部播放器**：会接收临时签名播放链接，需选择可信应用；不向它传递 OpenList 会话 token。

4K、HEVC、HDR、AV1、杜比与音频直通取决于设备系统解码器、具体格式和带宽，不承诺所有设备支持。
原始比例保持片源比例；4:3、16:9 与拉伸铺满可能改变画面形状。亮度手势仅修改当前播放窗口，不修改系统全局亮度。
播放器处理媒体音频焦点，并在拔耳机等音频输出变化时暂停。尚无海报刮削、目录密码、外挂字幕搜索或持久播放历史。

## 手机扫码配置

在电视打开 **服务器 → 连接服务器 / 登录 → 手机扫码填写配置**。手机与电视连接同一可信局域网，
用浏览器扫码，核对配对标识，填写后发送，再回到电视确认配置并登录。

配对服务仅在配对页面存在，绑定一个私网 IPv4 地址及随机端口，最长 3 分钟、只接受一次提交。
离开页面或到期会关闭监听和活动连接。访客网络隔离、VPN、纯 IPv6 网络或浏览器局域网权限可能影响连接。

二维码包含临时 AES-GCM 密钥，不含账号密码；密钥位于 URL fragment，浏览器读取后会清理地址栏中的 fragment。
提交负载加密并校验会话、Origin、Host 和长度，不加载第三方资源。但**配对表单通过局域网 HTTP 提供，不能防止主动中间人篡改页面**。
公共网络请使用电视手动输入；不要拍照分享二维码。

浏览器加密使用 node-forge 1.4.0 的 AES 精简构建，不包含 RSA、ASN.1 或 PKI，随机 IV 使用浏览器
`crypto.getRandomValues`。完整源依赖仍有 RSA 漏洞公告，不能将精简构建称为上游已修复。
重建方式与边界见 `scripts/crypto/README.md`，许可证保留在 `app/src/main/assets/forge-LICENSE.txt`。

## 本地构建

使用 JDK 17、Android SDK platform 35，在忽略的 `local.properties` 配置 SDK 路径：

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

测试包：`app/build/outputs/apk/debug/app-debug.apk`。

正式构建从 `~/.config/openlist-tv/signing.properties` 读取签名，或通过 `OPENLIST_SIGNING_PROPERTIES` 指定文件。
配置项为 `storeFile`（keystore 绝对路径）、`storePassword`、`keyAlias`、`keyPassword`。不提供配置时生成未签名包。
不要提交配置、密码或私钥；请单独安全备份。

```sh
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

签名包：`app/build/outputs/apk/release/app-release.apk`。启用 R8 与资源压缩，非调试版本。
升级必须复用同一证书并递增 `versionCode`。正式签名与本地 debug 签名不同，不能直接覆盖；不要为了安装擅自卸载或清数据。

## GitHub 自动构建与签名

更新 `main`、提交 PR、推送 `v*` 标签或手动运行都会执行测试、Lint 和编译。
在成功运行的 **Artifacts** 中下载，保留 14 天：

- `openlist-tv-apks`：测试包与优化后的未签名包。CI debug 证书可能随运行变化，不保证覆盖其他安装。
- `openlist-tv-signed-release`：仅可信 `main` 或版本标签构建生成，包含稳定正式证书签名的可安装 APK、证书指纹和 SHA-256 校验值。

推送版本标签会自动发布签名产物到 GitHub Releases。发布前修改 `versionName`、递增 `versionCode`，再推送匹配标签，例如 `v0.1.10`。
没有额外配置必需检查、分支保护或审批门禁。

私钥及密码保存在 Actions Secrets：`ANDROID_KEYSTORE_BASE64`、`ANDROID_STORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。
Base64 只是编码，加密存储由 GitHub Secrets 提供。独立签名任务不检出或执行仓库源码，PR 不运行该任务；
keystore 仅临时写入 runner，签名后删除，不进缓存或产物。必须信任能修改可信工作流的仓库维护者，Secrets 不能防止恶意授权工作流泄密。
公开 fork 必须配置自己的私钥，不能签发本项目的更新。如果上架 Google Play，可以使用 Play App Signing 和独立上传密钥。

## 验证范围

构建、单元测试、Lint、签名验证不等于所有设备通过。已有 Android 16 小米手机安装、目录浏览和部分视频播放记录，
以及真实浏览器到 Java 的配对加解密测试。真实 TV 遥控器、4K、全部比例/手势、耳机断开与音频焦点仍需完整设备回归。
本次新增语言资源与配对页面的中英文检查；不能据此宣称所有屏幕尺寸或大字号布局均已验收。

## 安装

```sh
adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <device> shell am start -n site.openlist.player/.MainActivity
```

电视和普通桌面入口均名为 **OpenList TV**，打开同一个横屏界面。仅使用网络、网络状态与媒体音量权限，
不需要 root 或 Google Play Services。设计原型与生图参考位于 `design/`，不是实际功能或真机截图证明。
