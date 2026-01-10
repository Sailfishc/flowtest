# FlowTest 框架实现计划 (v2)

## 项目概述

FlowTest 是一款 **Code-First** 的 Java 集成测试框架，提供流式 DSL 进行测试数据准备、生命周期管理和数据库快照断言。

### 确认的需求
- **发布形式**: 独立 Maven 多模块库
- **技术栈**: JDK 8+, Spring Boot 2.x, 通用 JDBC
- **MVP 范围**: Fixture 数据准备 + 生命周期管理 + 快照断言
- **自动填充**: EasyRandom (不含 AI)
- **DB 断言**: 集成 AssertJ-DB
- **API 风格**: 完全链式，字段注入 `@Autowired TestFlow flow`
- **MVP 模块**: core + spring-boot-starter + junit5 + assertj-db
- **未来扩展**: TestNG、纯 Spring 环境

---

## 目标 API 形态

```java
@FlowTest
@SpringBootTest
class OrderServiceTest {

    @Autowired TestFlow flow;
    @Autowired OrderService orderService;

    @Test
    void testCreateOrder_Success() {
        flow.arrange()
                .add(User.class, UserTraits.vip(), UserTraits.balance(100.00))
                .add(Product.class, ProductTraits.price(50.00))
            .persist()

            .act(() -> orderService.createOrder(
                    flow.get(User.class).getId(),
                    flow.get(Product.class).getId()))

            .assertThat()
                .noException()
                .returnValue(order -> {
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
                })
                .dbChanges(db -> db
                    .table("t_order").hasNumberOfRows(1)
                    .table("t_order").row(0)
                        .value("status").isEqualTo("CREATED")
                );
    }

    @Test
    void testCreateOrder_InsufficientBalance() {
        flow.arrange()
                .add(User.class, UserTraits.balance(10.00))
                .add(Product.class, ProductTraits.price(100.00))
            .persist()

            .act(() -> orderService.createOrder(
                    flow.get(User.class).getId(),
                    flow.get(Product.class).getId()))

            .assertThat()
                .exception(InsufficientBalanceException.class)
                    .hasMessageContaining("余额不足")
                .dbChanges(db -> db
                    .table("t_order").hasNumberOfRows(0)
                    .table("t_audit_log").hasNumberOfRows(1)
                );
    }
}
```

---

## 多模块项目结构

```
flowtest/
├── pom.xml                           # 父 POM
│
├── flowtest-core/                    # 核心引擎 (零框架依赖)
│   └── src/main/java/com/flowtest/core/
│       ├── TestFlow.java             # 主入口 (非静态，可注入)
│       ├── TestContext.java          # 测试上下文
│       ├── fixture/
│       │   ├── Trait.java            # 特征函数式接口
│       │   ├── ArrangeBuilder.java   # Arrange 构建器
│       │   ├── AutoFiller.java       # EasyRandom 封装
│       │   └── EntityMetadata.java   # 实体元数据解析
│       ├── persistence/
│       │   ├── EntityPersister.java  # 持久化接口
│       │   └── JdbcEntityPersister.java
│       ├── snapshot/
│       │   ├── SnapshotEngine.java   # 快照引擎
│       │   ├── TableSnapshot.java
│       │   └── SnapshotDiff.java
│       ├── assertion/
│       │   ├── ActBuilder.java       # Act 阶段
│       │   └── AssertBuilder.java    # Assert 阶段
│       └── lifecycle/
│           ├── CleanupMode.java
│           └── CleanupStrategy.java
│
├── flowtest-assertj-db/              # AssertJ-DB 集成
│   └── src/main/java/com/flowtest/assertj/
│       ├── DbChangesAssertion.java   # 桥接 SnapshotDiff → AssertJ-DB
│       └── FlowTestDbAssertions.java # 静态入口
│
├── flowtest-junit5/                  # JUnit 5 扩展
│   └── src/main/java/com/flowtest/junit5/
│       ├── FlowTestExtension.java    # JUnit 5 Extension
│       └── FlowTest.java             # @FlowTest 注解
│
├── flowtest-spring-boot-starter/     # Spring Boot 自动配置
│   └── src/main/java/com/flowtest/spring/boot/
│       ├── FlowTestAutoConfiguration.java
│       └── FlowTestProperties.java
│
└── flowtest-demo/                    # 示例项目
    ├── src/main/java/.../entity/     # User, Product, Order
    ├── src/main/java/.../service/    # OrderService
    └── src/test/java/
        ├── traits/                   # UserTraits, ProductTraits
        └── OrderServiceTest.java
```

---

## 实现阶段

### Phase 1: 父 POM 与核心骨架

**文件:**
- `pom.xml` (父 POM，定义模块和依赖管理)
- `flowtest-core/pom.xml`

**核心类:**
| 类 | 职责 |
|------|------|
| `Trait<T>` | 函数式接口：`void apply(T entity)` + `default and()` |
| `TestContext` | 存储已创建实体 `Map<Class<?>, List<Object>>`，快照状态 |
| `EntityMetadata` | 反射解析 @Table/@Entity，驼峰转下划线 |
| `AutoFiller` | EasyRandom 封装，排除 ID 字段 |

### Phase 2: 持久化与 Arrange

**核心类:**
| 类 | 职责 |
|------|------|
| `EntityPersister` | 接口：`persist()`, `delete()`, `deleteAll()` |
| `JdbcEntityPersister` | JDBC 实现，动态 INSERT，返回生成的 ID |
| `ArrangeBuilder` | 流式 API：`add()` → `persist()` → 返回 `ActPhase` |

**链式设计:**
```java
ArrangeBuilder.persist() → ActPhase
ActPhase.act(Supplier) → AssertPhase
AssertPhase.assertThat() → AssertBuilder
```

### Phase 3: 快照引擎

**核心类:**
| 类 | 职责 |
|------|------|
| `SnapshotEngine` | `takeBeforeSnapshot()`, `takeAfterSnapshot()`, `computeDiff()` |
| `TableSnapshot` | 单表：maxIdBefore, maxIdAfter, rowCountBefore, rowCountAfter |
| `SnapshotDiff` | 差异结果：newRows, deletedRows, newRowsData |

**快照时机:**
- Before: `act()` 执行前自动拍摄
- After: `assertThat().dbChanges()` 调用时拍摄

### Phase 4: AssertJ-DB 集成

**模块:** `flowtest-assertj-db`

**核心类:**
| 类 | 职责 |
|------|------|
| `DbChangesAssertion` | 包装 AssertJ-DB 的 `ChangesAssert` |
| `FlowTestDbAssertions` | 静态工厂，创建 Changes 对象 |

**集成方式:**
```java
// 内部实现
Changes changes = new Changes(dataSource);
changes.setStartPointNow();  // act() 前
// ... 执行业务逻辑 ...
changes.setEndPointNow();    // assertThat() 时
return new ChangesAssert(changes);
```

### Phase 5: JUnit 5 扩展

**模块:** `flowtest-junit5`

**核心类:**
| 类 | 职责 |
|------|------|
| `@FlowTest` | 组合注解：`@ExtendWith(FlowTestExtension.class)` |
| `FlowTestExtension` | 实现 `BeforeEachCallback`, `AfterEachCallback` |

**职责:**
- beforeEach: 创建 TestContext，注册清理策略
- afterEach: 执行清理（事务回滚或补偿删除）

### Phase 6: Spring Boot Starter

**模块:** `flowtest-spring-boot-starter`

**核心类:**
| 类 | 职责 |
|------|------|
| `FlowTestAutoConfiguration` | 注册 TestFlow, EntityPersister, SnapshotEngine 为 Bean |
| `FlowTestProperties` | 配置：`flowtest.cleanup-mode`, `flowtest.snapshot-tables` |

**spring.factories:**
```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
  com.flowtest.spring.boot.FlowTestAutoConfiguration
```

### Phase 7: Demo 项目

**内容:**
| 文件 | 说明 |
|------|------|
| `User.java`, `Product.java`, `Order.java` | JPA 实体 |
| `OrderService.java` | 包含业务逻辑和异常 |
| `UserTraits.java`, `ProductTraits.java` | Trait 示例 |
| `OrderServiceTest.java` | 完整测试用例 |

---

## 核心依赖

```xml
<!-- flowtest-core -->
<dependencies>
    <dependency>
        <groupId>org.jeasy</groupId>
        <artifactId>easy-random-core</artifactId>
        <version>5.0.0</version>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-jdbc</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>javax.persistence</groupId>
        <artifactId>javax.persistence-api</artifactId>
        <version>2.2</version>
        <scope>provided</scope>
        <optional>true</optional>
    </dependency>
</dependencies>

<!-- flowtest-assertj-db -->
<dependencies>
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-db</artifactId>
        <version>2.0.2</version>
    </dependency>
</dependencies>

<!-- flowtest-junit5 -->
<dependencies>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-api</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>

<!-- flowtest-spring-boot-starter -->
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-autoconfigure</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

---

## 关键设计决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| API 入口 | 实例方法，@Autowired 注入 | 支持 DI，便于 TestNG 扩展 |
| API 风格 | 完全链式 arrange→persist→act→assertThat | 流畅，减少中间变量 |
| 实体获取 | `flow.get(Class)` | 简洁，上下文自动管理 |
| DB 断言 | 集成 AssertJ-DB | 成熟稳定，功能丰富 |
| 模块拆分 | 4 模块 MVP | 解耦，便于未来扩展 TestNG/纯 Spring |
| Trait | 函数式接口 + Lambda | JDK 8 兼容，组合灵活 |
| 持久化 | 纯 JDBC | 通用性强，不依赖 ORM |
| 快照 | MAX(ID) before/after + AssertJ-DB Changes | 双保险 |

---

## 未来扩展路线

| 模块 | 说明 |
|------|------|
| `flowtest-spring` | 纯 Spring 环境支持 (非 Boot) |
| `flowtest-testng` | TestNG 适配器 |
| `flowtest-ai` | AI 智能数据生成 |

---

## 验证计划

1. **单元测试**: 每个核心类在 `flowtest-core` 内测试
2. **集成测试**: 使用 H2 内存数据库
3. **Demo 验证**:
   ```bash
   cd flowtest-demo
   mvn test
   ```
   - 验证 Arrange → Act → Assert 完整链路
   - 验证事务回滚模式
   - 验证补偿清理模式
   - 验证 AssertJ-DB 断言

---

## 文件创建顺序

**Phase 1: 基础设施**
1. 父 `pom.xml`
2. `flowtest-core/pom.xml`
3. `Trait.java`
4. `TestContext.java`
5. `EntityMetadata.java`
6. `AutoFiller.java`

**Phase 2: 持久化**
7. `EntityPersister.java`
8. `JdbcEntityPersister.java`

**Phase 3: 核心流程**
9. `ArrangeBuilder.java`
10. `ActPhase.java`
11. `AssertPhase.java`
12. `TestFlow.java`

**Phase 4: 快照**
13. `TableSnapshot.java`
14. `SnapshotDiff.java`
15. `SnapshotEngine.java`

**Phase 5: 生命周期**
16. `CleanupMode.java`
17. `CleanupStrategy.java`
18. `TransactionalCleanup.java`
19. `CompensatingCleanup.java`

**Phase 6: AssertJ-DB 模块**
20. `flowtest-assertj-db/pom.xml`
21. `DbChangesAssertion.java`

**Phase 7: JUnit 5 模块**
22. `flowtest-junit5/pom.xml`
23. `FlowTest.java` (注解)
24. `FlowTestExtension.java`

**Phase 8: Spring Boot Starter**
25. `flowtest-spring-boot-starter/pom.xml`
26. `FlowTestAutoConfiguration.java`
27. `FlowTestProperties.java`
28. `spring.factories`

**Phase 9: Demo**
29. `flowtest-demo/` 完整项目
