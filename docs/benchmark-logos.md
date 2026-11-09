# 评测 Logo 素材

## 已实现

2026-11-09 从维护者提供的 `model-atlas/res/benchmark-logo/` 收录，源目录的 `README.md` 与 `sources.json` 记录了官方网址、提取方式和摘要。使用 Android SDK `Svg2Vector`（sdk-common 32.4.1）转换为 `app/src/main/res/drawable/ic_benchmark_*.xml`，未引入运行时 SVG 依赖或网络请求。

11 个明确的评测定义 ID 对应 10 份品牌矢量及 1 份独立中性兜底图。Intelligence Index 与 GDPval-AA 的原 SVG 完全相同，显式共用资源；Terminal-Bench-Science 采用官网图标，不收录备用超宽完整字标。白色圆角底、原色、完整画布与宽高比保留，长字标使用较宽底座，不裁成正方形。

| 评测定义 ID | 资源后缀（`ic_benchmark_`） | 官方素材来源 | 原 SVG SHA-256 |
| --- | --- | --- | --- |
| `artificial-analysis-intelligence-index-v5`、`gdpval-aa-v2.1` | `artificial_analysis` | [Artificial Analysis 主办方图标](https://artificialanalysis.ai/img/logo-icon.svg) | `132ad19b4ea8ac29a754e86f37c9b3451ca3a2f4a999c5969d4ef47e32c778aa` |
| `automationbench-v1.0.6` | `automationbench` | [Zapier 官网页头字标](https://zapier.com/) | `8a9258f321cb099f07377374597112ae5128d638ab009dbd75b678b33a32db6c` |
| `chartography` | `chartography` | [Surge AI 字标](https://cdn.prod.website-files.com/68dc970bd6e945ea3fb0f426/68e340b3dfa20e2c97749a3a_Logo.svg) | `3de51ea915c0fc41b1bf57b1f0d24bc58a81667ecc157d8de757dd288b5307fe` |
| `deepswe-v1.1` | `deepswe` | [DeepSWE favicon（Datacurve 标识）](https://deepswe.datacurve.ai/favicon.svg) | `b25c9ac39910579ceb0273edd74691efa93824fe720da7683900cd2578ab3968` |
| `humanitys-last-exam` | `humanitys_last_exam` | [HLE 原版 Logo](https://lastexam.ai/_next/static/media/hle_logo.42325f6d.svg) | `395c09a86fed4c2e3842a72ca09c0abdeadc7a8193fd344314e72ef29190690d` |
| `osworld-v2.1` | `osworld` | [OSWorld 官网 PNG favicon，本地描摹](https://osworld-v2.xlang.ai/static/images/favicon.png) | `2194636c20eb254a601a56043c76158325b72f43207616fe00b39623bc9e11ed` |
| `swe-bench-pro-v2` | `swe_bench_pro` | [Scale Labs 主办方字标](https://labs.scale.com/images/logo-scale-labs.svg?v=2) | `2e3e087c3928df0dbf5b76190ea2ca93d441b37bb18e1ccda2100d330e191a85` |
| `terminal-bench-science-v0.1` | `terminal_bench_science` | [Terminal-Bench-Science 官网 SVG favicon](https://terminal-bench-science.ai/favicon.svg?v=7) | `b0de02ced5a65d67f5ba0d35910a72654f641c27a02ded022694899cd7d15c39` |
| `terminal-bench-v4` | `terminal_bench` | [Terminal-Bench 官网 PNG favicon，本地描摹](https://www.tbench.ai/fav.png) | `ef9da853ea56a78eaf1e01c9c99fa0d25c87890eb781bafb58a15b4dbcb10c19` |
| `vals-index-v2.2` | `vals_index` | [Vals AI 主办方字标](https://www.vals.ai/Logos/vals-full.svg) | `8fec037b8b2336bc54b0ad80a42adcdf2777fa900f39ec51c9518708c9790bf1` |

OSWorld、Terminal-Bench 的 SVG 是源目录用 VTracer 0.6.15 从官方 PNG 描摹的近似轮廓，并非官方直接发布的矢量；本次只做 Android 格式转换。各标识版权归原权利方，不将品牌素材推定为 Models.dev MIT 授权，也不表示主办方背书或成绩已经核验。发布前仍需复核素材使用条款。

## 匹配与维护

- `ModelDetailMapper` 将已有成绩的 `benchmarkId` 传给应用模型，不新增数据库列；`BenchmarkLogo.kt` 精确映射 ID，不按名称猜测、不自动剥离版本。
- 未匹配定义的 `unmapped:` 成绩、新版本／未知 ID 和无 ID 数据显示中性评测图标，绝不借用模型 LAB、API 服务商或相似评测的 Logo。
- 新增版本或素材时同步更新映射、来源记录和 `BenchmarkLogoTest`；`ModelBenchmarkCardTest` 验证所有资源加载、深浅主题保留原色／白底和窄屏双倍字号 RTL。
