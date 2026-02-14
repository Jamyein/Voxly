# 应用签名配置指南

## 本地开发配置

### 1. 配置本地签名

在项目根目录创建 `local.properties` 文件（从模板复制）：

```bash
cp local.properties.template local.properties
```

编辑 `local.properties`，添加你的密钥库密码：

```properties
RELEASE_STORE_PASSWORD=你的密钥库密码
RELEASE_KEY_PASSWORD=你的密钥密码  # 如果与密钥库密码相同，可以省略
```

### 2. 确保密钥库文件位置正确

将生成的 `voxly-release.keystore` 文件放置在：
- 项目根目录（`D:\Documents\opencode\Voxly\voxly-release.keystore`）

或

- app 模块目录（`D:\Documents\opencode\Voxly\app\voxly-release.keystore`）

### 3. 构建签名 APK

在 Android Studio 中：
- 选择 Build → Generate App Bundles or APKs → APK
- 选择 release 或 dist build variant
- 生成的 APK 将使用你的发布密钥签名

或使用命令行：

```bash
./gradlew assembleRelease
```

## GitHub Actions CI/CD 配置

### 1. 准备 Base64 编码的密钥库

在 Windows PowerShell 中执行：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("D:\Documents\opencode\Voxly\voxly-release.keystore")) | Set-Clipboard
```

或在 CMD 中使用 certutil：

```cmd
certutil -encode voxly-release.keystore voxly-release.keystore.b64
type voxly-release.keystore.b64
```

### 2. 配置 GitHub Secrets

1. 打开 GitHub 仓库页面
2. 点击 **Settings** → **Secrets and variables** → **Actions**
3. 点击 **New repository secret** 添加以下密钥：

| Secret 名称 | 值 | 说明 |
|------------|-----|------|
| `SIGNING_KEYSTORE_BASE64` | Base64 编码的密钥库内容 | 上一步生成的 base64 字符串 |
| `SIGNING_STORE_PASSWORD` | 你的密钥库密码 | 创建密钥库时设置的密码 |
| `SIGNING_KEY_PASSWORD` | 你的密钥密码 | 如果与密钥库密码相同，填写相同值 |

### 3. 验证配置

配置完成后，触发一次构建：

- **Release Build**: 手动触发 Actions → Release Build workflow
- **Dev Build**: 推送到 main 或 develop 分支自动触发

### 4. 验证 APK 签名

下载构建的 APK 后，使用以下命令验证签名：

```bash
# 检查 APK 签名
jarsigner -verify -verbose -certs app-release.apk

# 或查看证书信息
keytool -printcert -jarfile app-release.apk
```

## 安全注意事项

1. **永远不要提交密钥库文件到 Git**
   - 已配置 `.gitignore` 忽略 `*.keystore` 和 `*.jks` 文件
   - 已配置 `.gitignore` 忽略 `local.properties` 文件

2. **备份密钥库**
   - 将 `voxly-release.keystore` 备份到安全位置（如密码管理器或加密云存储）
   - 丢失密钥库将导致无法更新已发布的应用

3. **密码安全**
   - 不要在代码中硬编码密码
   - 使用 GitHub Secrets 管理 CI/CD 密码
   - 定期更换密码

## 故障排除

### 问题：本地构建时提示 "Keystore file not found"

**解决方案**：
1. 确认 `voxly-release.keystore` 文件存在于项目根目录
2. 检查 `local.properties` 是否正确配置密码

### 问题：GitHub Actions 构建失败，提示签名错误

**解决方案**：
1. 检查 GitHub Secrets 是否正确配置
2. 确认 `SIGNING_KEYSTORE_BASE64` 是完整的 base64 编码内容
3. 确认密码与创建密钥库时的密码一致

### 问题：APK 安装时提示 "App not installed"

**解决方案**：
1. 确认 APK 使用正确的签名密钥
2. 如果之前安装过不同签名的版本，需要先卸载旧版本
3. 验证 APK 签名：`keytool -printcert -jarfile app.apk`

## 参考

- [Android 官方签名指南](https://developer.android.com/studio/publish/app-signing)
- [GitHub Actions 文档](https://docs.github.com/en/actions)
