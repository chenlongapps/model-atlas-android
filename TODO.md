# ModelAtlas Android TODO

- [ ] 优化模型参数量的显示 将两个参数量合并 例如：671B-A37B
    
## 后续

- [ ] 补缺失 LAB Logo，核对来源／使用条件，保留兜底图。
- [ ] 核对局部设计 Token：`ModelCardSkeleton.kt` 的部分圆角与主题形状当前取值相同，却是独立字面量；先按语义确认可复用项。`LabLogo.kt` 与评测总览的 `BenchmarkCatalogLogo` 分别用比例圆角和固定圆角实现近似底座；如确认需同步，集中到共享品牌底座并保留宽字标变体，不并入普通容器形状。
