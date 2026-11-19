# LLM 请求协议

`ai/LlmClient.kt` 提供 `suspend complete()` 和冷 `Flow stream()`。`ai/openai/OpenAiCompatibleClient.kt` 实现 OpenAI 兼容的 `POST chat/completions`，复用无日志的 OkHttp 与 IO dispatcher；协议 DTO 不暴露给上层。客户端不访问[配置存储](chat.md#配置存储)。

## 配置与调用

| 项目 | 约定 |
| --- | --- |
| `LlmConfig` | 每次显式传入 API 根路径、Bearer API Key、服务实际 Model ID；无默认站点、密钥或目录 ID 映射 |
| API 根路径 | 例如 `https://gateway.example.com/openai/v1/`；仅追加 `chat/completions`，不补 `/v1`，不要传完整请求地址 |
| URL 安全 | `LlmConfig.validate()` 不联网；仅 HTTPS，拒绝 userinfo、query、fragment。仅本机开发／MockWebServer 允许 `localhost`、`127.0.0.1`、`[::1]` 的 HTTP，不放宽 Android 明文策略 |
| `LlmRequest` | 有序的 system／user／assistant 文本；`temperature`、`maxOutputTokens` 可选，后者映射 `max_tokens` |
| 默认超时 | 连接 15 秒、空闲读取 60 秒、写入 30 秒、完整调用 300 秒；可覆盖为正值 |

不支持 Responses API、非 Bearer 认证或只接受 `max_completion_tokens` 的服务，不自动降级或改写请求。

## 响应与取消

- 普通和 SSE 响应只取 index 0。流每次收集都重新请求；`LlmChunk.content` 是增量。忽略纯 role 事件，结束原因之后仍可收到 usage；缺失用量不补零。只有收到 `[DONE]` 并正常结束才算完整。
- 普通响应限 8 MiB，每个 SSE 事件限 1 MiB。超限、损坏 JSON、缺失必要结构或意外 EOF 都失败；部分文本不能算成功。
- 取消调用或提前结束收集都会取消 OkHttp Call（含等待响应头和读取体）；响应总会关闭。不吞 `CancellationException`，不把收集方异常包装为网络错误。
- `LlmException` 区分 HTTP、网络、超时、协议和远端错误。HTTP 保留状态码及可解析的非负 `Retry-After` 秒数；不读 HTTP 错误体，不传播上游消息、原始异常或凭据。
- **不重定向、不自动重试或重放。** 关闭 OkHttp 连接失败重试，并以 `isOneShot()` 阻止付费 POST 的 follow-up 重放。显式重试可能再次计费。
- 不记录认证头、请求体、提示词或回复，不添加可能暴露这些数据的日志拦截器。开发密钥不得进入 Git、APK、资源或 BuildConfig。

## 测试

本地用 `OpenAiCompatibleClientTest`（MockWebServer＋真实 OkHttp），覆盖协议、SSE、取消、超限和脱敏；虚拟时间不能控制真实 socket 超时。选测方式见[分级测试](testing.md#ai)。

### 线上冒烟

`LlmLiveTest` 显式启用后发两次请求（普通响应和多轮流式），**可能计费**。逐字段优先读同名环境变量，缺失或空白回退仓库根的 `local.properties`。保留原有 `sdk.dir` 等配置，以下仅为占位示例：

```properties
LLM_TEST_BASE_URL=https://your-api.example.com/v1/
LLM_TEST_API_KEY=replace-with-your-key
LLM_TEST_MODEL_ID=your-service-model-id
```

```sh
./gradlew :app:testDebugUnitTest \
  --tests 'io.github.chenlongapps.modelatlas.ai.openai.LlmLiveTest' \
  -PllmLiveTest=true --no-configuration-cache --no-daemon
```

`-P` 只传开关，不传密钥。未启用时不读配置、默认跳过；启用时缺配置失败，不复用任务缓存，核对报告 `skipped=0`。不要提交、分享或打包明文 `local.properties`（可用 `chmod 600 local.properties` 限制权限）。该文件仅供开发机测试，应用运行时不读取。
