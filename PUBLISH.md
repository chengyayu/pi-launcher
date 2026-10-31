# 发布到 JetBrains Marketplace

## 当前状态

| 项 | 值 |
|---|---|
| Plugin ID | `com.chengyayu.pi-launcher` |
| Marketplace ID | [34851](https://plugins.jetbrains.com/plugin/34851-pi-launcher) |
| 已发布版本 | `0.1.0`、`0.2.0`、`0.2.1`、`0.3.0`、`0.3.1`、`0.4.2`、`0.4.3`、`0.4.4` |
| 审核状态 | 0.1.0 – 0.3.1 为 `approve: true` / `listed: true`；0.4.2 起已上传，待审 |
| Pricing | Free，MIT |
| Vendor | chengyayu（Non-trader） |

插件 ID 一经发布**不可更改**。

## 首次发布（已完成，仅作记录）

首次发布必须在网页上手动上传 —— Gradle 的 `publishPlugin` 只能用于**后续版本更新**。

1. 登录 <https://plugins.jetbrains.com/author/me>
2. 首次上传需先接受 **JetBrains Marketplace Developer Agreement** 并创建 Vendor profile
3. `Upload plugin` → 选择 `build/distributions/pi-launcher-<version>.zip`
4. 填写 Plugin ID / Name / Category / License，其余（描述、change notes、vendor）从 `plugin.xml` 自动带出
5. 在编辑页的 Media 区块单独上传截图（**截图不在 zip 里**）

## 后续版本发布

### 方式 A：GitHub Release（推荐）

1. 改 `build.gradle.kts` 的 `version`，更新 `plugin.xml` 的 `<change-notes>`
2. 提交并推送
3. 配好 secret（只需一次）：
   ```bash
   gh secret set JETBRAINS_PUBLISH_TOKEN --repo chengyayu/pi-launcher
   ```
   token 从 <https://plugins.jetbrains.com/author/me/tokens> 生成，输入时是隐藏的。
4. 在 GitHub 上创建 Release，tag 形如 `v0.2.0`

`.github/workflows/publish.yml` 会依次执行 test → buildPlugin → publishPlugin。
tag 与 `build.gradle.kts` 里的 version 不一致会直接失败，避免版本错乱。

触发条件是 `release: types: [published]`。**不要改回 `created`**：一步到位的发布（`gh release create`
不带 `--draft`、API、网页直接发布）只发 `published`，`created` 只对应草稿，监听它等于每次真发布都不触发。
release 事件用的是 **tag 所在 commit** 里的 workflow 文件，所以改这里要先合进 main 再打下一个 tag。

### 方式 B：本地发布

```bash
# token 放在 ~/.gradle/gradle.properties（在仓库外，不会被提交）
echo 'publishToken=<你的 token>' >> ~/.gradle/gradle.properties
./gradlew publishPlugin
```

`build.gradle.kts` 优先读环境变量 `PUBLISH_TOKEN`，其次读 `publishToken` 属性。

## 更新截图（Media）

截图不在 zip 里，`publishPlugin` 也不会碰 Media 区块，只能在插件编辑页手动上传：

1. 打开 <https://plugins.jetbrains.com/plugin/34851-pi-launcher/edit>
2. **Media** 区块上传 `screenshots/04-session-dashboard.png`（1600×1005）
3. 建议把它排到第一位：Session Dashboard 最能说明这个插件现在做什么

## 发布前检查

- [ ] `./gradlew test` 通过
- [ ] 在真实 IDE 里手工验证：启动 / 状态栏 / Send to Pi / 设置面板
- [ ] `plugin.xml` 的 `<change-notes>` 已更新
- [ ] tag 与 `build.gradle.kts` 的 `version` 一致（workflow 会硬校验，不一致直接红）

## 兼容性校验：只看 JetBrains 官方结果

**不在本地跑 `verifyPlugin`。** 它要下载约 9.5 GB、对照 sinceBuild 范围内的每一个 IDE
（243 / 251 / 252 / 253 / 261 / 262 / 263 共 7 个）逐个校验，耗时且占磁盘，CI 的 runner
也放不下——而且它算出的结论**官方不认**：用户装插件时看到的是 Marketplace 自己的校验结果。

官方路径（上传后自动进行，无需本地介入）：

- 作者后台 `/author/me` → 插件编辑页：版本审核状态、校验报错
- 公开版本页：`https://plugins.jetbrains.com/plugin/34851-pi-launcher/versions`
- 公开 API（无需 token）：
  ```bash
  curl -s "https://plugins.jetbrains.com/api/plugins/34851/updates" | python3 -m json.tool
  ```
  返回每个版本的 `compatibleVersions`（各 IDE 的兼容下限）、`since`/`until`、`approve`/`listed`

`build.gradle.kts` 里的 `pluginVerifier()` 依赖保留着，需要时仍可 `./gradlew verifyPlugin`，
但它不是发布门槛。

## 回填旧版本的 Release

给已经发布过的版本补 tag + release 时要注意：**release 事件用的是 tag 所在 commit 里的 workflow 文件**，
不是 main 上的最新版。所以它会跑当时那套流程，最终在 `publishPlugin` 因为「版本已存在」而失败
（见下方 0.1.0 例）。这种 run 不要当故障修，直接取消即可：它本来就不该再传一次。

建 release 时记得 `--latest=false`，否则新补的旧版本会因为发布时间最新而抢走 Latest：

```bash
gh release create v0.1.0 --title "0.1.0" --notes-file notes.md --latest=false
```

## 注意事项

- 描述、change notes、图标都来自 `plugin.xml`，改这些不需要重新上传 zip 之外的东西
- 截图和 tagline 在 Marketplace 网页上单独维护，与 `plugin.xml` 无关
- 故意不设 `untilBuild`：官方校验给出的 `since`/`until` 是 `243.0+`（无上限），各 IDE 均为
  `2024.3+`；设了上限反而会让升级 IDE 的用户看到「不兼容」，直到恰好发新版
- Marketplace 会自动为插件签名，不需要自备证书（`signPlugin` 在没有证书时会跳过）

## 换版本时不要删旧版本

在编辑页删除某条版本记录时，如果它是**唯一的版本**，Marketplace 会把**整个插件**一并删除
（插件页直接 404），连截图和 Vendor 关联都要重来。

要换内容就直接上传**新的版本号**，旧版本让它留着 —— 用户在 IDE 里只会装到最新的那条。

## 已验证

### 0.1.0

`publish.yml` 已用 `workflow_dispatch` 实测过一次（run 37577588313）：
Checkout → Java → Gradle → Test → Build → Upload artifact 全部通过，最后的
`publishPlugin` 报：

```
Failed to upload plugin: Upload failed:
The com.chengyayu.pi-launcher plugin already contains version 0.1.0 in channel
```

这是**预期结果**，说明 secret 里的 token 有效、上传通道畅通 —— 只是因为 0.1.0
已经上传过而被拒绝。如果 token 无效，报错会是 401 / unauthorized 之类的认证失败。

所以：**用同一个版本号重跑这个 workflow 会红，这不是故障。** 换新版本号即可正常发布。

### 0.1.0

首次发布是**网页手动上传**的（Gradle 的 `publishPlugin` 只能用于后续版本更新），所以没有对应的
workflow run 记录。后来追溯到发布当时的提交 `c368119` 补了 tag 与 Release：那就是被上传的
0.1.0（其后的 `drop the until-build cap` 是上传之后才改的）。

补 Release 时触发了一次 run（37933606715，`head_sha=c368119`）：它跑的是**那个提交里的**
workflow（还在跑 `Verify Plugin`），注定会在 `publishPlugin` 因 0.1.0 已存在而失败，因此已取消，
未对 JetBrains 发起重复上传。

### 0.2.0

走 Release 路径真实发布过一次（run 37932866698，`release: created` 触发）：
Check tag against plugin version → Test → Build plugin → Upload artifact →
`Publish plugin` 全部 success，`publishPlugin` 为 `BUILD SUCCESSFUL`（该任务失败会直接红，
重复版本号就是例子）。

上传后公开 API 仍只列出 `0.1.0`：插件级 `approve` 仍为 `false`，新版本要等人审通过才在
版本页可见。`publishPlugin` 成功 = 文件已进 JetBrains，版本页滞后属于审核流程，不是发布失败。

### 0.4.2 / 0.4.3

这两个版本的 Release 创建后**没有触发** `publish.yml`，是手动补的：

```bash
gh workflow run publish.yml --ref main
```

两次都 success（41177386395 传 0.4.2，41179498486 传 0.4.3），`publishPlugin` 为
`BUILD SUCCESSFUL`。原因是当时 workflow 监听 `created`，而 `gh release create` 直接发布
只发 `published` 事件（0.2.0/0.3.x 是在网页上先存草稿再发布，草稿创建时才发 `created`，
所以那时候能自动触发）。workflow 已改为 `published`。

`gh workflow run` 走的是 `main` 的最新提交，没有 tag，所以 `Check tag against plugin version`
那步是 skipped：手动补发时**自己确认** `build.gradle.kts` 的 version 就是想传的版本。

改成 `published` 之后实测验证过一次（run 41180019712）：在 main 上打临时 tag
`v0.0.0-trigger-check` 建一个非 draft 的 release，run 被自动拉起，在 `Check tag against plugin
version` 按预期报红（tag 与 0.4.3 不符），Test/Build/Publish 都没跑。release 与 tag 随即已删除，
所以这条红 run 留在 Actions 历史里是验证痕迹，不是故障。

## CI 工具链

| 项 | 值 |
|---|---|
| runner | `ubuntu-24.04`（固定，不用 `ubuntu-latest`） |
| `actions/checkout` | `v7` |
| `actions/setup-java` | `v6`（zulu 21） |
| `gradle/actions/setup-gradle` | `v5` |
| `actions/upload-artifact` | `v7` |

两点容易被"顺手升级"掉：

- `ubuntu-latest` 于 2026-10-19 迁到 Ubuntu 26，会把 JDK 和 Gradle 环境在发版当口换掉，所以写死
  Ubuntu 24.04。要换镜像就改这一行并跑一次 `workflow_dispatch` 验证。
- `setup-gradle` 停在 `v5`：`v6` 把缓存拆成 `gradle-actions-caching`，那是 Gradle 的专有许可组件
  （启用缓存即适用其 Terms of Use），这个 workflow 不需要它。

升级后验证过一次（run 41180764150，`workflow_dispatch`）：Checkout → Set up Java → Set up Gradle
→ Test → Build plugin → Upload artifact 全绿，只有 `Publish plugin` 报
`The com.chengyayu.pi-launcher plugin already contains version 0.4.3`。这是预期结果：
`workflow_dispatch` 不改版本号重跑必然重复上传（见上文 0.1.0），同时也再次证明 token 有效。

### 0.4.4

第一次走完**全自动**链路：建 Release 后 `publish.yml` 自己起来了（run 41184254454，
`release: published`），Check tag → Test → Build → Upload → `Publish plugin` 全 success，
`publishPlugin` 为 `BUILD SUCCESSFUL`。这就是 `published` 触发器、`ubuntu-24.04` 和新版本
action 的端到端验证。

0.4.3 时 plugin-verifier（1.411）对 `IU-263.7307.112` 报的那 1 条废弃 API
（`FileEditorManager.openFileEditor`）已在 0.4.4 修掉：改为 `OpenFileDescriptor`。对
`IC-252.28539.149` 与 `IU-263.7307.112` 都是 Compatible，无 deprecated 报告。
注意 2026.3 把**所有**阻塞式 `FileEditorManager.openFile*` 都标废弃，官方替代
（`requestOpenEditor` / `FileEditorOpenRequest`）在 243 基线不存在，且 243 的
`requestOpenFile(VirtualFile)` 返回 `void` 而 263 返回 `CompletableFuture`，所以那条
"改用非阻塞版"的建议在 sinceBuild 243 下不可行。
