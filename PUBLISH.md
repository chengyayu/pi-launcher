# 发布到 JetBrains Marketplace

## 当前状态

| 项 | 值 |
|---|---|
| Plugin ID | `com.chengyayu.pi-launcher` |
| Marketplace ID | [34849](https://plugins.jetbrains.com/plugin/34849-pi-launcher) |
| 已发布版本 | `0.1.0` |
| 审核状态 | 审核中（新插件首次上传需人工审核，通常 1–2 个工作日） |
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

### 方式 B：本地发布

```bash
# token 放在 ~/.gradle/gradle.properties（在仓库外，不会被提交）
echo 'publishToken=<你的 token>' >> ~/.gradle/gradle.properties
./gradlew publishPlugin
```

`build.gradle.kts` 优先读环境变量 `PUBLISH_TOKEN`，其次读 `publishToken` 属性。

## 发布前检查

- [ ] `./gradlew test` 通过（38 个用例）
- [ ] `./gradlew verifyPlugin` 通过 —— **不要跳过**
- [ ] 在真实 IDE 里手工验证：启动 / 状态栏 / Send to Pi / 设置面板
- [ ] `plugin.xml` 的 `<change-notes>` 已更新

### 关于 verifyPlugin

它会对照 `sinceBuild`–`untilBuild` 范围内的**每一个** IDE 做校验，当前配置下会下载约
**9.5 GB**、校验 7 个版本（263 / 2026.2 / 2026.1 / 2025.3 / 2025.2 / 2025.1 / 2024.3）。

因此它**没有**放进 CI：GitHub 托管 runner 的可用磁盘约 14 GB，放不下，且耗时过长。
请在本地发布前手动跑一次。

## 注意事项

- 描述、change notes、图标都来自 `plugin.xml`，改这些不需要重新上传 zip 之外的东西
- 截图和 tagline 在 Marketplace 网页上单独维护，与 `plugin.xml` 无关
- `untilBuild` 当前是 `262.*`：263 正式版发布后插件会显示不兼容，届时需要发版放宽
- Marketplace 会自动为插件签名，不需要自备证书（`signPlugin` 在没有证书时会跳过）

## 已验证

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
