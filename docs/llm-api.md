# LLM API 基础接入

## 当前范围

已实现 OpenAI 兼容的 `POST chat/completions` 文本请求、普通响应、SSE 流式响应，以及 system／user／assistant 有序多轮上下文。不新增依赖，不改变目录同步、数据库或外观持久化。

设置已提供独立[文本聊天 UI](chat.md)，由 `ChatViewModel` 直接管理内存配置、多轮上下文及请求生命周期，没有仅透传的 `ChatRepository`。尚未实现凭据／历史持久化、Function Calling、Agent、后台服务或服务端。

## 代码边界

AI 代码统一放在 `app/src/main/java/io/github/chenlongapps/modelatlas/ai/`，保留现有单 `:app` 模块，不新建 Gradle 模块。以下路径相对于 `modelatlas/`：

| 路径 | 职责 |
| --- | --- |
| `ai/LlmClient.kt` | 协议无关的 `suspend complete()`／冷 `Flow stream()` |
| `ai/LlmModels.kt` | 内存配置、超时、消息、请求、响应、增量及 token 用量 |
| `ai/LlmException.kt` | 可区分且脱敏的错误类型 |
| `ai/openai/OpenAiDtos.kt` | 私有协议 DTO／序列化，不向上层暴露 OpenAI 字段名 |
| `ai/openai/OpenAiCompatibleClient.kt` | OkHttp 请求、协程取消、限长读取及协议映射 |
| `ai/openai/ServerSentEvents.kt` | UTF-8／LF／CRLF、BOM、注释、多行 data 与 SSE 分帧 |
| `ai/di/LlmModule.kt` | 将 `OpenAiCompatibleClient` 绑定为 `LlmClient` |

客户端通过 Hilt 复用现有无日志 OkHttp 和 `@IoDispatcher`，派生客户端共享连接池／调度器，不改变目录客户端的超时。动态 Base URL、认证头和有界 SSE 读取直接使用 OkHttp；不为此重复创建 Retrofit Service／依赖。DTO 使用独立 Kotlinx Serialization `Json`，忽略新增字段，不改变 Models.dev 解析配置。

上层仅依赖 `LlmClient` 与应用模型。未来工具调用需要扩展消息内容和协议映射时再实现，不预建工具注册表、执行器或任意 JSON 参数通道。

## 配置与调用

- `LlmConfig(baseUrl, apiKey, modelId, timeouts)` 每次调用显式传入，不存在默认站点、密钥或模型。模型 ID 是实际服务 API ID，不自动使用目录规范模型 ID。
- `LlmConfig.validate()` 允许表单在提交前提前执行与客户端完全相同的 URL 安全校验，不联网；密钥／模型 ID 继续由构造器校验。客户端仍在每次真实请求前校验，不改变协议或地址拼接。
- `baseUrl` 是**完整 API 根路径**，例如 `https://api.example.com/v1/` 或 `https://gateway.example.com/openai/v1`；仅追加 `chat/completions`，保留自定义前缀，不推断／补充 `/v1`。不要传完整 completions 地址。
- 仅接受 HTTPS，拒绝 userinfo、query、fragment；仅为本机开发／MockWebServer 允许 HTTP 的 `localhost`、`127.0.0.1`、`[::1]`。没有放宽 Android cleartext 策略，外部服务必须 HTTPS。
- 当前只使用 `Authorization: Bearer`。`temperature`／`maxOutputTokens` 可选，默认不发送；后者映射兼容协议 `max_tokens`。只接受 `max_completion_tokens`、Responses API、非 Bearer 认证或特有参数的服务不在当前兼容范围，不自动改写请求／降级重试。
- 默认连接 15 秒、空闲读取 60 秒、写入 30 秒、完整网络调用 300 秒；`LlmTimeouts` 可按服务调整为正毫秒值，不提供无限等待。

业务层注入 `LlmClient`，将配置从受控配置来源传入。以下 `config` 和 `onContentDelta` 均由调用方提供：

```kotlin
val messages = listOf(
    LlmMessage(LlmRole.SYSTEM, "请简洁回答。"),
    LlmMessage(LlmRole.USER, "你好"),
)
val response = llmClient.complete(config, LlmRequest(messages))

val nextTurn = messages + listOf(
    LlmMessage(LlmRole.ASSISTANT, response.content),
    LlmMessage(LlmRole.USER, "继续解释"),
)
llmClient.stream(config, LlmRequest(nextTurn)).collect { chunk ->
    onContentDelta(chunk.content)
}
```

`stream()` 是冷流，每次收集都重新发起请求，不自动共享／缓存。`LlmChunk.content` 是增量，不是累计文本；空内容也可能携带 `finishReason` 或 `usage`。忽略纯 role 事件；允许结束原因之后继续返回 usage，只有收到 `[DONE]` 并正常结束收集才表示完整传输。未主动要求 `stream_options.include_usage`，避免额外兼容要求；服务返回时保留用量，缺失不补零。普通响应和流式响应均仅取 index 0 的结果，不合并多个候选。

## 取消、错误与安全

- 普通调用用 `withContext`、流用 `flowOn` 将阻塞读取移至注入的 IO dispatcher。结构化子协程的取消守卫覆盖等待响应头及阻塞响应体读取，取消调用方或提前结束收集会 `Call.cancel()`；响应始终 `use` 关闭，包括解析失败、超时与收集方异常。不会关闭共享连接池／调度器。
- 不吞掉 `CancellationException`，不将收集方异常包装为网络错误。上层处理 `LlmException` 时仍需让协程取消正常传播。
- `Http` 保留状态码及可解析的非负整数 `Retry-After` 秒数（HTTP 日期暂不解析）；`Network`、`Timeout`、`Protocol`、`Remote` 分别表示连接错误、超时、非法／不完整响应和成功状态中的 error 信封。HTTP 错误体不读取，上游错误 message／code 和原始异常 cause 不向外传播。
- 不重定向，不自动重试。除 `retryOnConnectionFailure(false)` 外，请求体设置 `isOneShot()`，阻止 OkHttp 因 `503 + Retry-After: 0` 等 follow-up 重放付费 POST。用户主动重试仍可能重复计费，应由后续业务明确确认，不对部分流式结果自动续接。
- 普通响应限 8 MiB，每个 SSE 事件限 1 MiB；超限、损坏 JSON、缺少必要结构、意外 EOF／缺少 `[DONE]` 均失败，不把截断内容当成功。上层可以保留已收到的部分文本，但必须标记请求未完成。
- 配置不使用自动暴露字段的 data class，`toString()` 固定脱敏。当前不记录 headers、body、提示词或响应；不得向共享客户端增加包含敏感数据的日志拦截器／EventListener，异常上报也不得额外附上配置。消息和响应本身仍是敏感数据，不能因错误已脱敏就随意记录。
- 不把开发者密钥放入 APK、BuildConfig、资源、Git、SavedState、备份或普通 Preferences。当前配置仅存在于调用方内存；未来用户密钥存储及删除策略需单独设计。工具调用、写入确认及 Agent 成本／循环上限均为后续任务。

## 测试

测试与生产包对应，位于 `app/src/test/java/io/github/chenlongapps/modelatlas/ai/openai/`。`OpenAiCompatibleClientTest` 使用现有 JUnit4、MockWebServer 与真实 OkHttp，覆盖请求序列化、配置切换、多轮消息、未知字段、UTF-8 拆包／多行 SSE／尾部 usage、冷流、HTTP／远端／协议错误、超时、响应头及响应体阶段取消、提前停止收集、资源关闭、超限及敏感内容不传播。此类测试有真实 socket／OkHttp 超时，使用注入的 IO dispatcher 和明确同步信号，不用协程虚拟时间假装控制网络时钟。

```sh
./gradlew :app:testDebugUnitTest \
  --tests 'io.github.chenlongapps.modelatlas.ai.openai.OpenAiCompatibleClientTest'
```

`LlmLiveTest` 为显式启用的线上冒烟，执行两次可能计费的请求：普通响应，以及带上该回复的多轮流式响应。必须指定 `-PllmLiveTest=true` 才执行，默认即使已有凭据仍跳过，避免普通单元测试意外计费。显式开启后如果缺少必要配置则失败，不把未配置误记为线上通过。不会输出配置、提示词或响应。

在根目录 `local.properties` 保留已有的 `sdk.dir` 等内容，增加以下三项即可一次保存、重复使用（示例不含真实密钥）：

```properties
LLM_TEST_BASE_URL=https://your-api.example.com/v1/
LLM_TEST_API_KEY=replace-with-your-key
LLM_TEST_MODEL_ID=your-service-model-id
```

测试按字段优先读取同名环境变量，未设置或空白时回退到本地配置，便于 CI 覆盖。配置由测试进程中的 `LlmLiveTestConfig.kt` 读取，Gradle 的测试配置只传入非敏感文件路径和启用开关，不把密钥加入系统属性、任务输入或 BuildConfig；不依赖测试进程当前工作目录。未开启时不会读取配置文件。`LlmLiveTestConfigTest` 使用临时文件和假密钥，覆盖本地读取、逐字段覆盖、空白回退、配置缺失及完整环境变量不读取本地文件，不接触真实配置。

保存后执行：

```sh
./gradlew :app:testDebugUnitTest \
  --tests 'io.github.chenlongapps.modelatlas.ai.openai.LlmLiveTest' \
  -PllmLiveTest=true \
  --no-configuration-cache --no-daemon
```

开启线上测试时，Gradle 禁止复用测试任务的 up-to-date／构建缓存结果，每次都真实执行。命令中的 `-P` 仅传布尔开关，**不要用 Gradle `-P`／命令行字面量传密钥**。继续关闭配置缓存并使用一次性 Gradle daemon，减少构建侧凭据残留风险。

`local.properties` 已被 Git 忽略，但仍是明文文件，不应提交、分享或作为应用资源打包；建议限制文件权限（macOS／Linux 可用 `chmod 600 local.properties`）。这仅是开发机测试配置，不是应用内凭据存储功能。报告中必须核对 `skipped=0`，不能把默认跳过记为线上通过。执行结果见[开发历史](archive/development-history.md#2026-11-11-线上测试支持本地配置)。
