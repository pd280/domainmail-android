# Hy4email Android · Rust + GitHub Actions

给 [ifwqu/domainmail](https://github.com/ifwqu/domainmail)（自建域名邮箱服务器）做的安卓客户端。

**业务逻辑 100% 用 Rust 写**，Android 端只是一层 Compose 渲染壳；**APK 完全由 GitHub Actions 构建**，本地不需要装 Android SDK 也能出包。

---

## 先回答：能做到吗

能。已经按下面的结构完整落地了：

| 层 | 语言 | 职责 |
|---|---|---|
| `crates/core` | **Rust** | HTTP/REST、`dm_session` Cookie 会话、错误映射、查询串构造、发信体规范化、附件 Base64 与落盘、会话持久化、53 个接口分发 |
| `crates/core/src/ffi.rs` | **Rust** | JNI 导出（只有 **1 个** native 方法），panic 拦截，日志 |
| `cli` | **Rust** | 桌面验证工具，连真实服务器跑通接口，不依赖 Android |
| `android/app` | Kotlin + Compose | 只做 UI 渲染与状态，零网络代码 |
| `.github/workflows` | YAML | `cargo test` / `clippy` / `fmt` + `cargo-ndk` 交叉编译 + `assembleRelease` + 签名 + Release 附件 |

Android 平台规定必须有一个 `Activity` 才能有窗口，所以不可能「一行 Kotlin 都没有」；但这里 Kotlin 不碰协议、不碰网络、不碰存储——所有请求都通过 `RustBridge.call("方法名", JSON)` 进 Rust。

---

## 架构

```
┌─────────────────────────────────────────────────────────┐
│  Jetpack Compose (Kotlin)                                │
│  登录 / 列表 / 详情 / 写信 / 别名 / 设置                  │
│       │  RustBridge.call(method, json) → json            │
├───────┼─────────────────────────────────────────────────┤
│       ▼  JNI: Java_com_ifwqu_domainmail_RustBridge_...  │
│  domainmail-core (Rust cdylib, libdomainmail_core.so)    │
│   ├ api.rs      dispatch(method)  → 53 个 REST 接口      │
│   ├ client.rs   reqwest + rustls + dm_session Cookie     │
│   ├ session.rs  token 持久化（不存密码）                 │
│   ├ model.rs    宽松取值 / 查询参数                       │
│   └ util.rs     URL 编解码 / Content-Disposition          │
├─────────────────────────────────────────────────────────┤
│  DomainMail 服务端 :8080  （53 个 REST / IMAP / SMTP）    │
└─────────────────────────────────────────────────────────┘
```

**为什么走 REST 而不是 IMAP/SMTP**：服务端已经把 MIME 解析、DKIM、反垃圾、别名展开、分页搜索都做完了，REST 拿到的是结构化 JSON；自己再实现一遍 IMAP 客户端纯属重复劳动，还容易在中文主题 / QP / Base64 附件上翻车。`/api/client-config` 会在设置页给出 IMAP/SMTP 参数，需要 Foxmail 之类直连时照抄即可。

---

## 目录

```
.
├── Cargo.toml                     # workspace: core + cli
├── crates/core/
│   ├── Cargo.toml
│   ├── src/{lib,api,client,session,model,util,error,ffi}.rs
│   └── tests/api_test.rs          # 内置假 HTTP 服务，无需外网
├── cli/src/main.rs                # 桌面 CLI
├── android/
│   ├── build.gradle.kts / settings.gradle.kts / gradle.properties
│   └── app/
│       ├── build.gradle.kts       # 签名回落逻辑
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/ifwqu/domainmail/*.kt
│           ├── java/com/ifwqu/domainmail/ui/Screens.kt
│           └── res/{values,values-night,xml}/
├── .github/workflows/
│   ├── ci.yml                     # fmt / clippy / test
│   └── build-apk.yml              # cargo-ndk → Gradle → 签名 APK
└── README.md
```

---

## 接口覆盖（对照 `src/web/routes.js` 逐字段核对过）

| Rust method | HTTP | 路径 |
|---|---|---|
| `login` / `logout` / `me` / `register` / `changePassword` / `registerConfig` | POST/GET | `/api/auth/*` |
| `folders` / `messages` / `message` / `rawMessage` / `attachment` | GET | `/api/folders`, `/api/messages*`, `/api/attachments/:id` |
| `setFlags` / `markRead` / `markUnread` / `star` / `unstar` | POST | `/api/messages/:id/flags` |
| `moveMessage` / `deleteMessage` / `emptyFolder` | POST/DELETE | `/api/messages/:id/*`, `/api/folders/:f/empty` |
| `send` | POST | `/api/send` |
| `mailboxes` / `aliases` / `createAlias` / `createAliasesBatch` / `updateAlias` / `deleteAlias` / `availableDomains` | GET/POST/PUT/DELETE | `/api/mailboxes`, `/api/aliases*`, `/api/domains/available` |
| `settings` / `setForward` / `setAutoresponder` / `setProfile` / `clientConfig` | GET/POST | `/api/settings*`, `/api/client-config` |
| `adminStats` / `adminDomains` / `adminUsers` / `adminAliases` / `adminOutbox` / `adminAudit` / `adminConfig` / `adminRetryOutbox` / `adminDeleteOutbox` | GET/POST/DELETE | `/api/admin/*` |

几个容易踩坑、已经在 Rust 侧处理好的细节：

- 登录响应是 `Set-Cookie: dm_session=<encodeURIComponent(token)>`，要解码后存；后续每个请求手动带 `Cookie: dm_session=...`（不启用 reqwest cookie store，完全可控）。
- 错误体统一是 `{"error":"..."}`，`401` 要清本地会话并映射成 `unauthorized`，App 自动回登录页。
- `date` 是毫秒时间戳，可能为 `null`（`Date.parse` NaN）；模型用宽松取值，不会因为一个 `null` 整包解析失败。
- `SYSTEM_FOLDERS = INBOX/Sent/Drafts/Trash/Junk`，中文名由 Rust 的 `folderLabels` 统一给。
- `/api/send` 的 `to/cc/bcc` 是**字符串**（服务端按 `[,;\s]+` 切分），Rust 把数组自动 join 成逗号串；附件 `content` 必须是 Base64，Rust 支持直接给本地文件路径，自己读文件并编码，不把大字节数组搬过 JNI。
- 附件 / `.eml` 由 Rust 直接写到 `filesDir/downloads`，JNI 只回一个小 JSON（路径/文件名/MIME/大小），Android 用 `FileProvider` 交给系统应用打开。
- 邮箱切换器：`?mailbox=别名@域名` 会同时影响邮件列表、文件夹计数和发件人身份。

---

## 快速开始

### 1. 服务端

按 domainmail 的 README 部署好，`http://mail.example.com:8080` 能打开 Webmail 即可。
如果用的是自签证书，App 登录页勾上「允许自签证书 / 不安全 TLS」；生产环境强烈建议挂 Nginx + Let's Encrypt（服务端 `config.json` 的 `tls` 段配证书）。

### 2. 先用 CLI 验证接口（推荐）

不需要 Android SDK，纯 Rust，10 秒就能确认服务器和账号是通的：

```bash
export DOMAINMAIL_URL=https://mail.example.com:8080
export DOMAINMAIL_PASSWORD='你的密码'

cargo run -p domainmail-cli -- login you@example.com
cargo run -p domainmail-cli -- me
cargo run -p domainmail-cli -- folders
cargo run -p domainmail-cli -- messages --folder INBOX --limit 20
cargo run -p domainmail-cli -- --insecure messages --unread
cargo run -p domainmail-cli -- message 9
cargo run -p domainmail-cli -- send --to bob@example.com --subject 你好 --text 正文 --attach ./a.txt
cargo run -p domainmail-cli -- mailboxes
cargo run -p domainmail-cli -- --help
```

会话存在 `~/.domainmail-cli/session.json`，只含 `dm_session`，不含密码。

### 3. 跑核心测试（不需要服务器、不需要网络）

```bash
cargo test --workspace
cargo clippy --workspace --all-targets
cargo fmt --all -- --check
```

`tests/api_test.rs` 内置了一个用 `std::net::TcpListener` 写的假 DomainMail 服务，验证：Cookie 捕获与复用、冷启动会话恢复、查询串编码、发信体规范化与附件 Base64、401 错误映射、二进制下载落盘、IMAP flag 名称等。

### 4. 本地出 APK

前置：Rust + `cargo-ndk`、Android SDK / NDK 26、JDK 17、Gradle 8.9。

```bash
cargo install cargo-ndk
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 \
  -o android/app/src/main/jniLibs \
  build --release -p domainmail-core

cd android
gradle assembleRelease --no-daemon
# 产物：app/build/outputs/apk/release/app-release.apk
```

本机若没有 `gradle` 命令，装好 Gradle 8.9 后执行一次 `gradle wrapper --gradle-version 8.9` 生成 wrapper，之后就能用 `./gradlew`。仓库里不放 `gradle-wrapper.jar` 二进制；CI 由 `setup-gradle` 的 `gradle-version: '8.9'` 下载 Gradle 并加入 PATH，直接调用 `gradle assembleRelease`。

---

## GitHub 构建

推到 GitHub 后两个 workflow 自动跑：

**`ci.yml`**（push / PR）
`cargo fmt --check` → `cargo clippy --workspace --all-targets` → `cargo test --workspace` → CLI `--help` 冒烟。

**`build-apk.yml`**（push / PR / tag / 手动触发）

1. `dtolnay/rust-toolchain` 装三个 Android target；
2. `cargo install cargo-ndk` 装交叉编译工具（不用 install-action：它不支持 cargo-ndk，会 fallback 到受 GitHub API 限流影响的 cargo-binstall）；
3. 直接用 ubuntu-latest 预装的 Android SDK + `sdkmanager` 装 NDK 26.3.11579264 / platform 35 / build-tools 35.0.0（不用 `setup-android@v3`：它在新版 runner 上会因找不到旧 `tools` 包而失败）；
4. `cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o .../jniLibs build --release -p domainmail-core`；
5. `gradle assembleRelease`；
6. `apksigner verify` 验签 → 上传 artifact `domainmail-android-apk`；
7. 打 `v*` tag 时自动建 Release 并附上 APK。

**签名与覆盖安装**：CI 每次都是全新 runner，Gradle 回落的 `debug.keystore` 会逐次随机生成，签名不一致 → 手机无法覆盖安装。所以 workflow 从仓库 Secrets 恢复**同一把固定密钥**（PKCS12），并注入 `DM_VERSION_CODE=github.run_number` 让 versionCode 单调递增：

```
DM_KEYSTORE_B64        # openssl 生成的 release.p12 的 base64
DM_KEYSTORE_PASSWORD
DM_KEY_ALIAS
DM_KEY_PASSWORD
```

`Restore fixed signing key` 步骤把 secret 解码到 `$RUNNER_TEMP` 再交给 Gradle；`build.gradle.kts` 里 `storeType = "PKCS12"` 必须显式声明，否则 p12 会被当成 JKS 解析失败。**私钥不进仓库**，仅在 Actions Secrets 里。本地不注入这些变量时回落到 debug key，仅供本机调试。

> 注意：换用新签名密钥后，第一次从旧版（v0.2.0 及之前）升级仍需**先卸载一次**——旧包是随机 debug key 签的。从这次发版起，后续版本之间就能直接覆盖安装了。

**应用名与图标**：App 显示名为 **Hy4email**（`res/values/strings.xml` 的 `app_name`）。`applicationId` 保持 `com.ifwqu.domainmail` 不变，改名不影响签名与覆盖安装。

图标使用用户提供的表情包形象（深棕波波头、大眼睛、红围巾、浅蓝背景）：

- 源图经居中裁切为 432×432 PNG，放在 `branding/mascot.png`；由于 Git Data API 只推文本，仓库里保存的是 `branding/mascot.png.base64`。
- workflow 的 `Decode launcher mascot` 步骤把它解码到 `res/drawable-xxxhdpi/mascot.png`（432px @ 4x = 108dp，正好是 adaptive icon foreground 画布尺寸）。
- `mipmap-anydpi-v26/ic_launcher*.xml` 的 foreground 与 `mipmap/ic_launcher*.xml` 回退图标都引用 `@drawable/mascot`；`ic_launcher_background` 取原图四角采样色 `#E1F7FC`，避免视差时露出色差。
- 顶栏品牌位 `BrandMark` 通过 `painterResource(R.drawable.mascot)` 复用同一张图，启动图标与界面顶部是同一个形象。

### 只用 GitHub API 跑完全程（推荐）

`tools/github_build.py` 是纯标准库的 REST 驱动脚本，**本机不执行任何编译命令**：

```bash
export GITHUB_TOKEN=ghp_xxx                       # 权限：repo + workflow + actions:read
export GITHUB_REPO=owner/domainmail-android      # 可选，不填自动建 <用户名>/domainmail-android
export GITHUB_API=https://api.github.com          # 可选，自建 / 代理端点

python tools/github_build.py all                 # push → dispatch → 等结果 → 打印每个 step → 列 artifact
```

子命令：`push` / `dispatch` / `status --run-id N` / `logs --run-id N` / `artifacts --run-id N`；失败时自动下载完整日志 zip。
脚本用 Git Data API 一次性 create-tree 提交全部文本文件，不需要本地 git、`gradle-wrapper.jar` 或任何 SDK。

---

## 安全与实现取舍

- **密码不落盘**：只持久化 `dm_session` token，文件权限 `0600`（Unix），App 沙箱再隔离一层。
- **TLS 用 rustls**（纯 Rust，无 OpenSSL），Android 交叉编译零原生库依赖；`danger_accept_invalid_certs` 只在用户显式勾选「自签证书」时打开。
- **WebView 渲染 HTML 邮件时禁用 JS、禁用文件访问、阻断远程资源加载**，降低追踪像素与本地文件读取风险。
- **JNI 边界 catch_unwind**：Rust 侧任何 panic 都转成 `{"ok":false,"error":{"kind":"panic",...}}`，不会炸掉进程。
- **响应原样透传**：`dispatch` 返回服务端原始 JSON，服务端以后加字段 App 不用重新发版。

---

## 已知边界

- 首版用轮询 + 手动刷新，没有接 `IDLE` / 推送（服务端 IMAP 支持 `IDLE`，但 REST 没有推送通道；后续可做 FCM 或轮询优化）。
- 草稿箱目前是服务端文件夹展示，App 内没有本地草稿自动保存。
- 管理后台接口已接好（管理员账号可用），UI 只做了个人设置页，没做管理后台界面。
- `armeabi-v7a` 与 `x86_64`（模拟器）都出包；纯 64 位机器只装 `arm64-v8a` 的 so 也完全没问题。

---

## 许可

MIT（与 domainmail 一致）。
