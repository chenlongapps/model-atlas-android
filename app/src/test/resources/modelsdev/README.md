# Models.dev 解析测试样本

- 结构依据：2026-10-19 实际读取的 `https://models.dev/models.json?type=all` 与 `https://models.dev/api.json?type=all`。
- `models.json` 与 `api.json` 均为**合成测试数据**，不是完整响应或真实模型、服务商及报价快照；`example.com` 链接仅用于测试。
- 样本覆盖已观察到的嵌套字段，并加入跨服务商重复模型 ID、任意请求参数和未知模式名等边界情况。测试无需联网，不随在线模型数量变化。
- 缺失字段、显式空值、未知字段、超出 Int 的限制值和异常类型由测试内的独立 JSON 片段覆盖。
