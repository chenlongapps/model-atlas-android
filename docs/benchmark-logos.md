# 评测 Logo 素材

## 资源与来源

素材来源记录在 `model-atlas/res/benchmark-logo/` 的 `README.md` 和 `sources.json`。用 Android SDK `Svg2Vector` 转为 `app/src/main/res/drawable/ic_benchmark_*.xml`，不在运行时下载；底座和留白见[设计规范](Design.md#评测榜单)。

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

OSWorld、Terminal-Bench 由官方 PNG 用 VTracer 0.6.15 描摹，并非官方矢量。标识归原权利方，不能推定适用 Models.dev MIT 授权，不表示主办方背书；使用条款仍待复核。

## 匹配与维护

- `BenchmarkLogo.kt` 按定义 ID 精确映射，不猜名称或剥离版本。未知、无 ID 或 `unmapped:` 成绩用中性兜底图，不借用 LAB 或服务商 Logo。
- 新增素材时同步更新映射和来源，选测 `BenchmarkLogoTest`、`ModelBenchmarkCardTest`。
