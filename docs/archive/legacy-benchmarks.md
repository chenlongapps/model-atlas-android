# 旧独立评测协议（仅兼容回归）

> 本文记录被 Atlas 整包替代的整数版本协议，不是当前生产同步。旧样本与 DAO 保留用于测试；Application 不再启动旧同步器。当前规则见 [Atlas 契约](../atlas-data.md)，历史实际执行见[开发历史](development-history.md)。

## 数据与版本

- [旧随包样本](../../app/src/androidTest/assets/benchmark-contract-v2/benchmarks.json)使用 `schemaVersion: 1`、递增正整数 `version`、`scores` 数组；当时每条要求唯一非空 `id`、目录规范 `modelId`、非空 `benchmarkId` 及有限数值 `score`。可选字段沿用 `BenchmarkScore`，标题直接用 `benchmarkId`；`sourceType: "test"` 在界面标记虚构，真实旧 v2 无此类型。
- `cost` 接受有限数值或 null，缺失不视为零。币种、单位和口径未定，不用于计算或详情展示。
- `benchmark-manifest.json` 保存旧导出的原字节摘要，当前 `benchmarks.json` 已是定义字典，不能交给旧 `BenchmarkSyncRunner`。
- 当时首次 IO 导入后再检查远程版本，有本地数据不等网络；已有独立成绩时保留并记版本 0，已初始化空表不重复导入。合法空快照可全量替换，调试修改可能被更新覆盖。

## 旧下载契约

- 旧 `benchmark_manifest_url` 曾指向 `https://chenlongapps.github.io/model-atlas-data/manifest.json`；**同地址现已使用新协议，不能以旧解析器继续请求**。
- 旧清单字段为 `schemaVersion`、`version`、绝对 HTTPS `url`、`sha256`，快照版本须与清单相同且高于本地。随包 v1 可升 v2，随包 v2 只能升 v3+；测试候选 v3 不代表线上已发布。
- 当时要求先发快照再发清单，或原子部署站点；版本／成绩同事务提交。摘要只检一致性，不是数字签名或来源认证。
- 清单最多 64 KiB，快照最多 16 MiB／100,000 条；流式限长、严格 UTF-8、只 HTTPS、不跟随重定向。网络、校验、解析或写入失败保留旧成绩及版本，不轮询、不在退出后执行后台任务。
- `BenchmarkSyncRunner` 可注入下载／存储、互斥串行；`BenchmarkHttpSource` 与随包读取均限长，取消不是失败。历史 `BenchmarkSync` 在 IO 记录非取消错误，不阻塞详情；此启动链路已停用。

## 旧真实来源与许可边界

首批五条成绩来自三模型的 DeepSeek-R1 README 固定修订 `0cf78561f1d51c84a21b2190626b21116d5c68bb`，项目为 MMLU-Pro EM／MATH-500 pass@1，经维护者批准分发。已知执行配置曾通过 `harness` 显示，不把建议设置当实际执行，不填造日期、工具或推理档位；`mmlu-pro-em` 不与待审 Overall Accuracy 合并。

`benchmark-attributions.json` 和 `third_party/deepseek-r1/MIT.txt` 一起导出，来源页保留出处、归属、修改说明及 MIT 全文。**旧远程协议只更新成绩，不下载归属旁车**；文档许可不代表权重／数据集许可，新增来源前仍需复核客户端分发要求。历史“首次离线仅有评测、模型仍需联网”只适用于旧阶段，当前已导入 Atlas 真实模型包。

## 回归入口

`BenchmarkUpdateTest`、`BenchmarkSyncRunnerTest`、`BenchmarkHttpSourceTest`、`BenchmarkIntegrationTest` 保留旧清单、原字节、版本替换、离线重开、摘要拒绝和失败回滚回归；注入 HTTPS 响应不等于真实线上更新。不要为普通榜单 UI 改动默认运行这套兼容测试，选测见[测试说明](../testing.md)。
