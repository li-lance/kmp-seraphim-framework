# Project Context

## Purpose

KMP Seraphim Workbench 是一个 manifest 驱动的多项目工作台。它生成可独立维护的产品工程，并用真实参考产品验证构建策略、模板和跨平台共享逻辑。

首个参考产品 `daily-board` 是个人日常任务看板。它必须在无账号、无网络情况下完整可用，并在后续阶段增加账户与多设备同步。

## Domain Language

- **Workbench**：维护工具链策略、生成器、模板和参考产品的仓库。
- **Product**：由 manifest 描述并由工作台生成的独立应用工程。
- **Reference product**：持续维护、用于证明工作台能力真实可用的产品；当前为 `daily-board`。
- **Manifest**：只描述当前产品拓扑与能力选择的 `project.yaml`，不记录路线图阶段。
- **Template catalog**：已被真实产品证明且通过干净生成验证的模板集合。
- **Platform kit**：只封装工具链、编译、质量和测试策略的 included build；不决定产品 Module 拓扑。
- **Render**：生成临时文件树、完成结构校验并原子发布的过程。
- **Certify**：对已生成产品执行 Gradle、Xcode 和 Web 构建验证的过程。
- **Adapter**：把共享 Interface 转换为平台 UI、存储、网络或 JavaScript 可消费形式的实现。
- **Operation**：可重试、具有稳定标识的离线业务变更。
- **Cursor**：服务端变化流中客户端已持久化应用的位置。
- **Conflict record**：无法安全自动合并、必须保留并显式解决的冲突。

## Invariants

1. UI 不在平台之间共享。
2. Android App、iOS App、Desktop App 和 Web UI 都是独立入口。
3. 未选择的平台不得进入生成产品的 Gradle Module 图。
4. Manifest 不允许嵌入 Gradle 或 shell 代码。
5. 生成器不得覆盖非空目标目录。
6. Render 成功只代表结构事务成功；Certify 成功才代表平台构建通过。
7. 独立导出的产品必须携带锁定的工作台来源信息和可用的 platform-kit。
8. OpenAPI 只描述传输模型；同步顺序、幂等、cursor 和冲突规则由独立协议规范定义。
9. 只有真实复用产生稳定 Seam 后才创建跨产品共享 Module。
10. Phase 1 产品在没有 Phase 2 后端时仍必须完整可用。

## Delivery Sequence

- **Phase 0**：工作台基线、兼容工具链、manifest、生成器、Android+iOS 最小纵向切片。
- **Phase 1**：四端本地任务看板、持久化、Desktop、Web/Wasm、独立 CLI 导出。
- **Phase 2**：账户、Ktor/PostgreSQL、离线同步与冲突处理。
- **Phase 3**：模板产品化、迁移工具、第二个真实产品。

