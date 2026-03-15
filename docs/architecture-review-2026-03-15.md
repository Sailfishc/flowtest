# FlowTest V2 架构评审报告

**日期**: 2026-03-15  
**范围**: 架构设计 + 用户友好度 + 迭代优化方向

## 总体评价

FlowTest V2 的架构方向非常好。**Observation-first** 的核心理念清晰,DSL 设计可读性强,Entity-first-citizen 的体验在同类框架中是差异化优势。整体已经达到了较高的工程成熟度。

以下按优先级排列需要关注的优化方向。

---

## P0 — 核心架构问题

### 1. 观察资源的身份标识过于依赖字符串

**问题**: `ObservationSpec`、`ObservationSnapshot`、`ObservationDiff` 全部以 `resourceName` (String) 作为唯一标识。

**具体风险**:
- `ObservationSpec.fixture(handle)` 使用 `handle.getType().getName()` 作为 resourceName（ObservationSpec.java:53）
- `ObservationSpec.entity(entityType)` 也使用 `entityType.getName()`（ObservationSpec.java:102）
- 同一个 Entity 类型的两个不同 route scope 的观察会产生 resourceName 冲突
- `DefaultScenarioBuilder.mergeObservations()` 使用 `explicitResourceNames.contains()` 去重（DefaultScenarioBuilder.java:310-335），同名资源会被静默覆盖

**建议**: 引入 `ObservationRef`（逻辑观察引用），包含:
- 逻辑别名
- 物理资源目标（表名/Entity 类型）
- route scope
- fixture handle 关联

### 2. ScenarioCompiler 校验不够充分

**现状**（ScenarioCompiler.java:32-84）: 只校验了:
- 重复 fixture handle
- 重复 fixture alias
- fixture 引用不存在
- route required 但未提供 route

**缺失的高价值校验**:
- 重复 observation resourceName（会导致静默覆盖）
- 同 Entity 类型多 observation 的歧义
- `ROLLBACK` / `CUSTOM_COMPENSATOR` 在 JDBC 后端不可用时的提前报错
- fixture expectation 引用同类型但无 alias 时的歧义警告

### 3. `before()` 方法实现脆弱

**证据**（DefaultScenarioBuilder.java:712-727）:
```java
public FixtureExpectationSpec<T> before(final FixtureAssertion<T> assertion) {
    plan.addFixtureExpectation(new FixtureStateExpectation<T>(handle, ...));
    // Actually, let's use fixtureChange to get before access
    plan.fixtureExpectations.remove(plan.fixtureExpectations.size() - 1);  // ← 脆弱
    plan.addFixtureChangeExpectation(...);
    return this;
}
```

先添加再删除最后一个元素,然后转为 change expectation。这是一个"先 hack 再弥补"的实现,说明数据模型缺少 `before-state` 的一等抽象。

---

## P1 — 模块边界与可扩展性

### 4. fixture 模块对 observe-rdbms 存在反向依赖

**证据**: `JdbcFixtureExecutor` 直接导入了:
- `com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationRegistry`
- `com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcEntityRegistration`
- `com.github.sailfishc.flowtest.v2.observe.rdbms.FlowTestDataSourceRegistry`

架构文档定义 fixture 和 observe 是平行模块,但实际 fixture-jdbc 依赖了 observe-rdbms 的元数据能力。

**建议**: 将共享的实体元数据抽取到 `flowtest-v2-spec` 或新建 `flowtest-v2-metadata` 模块。

### 5. CleanupPolicy 枚举承诺了未实现的能力

**证据**（CleanupPolicy.java）: 声明了 5 个策略,但:
- `ROLLBACK`: ScenarioExecutor.java:227 直接 `throw new UnsupportedOperationException`
- `CUSTOM_COMPENSATOR`: JdbcObservationExecutor.java:67 直接 `throw new UnsupportedOperationException`
- 没有任何 SPI 支持自定义补偿逻辑

**建议**: 短期在 Javadoc/ScenarioCompiler 中标注 `@Experimental` 或编译期拒绝;中期实现 `ROLLBACK` 对接外部事务管理器,`CUSTOM_COMPENSATOR` 提供 `CleanupHandler` SPI。

### 6. JdbcObservationRegistry 线程安全不完整

**证据**: 
- `registerEntityIfAbsent()` 加了 `synchronized`（JdbcObservationRegistry.java:106）✅
- 但 `registerTable()`、`registerEntity()` 内部直接操作 `LinkedHashMap`（非同步）
- 如果共享 `ScenarioExecutor` 实例跨并行测试,`resourcesByName`/`resourcesByType` 可能并发写入

**建议**: 要么全部切换为 `ConcurrentHashMap`,要么明确文档声明 registry 必须在测试执行前冻结配置。

---

## P2 — 用户体验优化

### 7. DSL 中 explicit observe 与 auto-inferred 的语义差异不够直观

**现象**: `EntityFirstCitizenTest` 第 394-421 行展示了一个微妙的行为:
- 使用 `observe()` 会覆盖 fixture-backed 的自动推导
- 变成 `WATCH_ONLY` 模式而非 `FIXTURE_BACKED`
- 用户需要同时提供 `dynamicTableBy()` 和 `route()`

这对新用户来说是一个容易踩的坑。

**建议**: 在 `observe()` DSL 中增加 `.forFixture(handle)` 形式,让用户显式声明"我在补充路由,但这仍然是 fixture-backed 观察"。

### 8. 错误报告缺少阶段信息

`ScenarioExecutor.execute()` 中,action 失败、verification 失败、cleanup 失败都可能抛出异常,但异常信息中没有标注"在哪个阶段出错"。

**建议**: 引入 `ScenarioExecutionException`,包含:
- scenario name
- 出错阶段 (prepare / beforeCapture / action / afterCapture / verify / cleanup)
- primary cause + suppressed causes
- observation summary (便于调试)

### 9. JdbcFixtureExecutor 构造函数重载过多

**证据**: JdbcFixtureExecutor.java 有 **9 个构造函数**,参数组合复杂。

**建议**: 引入 Builder 模式:
```java
JdbcFixtureExecutor.builder()
    .dataSource(ds)
    .observationRegistry(registry)
    .adapterRegistry(adapters)
    .build();
```

### 10. 缺少用户决策指南文档

测试用例写得很好（EntityFirstCitizenTest 是优秀的示例），但缺少一个简洁的决策矩阵：

| 场景 | 需要 given()? | 需要 observe()? | 需要 route? |
|------|:---:|:---:|:---:|
| 单表 fixture + 修改 | ✅ | ❌ 自动推导 | ❌ PK 自动 |
| 多表 fixture + watch-only | ✅ | ❌ 自动推导 | ❌ |
| act-only（无 fixture） | ❌ | ✅ 必须显式 | 看情况 |
| 非 PK 路由过滤 | ✅/❌ | ✅ 必须显式 | ✅ |
| 动态表 + fixture | ✅ | ❌ 自动推导 | ❌ trait 设置 bucket |
| 动态表 + act-only | ❌ | ✅ 必须显式 | ✅ dynamicTableBy |

---

## P3 — 长期演进方向

### 11. Spring Boot 配置模型增强
- `spring.factories` 对 Boot 3.x 不兼容（需要 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）
- 配置属性可以增加 enum 类型支持
- 缺少 `Customizer<JdbcObservationRegistry>` 类型的扩展 Bean

### 12. 性能优化空间
- `JdbcObservationRegistry` 每次 `resolve()` 都做反射注册检查
- `GenericJdbcFixtureEntityAdapter` 的 bean introspection 可以缓存
- `closeQuietly()` 方法签名声明了 `throws Exception`（JdbcObservationExecutor.java:329），实际 quiet close 不应该传播异常

### 13. 测试覆盖补充
建议增加:
- 重复 observation resourceName 的测试
- 同 Entity 类型多 fixture 的边界测试
- 并行测试执行的安全性测试
- 不支持的 CleanupPolicy 的友好错误信息测试

---

## 总结

FlowTest V2 的架构质量已经很高。核心的 observation-first 理念、DSL 设计、Entity-first-citizen 体验是明确的竞争优势。

**最高优先级的三件事**:
1. 解决 observation 身份标识的唯一性问题（影响正确性）
2. 增强 ScenarioCompiler 的编译期校验（影响用户体验）
3. 理清 fixture ↔ observe-rdbms 的模块边界（影响长期可维护性）

这三项做完后,框架的健壮性和可扩展性会有质的提升。
