# AI 配置持久化

## 当前实现

设置 → AI 助手 → 会话配置，将用户显式应用的配置保存在本机。只保存一套配置；配置草稿、输入和对话仍只在内存中，不实现历史持久化、多服务配置或后台请求。

`ai/config/` 的 `LlmConfigRepository` 负责真实配置读写，不是聊天请求透传层。`DataStoreLlmConfigRepository` 通过单例 `LlmPreferencesStore` 使用独立 Preferences DataStore，不混入外观 `SettingsRepository`；`ai/di/LlmConfigModule.kt` 提供 Hilt 绑定。读写、Keystore 操作在 IO dispatcher 上执行，仓库 Mutex 串行协调读取、整份保存和清除。

## 磁盘格式与密钥

应用私有文件 `files/datastore/llm_config.preferences_pb` 保存：

| 字段 | 内容 |
| --- | --- |
| `version` | 当前格式版本 `1` |
| `base_url`、`model_id` | 校验后的完整 API 根路径、服务模型 ID |
| `api_key_iv` | 12 字节随机 IV 的 Base64 |
| `api_key_ciphertext` | API Key 的 AES-GCM 密文及认证标签的 Base64 |

`KeystoreApiKeyCipher` 在 AndroidKeyStore 中生成 AES-256 密钥，别名为 `modelatlas.ai.api-key.v1`，仅允许 GCM／NoPadding 加解密、要求随机化加密，不要求生物认证。不导出密钥，不强制 StrongBox，也不承诺所有设备都使用同一硬件安全级别。每次加密由 Provider 生成新 IV，认证标签为 128 位；格式版本、URL、Model ID 以长度前缀编码后作为 AAD，篡改端点或模型不能继续解密已保存密钥。

先校验并加密，再在一次 DataStore `edit` 中写入全部字段，不存在明文密钥 Preferences，也没有明文回退。API Key 在请求和编辑期间仍需存在进程内存，不能将磁盘加密视为抵御已受控应用进程的保证；敏感类型继续使用脱敏 `toString()`，不记录原始异常、凭据、提示词或回复。

旧版本没有落盘配置，无需迁移；不读取运行时 `local.properties`、BuildConfig 或资源中的密钥。

## 恢复、提交与失败

- `ChatViewModel` 创建时异步读取一次；读取完成前禁止打开配置、发送或重试，读取／恢复不联网。Activity 内离页重入仍复用现有 ViewModel，不覆盖内存对话。
- 点击“应用”才使用既有 `LlmConfig.validate()` 校验，并持久化。保存中禁止编辑、重复提交、关闭弹窗和发送；保存成功才替换运行配置、关闭弹窗，只有配置内容变化才清空对话。取消编辑不写入。
- 保存失败保留原运行配置、对话和未提交草稿，允许再次应用或取消；错误只向 UI 传递存储／密钥不可用枚举，不传播 Provider 或磁盘异常的原始内容。
- 读取失败不等同于首次未配置，不自动覆盖或清除数据。UI 禁用配置覆盖及请求，提供重试读取和确认清除；失败后重新读取成功可继续使用已保存配置。读取密文时密钥丢失绝不生成替代密钥。
- “清除已保存配置”须二次确认。先清空 DataStore，再删除 Keystore 别名，成功后清理内存配置、输入、对话和草稿。损坏的 protobuf 只有在显式确认清除期间才允许替换；平常 corruption handler 继续抛出错误。已开始的文件清除在取消时也完成并关闭恢复权限。
- 清除如果部分完成后失败，不继续使用可能失效的旧凭据；清空内存运行配置和对话，阻止请求并提供重试读取／再次清除。此操作不会删除服务商账户或撤销远端 API Key。

## 备份与隔离

`backup_rules.xml`、`data_extraction_rules.xml` 同时排除此配置文件的旧式备份、云备份和设备迁移；其他数据备份规则不变。Keystore 密钥不能随配置迁移，换机需重新填写。Debug 和 Release 使用各自 application ID 的私有存储与 Keystore 命名空间，即使别名相同也不会共用凭据。

## 测试与边界

- `LlmConfigRepositoryTest`：真实 DataStore＋JCE AES-GCM，验证文件重开、整份并发提交、无明文、篡改、密钥丢失、加密失败不覆盖、取消传播及损坏文件显式恢复。
- `ChatViewModelTest`：延迟恢复／保存、防重入、失败保留、重新读取、确认删除及新 ViewModel 只恢复配置，不自动联网。
- `KeystoreConfigTest`：设备真实 AndroidKeyStore、不可导出密钥、随机 IV、认证失败、密钥删除和生产存储文件重开；使用随机测试别名和 cache 文件，绝不删除用户真实配置或生产别名。
- `ChatScreenTest`／`ChatNavigationTest`：配置状态、失败／重试、确认清除及导航；`AppearanceBackupRulesTest` 同时校验两种设置的备份排除范围。

实际执行结果见[开发历史](archive/development-history.md)，真实跨厂商 Keystore、系统备份／迁移和进程重启验收以 [TODO](../TODO.md) 为准；文件／对象重开不冒充真实进程恢复。
