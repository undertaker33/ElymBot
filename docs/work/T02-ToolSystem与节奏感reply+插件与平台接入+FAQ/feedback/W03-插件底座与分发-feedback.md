# W03 · 插件底座与分发 Feedback

## 实际结果

- App JNI 打包启用 legacy extraction，保证原生组件安装到 `nativeLibraryDir`。未添加 ABI 过滤器；App 的 `minSdk` 仍为 26。
- 按只读核对过的 ON-04 ARM64 输入，仅加入三项 QQ 容器启动组件：PRoot、loader 和 PRoot 的 talloc 依赖。每个文件的字节数与 SHA-256 用于固定来源；三项均与 ON-04 制品清单中的对应条目匹配。

  | App 原生文件 | 字节数 | SHA-256 |
  | --- | ---: | --- |
  | `app/src/main/jniLibs/arm64-v8a/libproot.so` | 214536 | `3682a2c88663477a1704463b4fd838c157bc11a731c417068ec126801e003c8c` |
  | `app/src/main/jniLibs/arm64-v8a/libloader.so` | 5608 | `05e04d87632546506eb03b6c72428f5f0cf5c989d4a629d2648c40a4bf67d45f` |
  | `app/src/main/jniLibs/arm64-v8a/liblibtalloc.so.2.so` | 31128 | `5f191acfd274e3bda67808d6df514f1f97fbd5c8b5b0d11e734ca04da36487ff` |

- 新增 App 内部 `ProotGuestLauncher`。每次启动重新读取安装后的原生库目录，直接执行其中的 proot，使用该目录中的 loader，并在私有临时目录建立 talloc SONAME 链接。固定 PRoot 命令只在 guest rootfs 中执行经过路径校验的 `/bin/bash` 脚本；不接收宿主可执行路径或任意宿主命令。guest rootfs 限定在宿主传入的 App 私有插件目录内。
- 启动结果由 `ProotGuestProcess` 持有。消费者可等待退出或停止进程；关闭句柄时清理临时库目录，临时目录仍被进程使用时保留。宿主进程合并并丢弃 guest stdout/stderr，不留存原始运行输出；W04 从插件受控状态文件读取进度。
- 未把 `libbash.so`、`libbusybox.so`、2 字节的 `libsudo.so`、rootfs、QQ/NapCat 资产或插件代码加入基础 App。未改 Manifest、Application、Core、QQ 插件或 UI；`ProotGuestLauncher` 保持 `internal`，由 W02 的 App 桥接在交付宿主授权目录后调用，W04 QQ 运行时持有返回的进程句柄。

## 验证

| Checklist 项 | 方法或命令 | 平台或工具版本 | 实际结果 | 仓库相对证据 |
| --- | --- | --- | --- | --- |
| `T02.05 A06`（部分静态证据） | 对照本地 ON-04 输入与制品清单，核对三项长度和 SHA-256；静态检查 JNI 打包与启动边界 | Windows 工作区；ON-04 ARM64 制品为历史验证输入 | 三项输入与归档制品匹配；QQ 容器启动链只纳入三项。未构建 APK，因此尚未核对实际 APK 清单或运行最小 Ubuntu 命令，Checklist 不勾选 | `app/build.gradle.kts`；`app/src/main/jniLibs/arm64-v8a/`；`app/src/main/kotlin/com/elymbot/android/runtime/container/ProotGuestLauncher.kt` |

原生启动切片完成时未运行 Gradle，依总控要求等待构建时段协调。未运行自动化测试、模拟器或真机；本记录不把已有探针结果当作该切片的 App 构建验收。

## 限制与待验

- 当前切片没有 Manifest Service 或插件可见 API。W02 接线后由 W04 使用正式插件私有目录与 guest 入口。
- 需在协调后的构建中检查 APK 仅带核准的 ARM64 启动文件，并确认其它设备 ABI 与 App 的既有安装兼容性；再在 ARM64 设备验证 proot 启动、guest 退出状态及更新后原生库路径重读。
- 进程关闭是否能结束 NapCat 派生进程，留待 W04 实际启动、退出与重连验收。

## 总控构建复验

- 在 JDK 17、Android SDK 37 和 Gradle Wrapper 9.9.1 下运行 `gradlew.bat :app:mergeDebugNativeLibs --offline --console=plain`，构建成功。基础 App 原生输入目录仅包含表列三项，各文件 SHA-256 与已验证输入一致。
- 随 W02 首轮构建运行 `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain` 通过。检查基础 debug APK ZIP 清单。ABI 仍为 `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64`，三个新增 ARM64 容器启动文件在制品中的哈希与表列输入一致，清单未见 Ubuntu rootfs、Linux QQ、NapCat 资产及旧 bash、busybox、sudo 文件。该 APK SHA-256 为 `069877d4a8190f2e590a5f736eb824b66865841125c377af388b3de7aa4e68c2`。
- 候选 App 的 ARM64 启动、基础 App 依赖图和完整签名插件装卸仍待验收。

## T02.05 文件包校验与私有暂存

- 新增 App 内部 `.elymbot-plugin` ZIP 校验器。内部 manifest 格式暂定 `formatVersion`、`publisherId`、`pluginId`、`version`、`hostApi`、`abis` 和 `files`；ZIP 成员为 `manifest.json`、`signature.bin` 与 `payload/<相对路径>`。签名覆盖 manifest 原始 UTF-8 字节，使用 minSdk 26 可用的 JCA `SHA256withRSA`；每个 payload 文件由签名 manifest 声明长度和 SHA-256。包身份及版本必须匹配调用方从权威发布配置取得的期望值，受信公钥由宿主配置传入，不接受制品自带密钥。
- 当前校验器要求 manifest 文件清单与 ABI 排序且无重复；拒绝未知、重复、目录项和不安全 ZIP 路径。限制为最多 258 个 ZIP 成员、256 个 payload 文件、单文件 64 MiB、总展开 128 MiB、manifest 64 KiB；RSA 公钥限 2048–4096 位。打包器须省略显式 ZIP 目录项。
- staging 入口为 suspend 函数，阻塞校验和拷贝转到 `Dispatchers.IO`；长拷贝检查协程取消，取消或任一校验失败会删除 partial 暂存。有效文件只写入 App 私有 `plugin-packages/staging/` 的全新目录，返回签名 metadata 与 payload 路径供后续初始化；本切片不改活动版本、不接线 UI、不加载 DEX。`hostApi` 当前按 opaque token 精确匹配，待 W02 交接确定正式兼容语义后再对接；此包格式仍是内部验证格式，未公开发布。
- 新增 Android instrumentation 测试源覆盖有效签名包、请求身份/版本不符、签名损坏、host API / ABI 不兼容、payload 摘要不符、路径穿越以及失败保留活动版本标记。测试使用运行时生成的临时 RSA 密钥，无持久测试或发布私钥。已核对的本地合成双源探针仅作为“签名目录 + 制品摘要 + 失败保留旧版”的策略参考；其 OpenSSL Ed25519 密钥与格式不复用。

### 本切片构建

| Checklist 项 | 方法或命令 | 平台或工具版本 | 实际结果 | 仓库相对证据 |
| --- | --- | --- | --- | --- |
| `T02.05` 文件包校验编译切片 | `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain` | JDK 17、Gradle Wrapper 9.9.1、Android SDK 37 | `BUILD SUCCESSFUL`；App 与 instrumentation 测试源码均编译通过。未执行 instrumentation 测试、安装 APK 或访问设备 | `app/src/main/kotlin/com/elymbot/android/runtime/pluginpackage/PluginPackageStager.kt`；`app/src/androidTest/kotlin/com/elymbot/android/runtime/pluginpackage/PluginPackageStagerTest.kt` |

instrumentation 测试需在总控安排的 Android 运行环境执行。当前 App UI service 尚无插件安装动作，因此 W02 接线前该内部暂存器没有调用方；初始化、活动指针切换、旧版回退和 DEX 只读加载仍未验证。

## MuMu Android 15 instrumentation 夹具修复

- 总控在 MuMu Android 15 运行 5 个 `PluginPackageStagerTest` 方法时，全部在测试夹具 `assertTrue(root.mkdirs())` 失败；均未进入包校验或暂存代码。
- 原因是夹具使用 `InstrumentationRegistry.getInstrumentation().context`，取得测试 APK 的私有目录；仪器测试实际运行在目标 App 进程中，且目标 App UID 与测试 APK UID 不同。总控通过 `run-as` 目录权限核对确认该路径归属不匹配。
- 只修正测试夹具：改用 `targetContext`，并在目标 App 的 cache 目录内通过 `Files.createDirectories` 与 `Files.createTempDirectory` 创建临时包文件。校验器和生产私有目录边界未放宽；测试仍向目标 App 私有文件目录暂存。总控将批量重建并复跑；修复后的设备结果待回填。

## 2026-11-23 总控 MuMu 复跑

- MuMu Android 15 / API 35 上使用 `adb shell am instrument -w -e class com.elymbot.android.session.RoomReplySubmitterTest,com.elymbot.android.runtime.pluginpackage.PluginPackageStagerTest com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner`，总计 12 项通过，其中签名包暂存 5 项。包含有效签名、等长摘要篡改、身份与版本、宿主 API 与 ABI、不可信签名和路径拒绝，等长篡改时确认活动版和暂存清理。
- App 和仪器 APK 摘要同 [W02 Feedback](W02-Android会话与投递-feedback.md#2026-11-23-总控-mumu-复跑)。暂存器具备模拟器执行证据。正式目录、用户安装入口、活动版本切换与 DEX 加载仍待接线。

## 2026-11-23 App 插件装载与生命周期切片

- 包格式补入签名覆盖的 `entryClass` 元数据，必需 payload 固定为 `payload/plugin.jar`，其中包含 `classes.dex`。验证后 App 将整个插件归档保持只读；loader 再检查 DEX 条目和大小，并用 Android `DexClassLoader` 的独立子加载器加载签名 entry class。entry class 先以 `Class.forName(..., false, loader)` 检查来源和 Core 契约，再由公开无参构造器触发初始化。
- `PluginRuntimeManager` 在 App 私有 `plugin-packages/installed` 保存完整验证包，以同目录原子 properties 文件保存宿主分配的安装 instanceId 与启用选择。正常进程恢复复用该 identity；明确禁用后再启用会生成新 identity。恢复所有候选后一次性发布 active map；取消会关闭已准备的候选并保留 `RESTORING` 状态。恢复或首装 prepare 失败保留已验证版本，标为 `UNAVAILABLE`，可重试；更新 prepare 失败清理候选，旧 active 实例与版本继续有效。
- W02 消费入口维持 App 内部：每轮由 `activeRegistrations()` 生成新 snapshot，`isActive(identity)` 提供执行时复核；`instanceAvailability(identity)` 区分 `RESTORING`、`ACTIVE`、`UNAVAILABLE`、`INVALIDATED`。W05 使用 `withActivePluginHandle(pluginId, identity, handleType, action)` 在每插件串行锁内获取已激活的类型化句柄；类型不匹配返回空值，不做通用反射或 service map。
- `PluginHostContext.dataDirectory` 由宿主提供，位置为 `files/plugin-packages/data/<pluginId>`，跨插件版本稳定、跨安装实例保持插件隔离。候选 prepare 可以构造延迟数据服务；正式调度由 active 实例的宿主消费方控制。App 不向插件暴露完整 Context。
- 文件持久化和 active map 交换在同一不可取消提交段完成，避免磁盘状态已切换而内存仍保留旧运行时。每个插件的 install/update/enable/disable/uninstall 由独立 Mutex 串行；关闭旧句柄发生在切换授权之后。未向插件提供任意宿主命令执行能力，也未将可选插件实现打入基础 APK。
- AndroidTest 新增真实 DexClassLoader fixture：test-only Java 源码放在非编译的 `app/src/androidTest/fixtures/dex-lifecycle/`，生成的 DEX 以 AndroidTest asset 打包；类 FQN 不存在于 App 或 instrumentation Java classpath。包内 entrypoint、handle 与 tool source 同处 DEX。fixture 覆盖首装 prepare 失败后保留并重试、宿主 owner/dataDirectory、禁用重启 identity 变化、第二 Manager 恢复 identity 复用，以及失败更新保留原 active 版本。fixture 与合成签名密钥只进入 AndroidTest APK/运行时，不进入基础 APK。

  | AndroidTest fixture | 字节数 | SHA-256 |
  | --- | ---: | --- |
  | `app/src/androidTest/assets/plugin-fixtures/signed-dex-plugin.dex` | 4720 | `E19DF10D5FE31665094C576C3E30B33B3E83B88CDB7DFD17584335040D5236CB` |

### DEX fixture 复现命令

fixture Java 源码位于 `app/src/androidTest/fixtures/dex-lifecycle/`，不属于 AndroidTest 编译 source set。用项目编译后的 Core JAR 和 Kotlin 2.4.21 标准库手动编译，再用 Android SDK Build Tools 37.0.0 的 D8 生成 API 26+ DEX。设置 `$KotlinStdlibJar` 为 Gradle 已解析的 Kotlin 标准库 2.4.21 JAR 路径后，可用以下 PowerShell 命令再生 asset：

```powershell
.\gradlew.bat :core:jar --offline --console=plain
$AndroidSdk = $env:ANDROID_HOME
$CoreJar = 'core/build/libs/core.jar'
$FixtureWork = Join-Path $env:TEMP 'elymbot-dex-lifecycle'
$CompileClasspath = "$CoreJar;$KotlinStdlibJar"
New-Item -ItemType Directory -Force -Path "$FixtureWork/classes", "$FixtureWork/dex" | Out-Null
javac --release 8 -cp $CompileClasspath -d "$FixtureWork/classes" 'app/src/androidTest/fixtures/dex-lifecycle/SignedDexPluginEntrypoint.java'
jar --create --file "$FixtureWork/fixture-classes.jar" -C "$FixtureWork/classes" .
java -cp "$AndroidSdk/build-tools/37.0.0/lib/d8.jar" com.android.tools.r8.D8 --min-api 26 --lib "$AndroidSdk/platforms/android-37.0/android.jar" --classpath "$CoreJar" --classpath "$KotlinStdlibJar" --output "$FixtureWork/dex" "$FixtureWork/fixture-classes.jar"
Copy-Item "$FixtureWork/dex/classes.dex" 'app/src/androidTest/assets/plugin-fixtures/signed-dex-plugin.dex' -Force
```

### 2026-11-23 App 与 MuMu 验证

| Checklist 项 | 方法或命令 | 平台或工具版本 | 实际结果 | 仓库相对证据 |
| --- | --- | --- | --- | --- |
| `T02.05` App 与 AndroidTest 编译 | `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain` | JDK 17、Gradle Wrapper 9.9.1、Android SDK 37 | `BUILD SUCCESSFUL`；App 和 AndroidTest 编译通过 | `app/src/main/kotlin/com/elymbot/android/runtime/pluginpackage/`；`app/src/androidTest/kotlin/com/elymbot/android/runtime/pluginpackage/` |
| 真实 DexClassLoader 首装、重试、更新失败与恢复 | `adb shell am instrument -w -e class com.elymbot.android.runtime.pluginpackage.PluginPackageStagerTest#loadsRealDexRetriesUnavailableFirstInstallAndRestoresHostIdentity com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner` | MuMu Android 15、API 35、x86_64 | `OK (1 test)`。独立 D8 fixture 在 base App 进程真实 `DexClassLoader` 子加载器中调用 `prepare`；首装失败保留已验证版本并可重试；host owner 与 dataDirectory 正确；禁用再启用更换 identity、普通恢复复用 identity；坏更新保留旧 active 版本 | `app/src/androidTest/assets/plugin-fixtures/signed-dex-plugin.dex`；`PluginPackageStagerTest.kt` |

本次 App APK SHA-256：`F7D0642BE646508DF3CAE3275EAA9D4577D30DEEEC916BBE01C8344CCD1E6C84`。AndroidTest APK SHA-256：`C94D4CF176C557B077E2ED7848C18809FC1CA8CA8CA6B827AD884B2B0C65E7C0`。此前 5 个签名包暂存用例已在同环境通过；本次新增 DEX 验证另运行 1 项生命周期测试。

### 首轮 MuMu DEX fixture 诊断

- 首次 MuMu DEX 测试的 prepare 次数预期 `1`、实际 `0`，原因是测试 APK 将源类拆入 `classes6.dex`，夹具误读主 `classes.dex`。改为扫描 DEX 后再次失败，原因是 AndroidTest/App classpath 已含同 FQN 的测试 entrypoint，父加载器优先找到后被生产 loader 的来源校验拒绝。最终使用唯一 FQN 的独立 D8 asset 解决；生产 child-loader 来源校验保持严格。随后独立生命周期测试通过，日志和 APK 标识记录在上表。

## 2026-11-23 官方目录与双源分发切片

- 新增 App 内部 `OfficialPluginDistributionRepository`。生产信任配置由宿主注入目录根 RSA 公钥、当前 host API 和支持 ABI；当前没有已授权的目录根密钥，因此 Application 使用 `configuration = null`，状态明确为 `NOT_CONFIGURED`，不发起请求。目录只从固定官方 GitHub latest Release API 的 `elymbot-plugin-directory.json` 与 `elymbot-plugin-directory.sig` 读取；校验 GitHub API 给出的两个资产大小和 SHA-256，再用注入的根公钥校验覆盖原始 UTF-8 字节的 `SHA256withRSA` 签名。已验证原文及签名写入 App 私有目录，读取缓存时仍使用当前根密钥重新验证。
- 内部目录格式为 `formatVersion: 1` 和排序的 `plugins` 发行列表。每个条目包含发布者、插件、版本、host API、ABI、发布者 X.509 RSA 公钥，以及包长度、SHA-256、GitHub URL 和可选 Gitee URL。公钥和地址均受根签名保护；未知字段、重复条目、越界长度、无效 URL、无效兼容条件及错误签名被拒绝。旧版本恢复依赖签名目录保留仍可用的历史发行条目；网络失败和新目录校验失败保留 last-good 缓存，不从已安装包或未签名状态推导信任。
- `resolveExpectation(PluginPackageReference)` 只为目录内精确的发布者、插件和版本返回 `PluginPackageExpectation`，包内不能提供信任根。`downloadPlugin(reference)` 只接受已验证目录中的兼容版本，先下载 GitHub，再在传输失败时使用同条目签名声明的 Gitee 镜像；两路都按同一签名长度与 SHA-256 校验。GitHub 返回完整但摘要不符时拒绝且不尝试镜像。返回的下载文件位于 App 私有缓存并只读，消费后可关闭删除。
- `PluginDirectorySnapshot` 区分未配置、可用、目录缺失、网络错误及无效目录，并带有受限失败类别；不暴露异常正文或远端响应体。W02 已接收 `refreshPluginDirectory()` → `manager.restore(repository::resolveExpectation)` 接线约定；目前 Application 在 Room 投递恢复前刷新并恢复插件。真实插件目录、独立信任根及 Gitee 地址尚未发布，故尚无生产目录接收或真实双源证据。
- 新增 `OfficialPluginDistributionRepositoryTest`，使用运行时 RSA 合成密钥和本机 HTTP fixture 覆盖未配置不联网、两个历史版本解析、重启后 last-good 缓存、主源 503 后 Gitee 精确制品回退、摘要错误不回退及无效新签名保留 last-good。测试上下文把 `applicationContext`、`filesDir` 与 `cacheDir` 指向唯一 fixture 私有目录，清理只作用于该目录。

### 目录分发构建与 MuMu 验证

| Checklist 项 | 方法或命令 | 平台或工具版本 | 实际结果 | 仓库相对证据 |
| --- | --- | --- | --- | --- |
| `T02.06 A12、A13` 合成目录、签名与双源失败路径 | `:app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain`；运行 `OfficialPluginDistributionRepositoryTest` | JDK 17、Gradle Wrapper 9.9.1、Android SDK 37；MuMu Android 15、API 35 | App 与 AndroidTest 构建成功；5/5 instrumentation 用例通过。证据等级为合成 RSA/HTTP 与模拟器，未连接真实目录或 Gitee | `OfficialPluginDistributionRepository.kt`；`OfficialPluginDistributionRepositoryTest.kt` |

MuMu 产物：App SHA-256 `A3C65A0511094AA2B5961DC863AC2803AFEA257CCF31E9D109912C16EED8FB90`；AndroidTest SHA-256 `54B3435FE56E420C0CE002D2DA0F40436A33E00FCEB0BBDC06AA00ED22CC4361`。

### 本机 HTTP fixture 返工

- 首轮 4 个有配置测试均在 `refreshPluginDirectory()` 返回 `NETWORK_ERROR`，没有进入目录验签或备用下载。MuMu debug network security 配置只允许 `localhost` 明文流量；fixture URL 使用 `127.0.0.1`，与既有配置不符。将测试 URL 改为 `localhost`，监听 socket 保持绑定 loopback 地址。
- 原测试曾清理真实 App 的 `plugin-distribution` 与 `plugin-downloads` 路径。现改用目标 App Context 包装出的唯一测试私有目录，并在 teardown 只删除该 fixture 根；生产路径和已安装目录未被测试清理。修复后上述 5/5 测试通过。

### App 更新实现状态

- 已确认当前官方 Release API 基线为 `v0.1.0` / tag `正式版`，仅含 debug 与 release APK，未含 `elymbot-release.json`、数值版本元数据或插件目录。Release APK 的 `versionCode=1`、`versionName=0.1.0`，摘要和签名证书见 [W07 Feedback](W07-集成与验收-feedback.md)。
- 已约定未来 sidecar 文件名 `elymbot-release.json`，字段为 `formatVersion`、`packageName`、`versionCode`、`versionName`、`apkAssetName` 和 `apkSha256`。当前 sidecar 缺失，因此 updater 应报告 `METADATA_MISSING`；App 更新检查与用户确认后的下载/签名校验/系统安装入口仍待实现。

## 2026-11-23 App 更新校验与系统安装请求

- 新增 `OfficialPluginDistributionConfiguration.load(context)`，从可选资源 `plugin-distribution/directory-root.der` 读取发布构建注入的 X.509 RSA 公钥，并以当前支持 ABI 和 `elymbot-plugin-api-v1` 生成目录配置。资源缺失返回 `NotConfigured`，无效或无法读取时返回可区分的 `Invalid` 结果；仓库没有加入生产根密钥。当前 `ElymBotApplication` 仍以空配置初始化目录仓库，因此不会因缺少正式信任根而联网或阻断基础 App 启动。
- 新增 App 内部 `OfficialAppUpdateService`。更新查询先核对安装包名和当前签名，再读取固定 GitHub Release API；必须先取得有 API 大小与摘要证明的 `elymbot-release.json`，按 sidecar 数值 `versionCode` 判断是否提供更新，且不会在确认前请求 APK。确认后的下载校验长度、SHA-256、APK 包名、数值版本和签名证书；当前代码固定 `com.elymbot.android` 及现有正式发行证书摘要。由于当前正式 Release 不含 sidecar，真实发行返回 `METADATA_MISSING`，不会下载 APK。
- 验证通过的更新包保存在 App 缓存 `app-updates/`，下载结束后设置为只读并确认 `canWrite()` 为 false。检查发现此前权限顺序会在置只读后重新打开所有者写权限，导致 `installerIntent()` 按自身只读要求拒绝有效文件；现改为关闭所有者及其他用户的写权限，并在权限无法生效时拒绝交付。
- `installerIntent()` 返回 `Intent.ACTION_INSTALL_PACKAGE`，以 `${applicationId}.updates` FileProvider URI 分享 APK，设置读取授权及返回安装结果标记。W02 Manifest 声明与实际路径一致：`REQUEST_INSTALL_PACKAGES`、`@xml/app_update_paths` 和仅限缓存 `app-updates/` 的 `<cache-path>`。真实用户确认界面仍由 App/W06 接入；本次测试不启动系统安装器。
- `OfficialAppUpdateServiceTest` 的先前 5 个合成检查通过，覆盖正式配置对 debug 包的拒绝、sidecar 缺失、数值版本比较、API 摘要/包名错误拒绝，以及确认前不请求 APK。该 5 项结果来自前一 APK；发现只读权限问题后未重复整组，且这些断言不覆盖下载后 APK 权限。
- 新增 `installerIntentUsesReadOnlyAppUpdateProviderUri`，使用目标 App 缓存下唯一 UUID 文件验证实际 FileProvider authority、路径、读取授权和内容读取。`finally` 只删除本测试创建的文件；只有当目录由本测试创建且仍为空时才删除目录。该测试不启动安装器，也不把合成内容作为可安装 APK。

### App 更新构建与 MuMu 验证

| 验证项 | 方法 | 平台或工具 | 结果 | 仓库相对证据 |
| --- | --- | --- | --- | --- |
| App 与 AndroidTest 编译及新增安装器请求 | `:app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain`；`adb shell am instrument -w -e class com.elymbot.android.runtime.pluginpackage.OfficialAppUpdateServiceTest#installerIntentUsesReadOnlyAppUpdateProviderUri com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner` | JDK 17、Gradle Wrapper 9.9.1、Android SDK 37；MuMu Android 15、API 35 | 构建成功；新增目标测试 `OK (1 test)`。通过实际 target App FileProvider URI 读取只读测试文件；未启动系统安装器 | `OfficialAppUpdateService.kt`；`OfficialAppUpdateServiceTest.kt`；`AndroidManifest.xml`；`app_update_paths.xml` |

最终目标测试产物：App SHA-256 `B0430C9EC8CEE8E456D3AB8A5FBDBC6D2061B3FA3100A48D130D01F95D2C40BC`；AndroidTest SHA-256 `E08A78D2BE0341C460FBE0B3B370C8D09992D579B4E745101EA875A78F6721F3`。真实官方更新 sidecar、独立签名元数据和可安装的新版 APK 尚未发布，本轮未验证正式制品下载、实际系统安装结果或关于页入口。

## 2026-11-23 插件 DEX 与签名发行包构建入口

- 根 `build.gradle.kts` 新增 `packagePluginDex`，以 `-PelymbotPluginId=scheduled-tasks|qq` 选择已接入插件 release AAR，解出 `classes.jar`，并用固定 Android SDK Build Tools 37.0.0 的 D8 生成 API 26+ 单个 `classes.dex`。使用插件实际 `releaseRuntimeClasspath` 作为 DEX 程序输入；排除宿主提供的 Core、Kotlin 标准库、annotations、coroutines 与 serialization 类型，插件之间的实现依赖会被拒绝。`releaseCompileClasspath` 只作为编译解析类路径。产物是仅含 `classes.dex` 和插件自身 `src/main/resources` 的 JAR，未接入基础 App 构建。
- `scripts/package-plugin.ps1` 将 DEX JAR 与签名清单封装为 App 校验器接受的 ZIP：`manifest.json`、`signature.bin`、`payload/plugin.jar`，签名为原始 UTF-8 manifest 的 SHA256withRSA/PKCS#1 v1.5。脚本要求显式提供 RSA PEM 私钥路径，不生成密钥或读取隐式密钥；签名密钥的发布者职责留给调用方。可选 `-Offline` 才向 Wrapper 传入离线参数。显式插件资源必须同时提供调用者预期的 SHA-256，且与源文件核对后才写入 inner JAR。输出与临时文件均位于忽略的 `build/` 下，不属于仓库发布材料。
- README 增加本地构建脚本的最小调用例和 source-only 边界说明。发布签名密钥、已签名发行包及 QQ rootfs 均未加入仓库。

### W05 插件包合成签名烟测

| 验证项 | 方法或命令 | 工具与输入 | 实际结果 | 仓库相对证据 |
| --- | --- | --- | --- | --- |
| `packagePluginDex` D8 输出与宿主类型排除 | `.\gradlew.bat packagePluginDex -PelymbotPluginId=scheduled-tasks --offline --console=plain`；静态读取输出 DEX class definitions | Gradle Wrapper 9.9.1、JDK 17、Android SDK Build Tools 37.0.0；W05 release AAR | 生成只有一个 `classes.dex` 的 JAR，191,346 字节，SHA-256 `91d360381fdafcc748eeb1a044dad33816dbdccb6c5ad21e958d88e5df3232c4`。DEX 有 91 个 class definitions；未定义 Core、Kotlin/Kotlinx、JSON 或 JetBrains 宿主类 | `build.gradle.kts`；`plugins/scheduled-tasks/build.gradle.kts` |
| App 文件包格式与签名 | `.\scripts\package-plugin.ps1`，显式传入临时测试 PEM 与以下合成 identity | `publisherId=synthetic.elymbot.tests`；`pluginId=scheduled-tasks`；`version=0.0.2-ui-probe`；`hostApi=host-api-test-1`；ABIs `x86_64`, `x86`, `arm64-v8a`, `armeabi-v7a`；entry `com.elymbot.android.plugins.tasks.ScheduledTasksPluginEntrypoint` | 独立检查确认 ZIP 仅含三个约定成员；PKCS#1 v1.5 / SHA-256 签名有效；manifest 声明的 payload 长度和摘要与 inner JAR 相符。签名文件包 192,218 字节，SHA-256 `CAB3C3380C6214AA2474098C16F2785B7DAD2497497016B6A69CB8A03EB5FE51`。使用的是一次性合成 RSA 测试密钥，未使用或生成正式发布密钥 | `scripts/package-plugin.ps1`；`app/src/main/kotlin/com/elymbot/android/runtime/pluginpackage/PluginPackageStager.kt` |

该验证覆盖 W05 release AAR 到本地签名文件包的构建链，不代替正式发布目录、发布者信任根或用户安装验收。脚本最初检查私钥普通文件时错误拒绝有效 PEM，并在 ZIP 成员完全相等时受到 PowerShell strict mode 的空比较结果影响；两处已修正。Gradle 离线参数现为 opt-in，默认为正常在线 Wrapper 行为。

### QQ DEX 与 rootfs 资源打包

- QQ release runtime graph 的实际依赖为 Commons Compress 1.28.0、Commons Codec 1.19.0、Commons IO 2.20.0、Commons Lang 3.18.0 和 XZ 1.10；宿主 Kotlin 标准库及 annotations 由 App 提供。`packagePluginDex -PelymbotPluginId=qq` 依据该图将 QQ 实现及所需 runtime 依赖转为单一 DEX。QQ 运行资源按 W04 消费端读取路径注入内层 `plugin.jar`：`runtime/ubuntu-rootfs.tar.xz` 与模块资源 `runtime/root_launcher.sh`，未额外写入 `payload/runtime/`。
- 首次合包把 `classes.dex` 也按 STORE 写入，连同 64,133,552 字节 rootfs 后超过 App 单 payload 文件的 64 MiB 上限 47,394 字节。现仅对未预压缩的 DEX/脚本使用 ZIP Optimal；`.tar.xz` 保持 STORE。重新签名检查通过，内层 JAR 为 65,434,541 字节，低于 67,108,864 字节限制；外层包为 65,435,386 字节，低于 128 MiB 限制。
- 当前 QQ 合成发行包身份为 `publisherId=synthetic.elymbot.tests`、`pluginId=qq`、`version=0.0.1-on04-probe`、`hostApi=elymbot-plugin-api-v1`、ABI `arm64-v8a`。外层 ZIP 成员、RSA PKCS#1 v1.5 / SHA-256 签名、manifest 内层长度/摘要及资源清单均由总控独立核对；rootfs SHA-256 为 `91acaa786b8e2fbba56a9fd0f8a1188cee482b5c7baeed707b29ddaa9a294daa`。本地 ON-04 rootfs 与一次性合成签名输入未加入仓库。

| 制品 | 大小 | SHA-256 |
| --- | ---: | --- |
| QQ `payload/plugin.jar` | 65,434,541 | `5ebc52d8ba731f9fc1aa7aa810c6482dca4367029cf4561080c3ae61386529a2` |
| QQ signed `.elymbot-plugin` | 65,435,386 | `82c29ac21455fe9eff721e18919659b7c9399213816de3c57f481f1540289852` |

内层条目检查：`classes.dex` 为 3,017,440 字节、压缩后 1,296,231 字节；`runtime/root_launcher.sh` 为 14,900 字节、压缩后 4,382 字节；`runtime/ubuntu-rootfs.tar.xz` 为 64,133,552 字节并保持原样存储。此为合成签名、目录外本地输入的格式及大小证明，不是正式发布或 QQ 登录验收。W04 的源代码审查仍有未关闭项，因此该 QQ 制品摘要仅作为中间验证结果。

### W05 正式 App 中的签名插件装载

| 验证项 | 方法或命令 | 平台 | 实际结果 | 仓库相对证据 |
| --- | --- | --- | --- | --- |
| 已签名 W05 包通过真实 App 插件入口加载 | `adb shell am instrument -w -e class com.elymbot.android.session.ScheduledTaskHostIntegrationTest#signedUiCapabilityLoadsAndPreservesGuideChoice com.elymbot.android.debug.test/androidx.test.runner.AndroidJUnitRunner` | MuMu Android 15、API 35、x86_64 | `OK (1 test)`；检查了刚生成且 SHA-256 为 `CAB3C3380C6214AA2474098C16F2785B7DAD2497497016B6A69CB8A03EB5FE51` 的 signed W05 文件包。测试后恢复了原有缓存探针文件；未使用正式发行密钥或权威目录 | `ScheduledTaskHostIntegrationTest.kt`；`PluginRuntimeManager.kt`；`PluginRuntimeLoader.kt` |

App APK SHA-256：`6946D5265E119A07BFBE2DF7178657C7B7A27AA793C2F416645EE37918F3FB38`。AndroidTest APK SHA-256：`B068F028E8DD8838DD3227CDD885C1AE8310DF820B0EC26CA3D2685B10A09F18`。该 MuMu 用例仅验 W05 的签名插件装载和宿主状态；未执行 QQ 插件加载、登录或网络往返。当前 QQ DEX/签名包也尚未在 App 中加载，且 W04 源审查项未闭合。

## 2026-11-24 本地签名目录与 App sidecar 生成入口

- 新增 scripts/create-official-plugin-directory.ps1，按 App 实际解析格式从一个已签插件文件包生成签名发行目录。脚本先对输入包和可选历史目录材料创建有界临时快照，再对快照检查外层 ZIP 成员、插件 RSA 签名、manifest 身份、DEX JAR 长度与摘要，最终把同一份经验证快照移入 release-assets，避免验证后重新打开源路径。目录项按 App 要求排序，重复版本被拒绝；提供历史目录时，先用同一显式目录根公钥验签并保留此前版本。输出 JSON 和签名资产使用 App 固定文件名，目录根公钥 DER 另置 review 子目录，仅供审阅，不会自动接入 App。输出已存在时拒绝覆盖。
- 新增 scripts/create-app-release-sidecar.ps1，调用 Android SDK Build Tools 的 aapt 与 apksigner 检查候选 APK 的正式包名、数值版本及当前 updater 固定的正式签名证书。它校验与交付均使用同一只读快照，并将 APK 与 elymbot-release.json 写入忽略的 build/outputs；不改 Gradle 版本、不触发安装、不发布资产。
- 更新 README 的本地发行目录和 sidecar 使用说明。正式目录根密钥、插件发布者生产公钥和 Gitee 镜像地址尚未由发布方提供；本次没有生成、读取或导入任何生产私钥，也没有修改 Application 的未配置状态或远端发布内容。

| 验证项 | 方法或操作 | 环境与输入 | 实际结果 | 证据 |
| --- | --- | --- | --- | --- |
| PowerShell 脚本语法 | 对两个新增脚本调用 PowerShell AST Parser | PowerShell 7 | 两个脚本均无解析错误 | scripts/create-official-plugin-directory.ps1；scripts/create-app-release-sidecar.ps1 |
| 插件发行目录合成烟测 | 使用两份已签 W05 包、显式合成发布者公钥与临时目录根密钥生成新目录并合并已签历史目录；独立复核目录 RSA 签名，再以错误发布者公钥重跑拒绝路径 | 合成 publisher、合成 root key、合成 GitHub URL；未访问真实发布端 | 成功保留 2 个版本；有效目录签名通过独立 RSA/SPKI 验证，错误发布者公钥明确拒绝。最近包快照 75,505 字节，SHA-256 E9F1392B9F72F436D8830C8356114C0277A58A14490E0DB7641ECD9E235E51D2；目录 JSON 1,797 字节，SHA-256 302568A5ED8B0E459FE5F560839E91B5B01C100C197A26B6650D46FA3691C307 | scripts/create-official-plugin-directory.ps1；本地 build/outputs 产物未纳入仓库 |
| App sidecar 正向生成 | .\scripts\create-app-release-sidecar.ps1 -AppApkPath updates/v0.1.0/ElymBot-v0.1.0-release.apk -ApkAssetName ElymBot-v0.1.0-release.apk | Android SDK Build Tools 37.0.0；现有公开 release APK | 通过 aapt、apksigner、包名/版本/证书核验；生成 sidecar 216 字节。sidecar 记录 versionCode 1、versionName 0.1.0，APK SHA-256 6861704B7CA84E63A2E000A94F19EFAF69E8B2B2C57219E9A1EAC16E3907AC3B；sidecar SHA-256 8C75B1F8058F38FC4F95437A89730FCFF29F7160B0DD0DD8B9456A43F68BA12F | scripts/create-app-release-sidecar.ps1；输出留在忽略的 build/outputs |
| App sidecar 反例拒绝 | 对同包名、同数值版本但由临时非官方证书重签的 APK 运行 sidecar；另将公开 debug APK 作为输入 | 本地 APK 校验；临时签名副本与密钥随后清理 | 重签 APK 在证书摘要比对阶段拒绝；debug APK 在包名元数据检查阶段拒绝；原 release APK SHA-256 保持不变，未生成反例 release 输出 | scripts/create-app-release-sidecar.ps1 |

本次验证证明本地合成签名目录和现有公开 App APK sidecar 的生成格式；未生成或发布正式插件目录，未加入正式目录根公钥，未验证真实 Gitee 回退或新的 App 系统安装。
