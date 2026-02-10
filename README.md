# FlowTest

FlowTest 是一个 **Code-First** 的 Java 集成测试框架，提供流式 DSL 来简化数据库相关的测试。它遵循 **Arrange-Act-Assert** 模式，支持自动生成测试数据、自动追踪数据库变更、测试后自动清理。

```java
@FlowTest
@SpringBootTest
class OrderServiceTest {

    @Autowired TestFlow flow;
    @Autowired OrderService orderService;

    @Test
    void testCreateOrder() {
        flow.arrange()
            .add(User.class, UserTraits.vip(), UserTraits.balance(1000))
            .add(Product.class, ProductTraits.price(200))
            .persist()
            .act(() -> orderService.createOrder(
                flow.get(User.class).getId(),
                flow.get(Product.class).getId()))
            .assertThat()
                .noException()
                .created(Order.class)
                .entity(User.class)
                    .has(User::getBalance, BigDecimal.valueOf(800))
                .and()
                .newRow(Order.class)
                    .has(Order::getStatus, OrderStatus.CREATED);
    }
}
```

---

## 目录

- [快速开始](#快速开始)
- [核心概念](#核心概念)
  - [Arrange-Act-Assert 流程](#arrange-act-assert-流程)
  - [Trait 特征系统](#trait-特征系统)
  - [数据自动填充](#数据自动填充)
  - [实体元数据解析](#实体元数据解析)
- [断言 API](#断言-api)
  - [异常断言](#异常断言)
  - [返回值断言](#返回值断言)
  - [数据库变更断言](#数据库变更断言)
  - [实体状态断言](#实体状态断言)
  - [新行断言](#新行断言)
  - [快捷断言](#快捷断言)
- [清理策略](#清理策略)
- [多数据源支持](#多数据源支持)
- [Mockito 集成](#mockito-集成)
- [配置参考](#配置参考)
- [常见问题](#常见问题)

---

## 快速开始

### 1. 引入依赖

Spring Boot 项目只需引入一个 starter，自动包含核心框架、JUnit 5 扩展、AssertJ-DB 集成：

```xml
<dependency>
    <groupId>com.github.Sailfishc</groupId>
    <artifactId>flowtest-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

Starter 自动配置以下 Bean，无需手动创建：

| Bean | 作用 |
|------|------|
| `DataFiller` | 自动填充实体字段（默认 Instancio，可选 EasyRandom） |
| `EntityPersister` | 通过 JDBC 插入/删除实体 |
| `SnapshotEngine` | 数据库快照与变更追踪 |
| `TestFlow` | 测试入口，注入到测试类中使用 |

如需 Mockito 集成或 TestNG 支持，额外引入对应模块：

```xml
<!-- Mockito 集成（可选） -->
<dependency>
    <groupId>com.github.Sailfishc</groupId>
    <artifactId>flowtest-mockito</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>

<!-- TestNG 支持（可选，与 JUnit 5 二选一） -->
<dependency>
    <groupId>com.github.Sailfishc</groupId>
    <artifactId>flowtest-testng</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

### 2. 写第一个测试

```java
@FlowTest                      // 启用 FlowTest，默认使用事务回滚清理
@SpringBootTest
class UserServiceTest {

    @Autowired TestFlow flow;   // 注入测试入口
    @Autowired UserService userService;

    @Test
    void testDeductBalance() {
        flow.arrange()
            .add(User.class, u -> u.setBalance(BigDecimal.valueOf(1000)))
            .persist()
            .act(() -> userService.deductBalance(
                flow.get(User.class).getId(),
                BigDecimal.valueOf(300)))
            .assertThat()
                .noException()
                .entity(User.class)
                    .has(User::getBalance, BigDecimal.valueOf(700));
        // 测试结束后自动回滚，数据库无残留
    }
}
```

关键点：
- `@FlowTest` 注解启用框架，处理生命周期
- `@Autowired TestFlow flow` 注入测试入口
- `add()` 自动填充实体的所有字段，你只需设置测试关心的字段
- `persist()` 将实体写入数据库
- `act()` 执行被测业务逻辑
- `assertThat()` 开始断言

---

## 核心概念

### Arrange-Act-Assert 流程

FlowTest 的核心是三阶段的链式 API：

```
flow.arrange()          → ArrangeBuilder    准备测试数据
    .persist()          → ActPhase          写入数据库
    .act(...)           → AssertPhase       执行业务逻辑
    .assertThat()       → AssertBuilder     验证结果
```

#### Arrange 阶段 —— 创建测试数据

```java
// 基本用法：添加一个实体，字段自动填充
.add(User.class)

// 应用 Trait（可复用的配置函数）
.add(User.class, UserTraits.vip(), UserTraits.balance(100))

// Lambda 配置
.add(User.class, u -> {
    u.setUsername("alice");
    u.setBalance(BigDecimal.valueOf(1000));
})

// 别名 —— 同类型多个实体时用于区分
.add("buyer", User.class, u -> u.setBalance(BigDecimal.valueOf(1000)))
.add("seller", User.class, u -> u.setBalance(BigDecimal.valueOf(500)))

// 批量添加
.addMany(Product.class, 5)

// 批量添加 + 按索引配置
.addMany(Product.class, 3, (p, index) -> p.setPrice(BigDecimal.valueOf(100 * (index + 1))))
```

`persist()` 将实体写入数据库并记录生成的 ID；`build()` 只创建实体不写库（用于调试）。

#### 获取已创建的实体

```java
flow.get(User.class)               // 获取第一个 User
flow.get(User.class, 0)            // 按索引获取
flow.get("buyer", User.class)      // 按别名获取
flow.getAll(User.class)            // 获取所有 User（返回 List）
```

#### Act 阶段 —— 执行业务逻辑

```java
// 无返回值
.act(() -> orderService.cancelOrder(orderId))

// 有返回值（可在 Assert 阶段验证）
.act(() -> orderService.createOrder(userId, productId))
```

`act()` 会捕获业务逻辑抛出的异常，供后续 `exception()` 断言使用。

### Trait 特征系统

Trait 是一个函数式接口，用来定义可复用、可组合的实体配置。**建议为每个实体创建一个 Traits 类**：

```java
public class UserTraits {

    public static Trait<User> vip() {
        return u -> u.setLevel(UserLevel.VIP);
    }

    public static Trait<User> balance(double amount) {
        return u -> u.setBalance(BigDecimal.valueOf(amount));
    }

    public static Trait<User> named(String name) {
        return u -> u.setUsername(name);
    }

    // 组合 Trait
    public static Trait<User> richVip() {
        return vip().and(balance(10000));
    }
}
```

使用方式：

```java
// 可变参数 —— 按顺序应用
.add(User.class, UserTraits.vip(), UserTraits.balance(1000))

// .and() 链式组合
.add(User.class, UserTraits.vip().and(UserTraits.balance(1000)))

// Trait.compose() 静态组合
.add(User.class, Trait.compose(UserTraits.vip(), UserTraits.balance(1000), UserTraits.named("alice")))
```

### 数据自动填充

`DataFiller` 会自动填充实体的全部字段（String 填随机字符串、Number 填随机数字……），你只需要覆盖测试相关的字段：

```java
// username、email、createdAt 等字段全部自动填充
// 你只需要关注 balance 这一个字段
.add(User.class, u -> u.setBalance(BigDecimal.valueOf(1000)))
```

两种引擎可选：

| 引擎 | 配置值 | 特点 |
|------|--------|------|
| **Instancio**（默认） | `instancio` | 更强的类型推断，原生支持 JPA 注解感知 |
| **EasyRandom** | `easyrandom` | 稳定可靠，支持 seed 重放 |

在 `application.yml` 中切换：

```yaml
flowtest:
  data-filler: easyrandom    # 默认 instancio
  seed: 12345                # 固定种子，让测试数据可重放（0 = 随机）
  string-length-min: 5
  string-length-max: 20
```

### 实体元数据解析

FlowTest 通过反射自动解析实体类的表名、列名、ID 字段。**无需编译期依赖 JPA**——框架在运行时通过反射读取注解。

**表名解析优先级**：
1. `@Table(name = "t_user")` — JPA
2. `@TableName("t_user")` — MyBatis-Plus
3. `@Entity(name = "t_user")` — JPA
4. 类名驼峰转下划线：`UserInfo` → `user_info`

**ID 字段解析优先级**：
1. `@Id` — JPA
2. `@TableId` — MyBatis-Plus
3. 名为 `id` 的字段

**列名解析优先级**：
1. `@Column(name = "user_name")` — JPA
2. `@TableField("user_name")` — MyBatis-Plus
3. 字段名驼峰转下划线：`userName` → `user_name`

不加任何注解也能正常工作。

---

## 断言 API

### 异常断言

验证业务方法抛出了预期异常：

```java
.act(() -> userService.deductBalance(userId, BigDecimal.valueOf(9999)))
.assertThat()
    .exception(InsufficientBalanceException.class)
        .hasMessageContaining("余额不足")
        .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(400))
    .and()                                  // 返回 AssertBuilder，可以继续断言
    .unchanged(User.class);                 // 异常时用户余额不应变化
```

`exception()` 的方法：
- `.hasMessage("完整消息")` — 精确匹配
- `.hasMessageContaining("部分消息")` — 包含匹配
- `.satisfies(e -> ...)` — 自定义断言
- `.and()` — 返回 AssertBuilder

验证无异常：

```java
.assertThat().noException()
```

### 返回值断言

**Lambda 风格**（适合复杂断言）：

```java
.act(() -> orderService.createOrder(userId, productId))
.assertThat()
    .returnValue(order -> {
        assertThat(order).isNotNull();
        assertThat(order.getTotalAmount()).isEqualByComparingTo("200.00");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    });
```

**方法引用风格**（更简洁）：

```java
.assertThat()
    .result()
        .isNotNull()
        .has(Order::getStatus, OrderStatus.CREATED)
        .has(Order::getTotalAmount, BigDecimal.valueOf(200))
    .and()                      // 返回 AssertBuilder
    .created(Order.class);
```

**直接获取返回值**：

```java
Order order = flow.arrange()
    ...
    .assertThat()
    .getResult();
```

### 数据库变更断言

`dbChanges()` 提供最细粒度的数据库变更断言。框架自动在 `persist()` 前后拍摄快照，然后计算差异。

```java
.assertThat()
    .dbChanges(db -> db

        // 新增行断言
        .table("t_order")
            .hasNewRows(1)
            .row(0)                                         // 第一行新增数据
                .value("status").isEqualTo("CREATED")
                .value("amount").isEqualTo(200)
                .value("created_at").isNotNull()

        // 修改行断言
        .table("t_user")
            .hasModifiedRows(1)
            .modifiedRow(0)                                 // 第一行修改
                .column("balance").changedFrom(1000).to(800)
                .column("updated_at").wasModified()
                .column("username").wasNotModified()

        // 通过主键定位修改行
        .table("t_user")
            .modifiedRowWithId(userId)
                .column("balance").changedFrom(1000).to(800)

        // 删除行断言
        .table("t_order")
            .hasDeletedRows(2)

        // 无变化断言
        .table("t_product")
            .hasNoChanges()
    )
```

### 实体状态断言

验证 arrange 阶段创建的实体，在 act 之后的数据库状态（从数据库重新读取）：

```java
.assertThat()
    // 按类型（取第一个）
    .entity(User.class)
        .has(User::getBalance, BigDecimal.valueOf(800))
        .has(User::getLevel, UserLevel.VIP)
    .and()

    // 按别名
    .entity("buyer", User.class)
        .has(User::getBalance, BigDecimal.valueOf(200))
    .and()

    // 按索引
    .entity(User.class, 1)
        .has(User::getBalance, BigDecimal.valueOf(1000))
    .and()

    // 也支持直接用列名
    .entity(User.class)
        .has("balance", BigDecimal.valueOf(800))
```

### 新行断言

验证 act 阶段新插入的行（不是 arrange 创建的，而是业务逻辑产生的）：

```java
.assertThat()
    // 只有一条新行时，直接断言
    .newRow(Order.class)
        .has(Order::getStatus, OrderStatus.CREATED)
        .has(Order::getTotalAmount, BigDecimal.valueOf(200))
    .and()

    // 多条新行时，用 matching 定位到具体行
    .newRow(OrderItem.class)
        .matching(OrderItem::getProductId, productId)
        .has(OrderItem::getQuantity, 2)
```

### 快捷断言

不需要写 `dbChanges()` 的简化方式，适合只关心行数不关心具体内容的场景：

```java
.assertThat()
    .noException()
    .created(Order.class)                   // 新增 1 行
    .created(OrderItem.class, 3)            // 新增 3 行
    .modified(User.class)                   // 修改 1 行
    .modified(User.class, 2)                // 修改 2 行
    .deleted(Product.class)                 // 删除 1 行
    .unchanged(Product.class)               // 无变化
    .onlyChanged(Order.class, User.class)   // 只有这些表有变化，其他表不变
    .noDatabaseChanges()                    // 所有监控的表都无变化
```

---

## 清理策略

FlowTest 在每个测试结束后自动清理测试数据。通过 `@FlowTest(cleanup = ...)` 指定策略。

| 策略 | 清理 arrange 数据 | 清理 act 数据 | 需要真实提交 | 适用场景 |
|------|:-:|:-:|:-:|----------|
| `TRANSACTION`（默认） | Yes | Yes | No | 绝大多数测试 |
| `COMPENSATING` | Yes | 默认 No，可选 Yes | Yes | 异步消息、REQUIRES_NEW |
| `SNAPSHOT_BASED` | Yes | Yes | Yes | 需要真实提交且全面清理 |
| `NONE` | No | No | — | 调试时保留数据检查 |

### TRANSACTION（默认，推荐）

利用 Spring `@Transactional` 回滚。最快、最简单，**大多数测试用这个就够了**：

```java
@FlowTest   // 默认 cleanup = CleanupMode.TRANSACTION
@SpringBootTest
class OrderServiceTest {
    @Test
    void testCreateOrder() {
        flow.arrange().add(User.class).persist()
            .act(() -> orderService.createOrder(...))
            .assertThat().noException();
        // 测试结束自动回滚，数据库干净如初
    }
}
```

### COMPENSATING

通过物理 DELETE 删除 `persist()` 阶段创建的数据。适用于需要真实提交的场景（异步操作、分布式事务等）：

```java
@FlowTest(cleanup = CleanupMode.COMPENSATING)
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // 禁用事务以真实提交
@Test
void testAsyncProcess() {
    flow.arrange().add(User.class).persist()
        .act(() -> asyncService.process(flow.get(User.class).getId()))
        .assertThat().noException();
    // persist 阶段创建的 User 被 DELETE
    // act 阶段产生的数据默认不清理
}
```

如果也需要清理 act 阶段的数据，开启 `cleanActData`：

```java
@FlowTest(cleanup = CleanupMode.COMPENSATING, cleanActData = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Test
void testWithActDataCleanup() {
    // act 阶段新增的行也会被自动清理
}
```

### SNAPSHOT_BASED

通过 before/after 快照对比主键集合，删除所有新增行。最全面的清理策略，支持任意主键类型（数字、UUID、字符串）：

```java
@FlowTest(cleanup = CleanupMode.SNAPSHOT_BASED)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Test
void testWithFullCleanup() {
    flow.arrange().add(User.class).persist()
        .act(() -> orderService.createOrder(...))
        .assertThat().noException().created(Order.class);
    // User 和 Order 都会被清理
}
```

### 手动清理

在不使用注解的场景下，可以手动调用 `flow.cleanup()`：

```java
@Test
void testManualCleanup() {
    try {
        flow.arrange().add(User.class).persist();
        // ... 测试逻辑 ...
    } finally {
        flow.cleanup();   // 手动触发清理
    }
}
```

---

## 多数据源支持

当应用使用多个数据库时（如订单库、用户库），FlowTest 可以自动将实体路由到正确的数据源。**测试代码无需任何修改**。

### 配置方式

在 `application.yml`（或 `application.properties`，见[配置参考](#applicationproperties-格式)）中声明数据源映射：

```yaml
# 方式一：自动发现（最简单）
# 只需声明数据源 Bean 名称，FlowTest 自动查询每个库的表元数据
flowtest:
  datasources:
    orderDataSource: {}
    userDataSource: {}

# 方式二：显式指定表名
flowtest:
  datasources:
    orderDataSource:
      tables:
        - t_order
        - t_order_item
        - t_product
    userDataSource:
      tables:
        - t_user
        - t_account

# 方式三：通配符匹配（推荐）
# * 匹配零个或多个字符
flowtest:
  datasources:
    orderDataSource:
      tables:
        - t_order*          # 匹配 t_order, t_order_item, t_order_detail...
        - t_product
    userDataSource:
      tables:
        - t_user*           # 匹配 t_user, t_user_info, t_user_role...
        - t_account

# 三种方式可以混用
flowtest:
  datasources:
    orderDataSource:
      tables: [t_order*, t_product, t_payment]
    userDataSource: {}       # 自动发现
```

### 通配符说明

`*` 匹配任意数量的字符（包括零个）：

| 模式 | 匹配 | 不匹配 |
|------|------|--------|
| `t_order*` | `t_order`、`t_order_item`、`t_order_detail` | `t_product` |
| `*_log` | `access_log`、`error_log` | `t_log_detail` |
| `t_*_log` | `t_access_log`、`t_error_log` | `t_log` |
| `*order*` | `t_order`、`t_order_item`、`my_order_table` | `t_product` |

### 路由查找优先级

1. **精确匹配** — 查找精确的表名映射
2. **通配符匹配** — 查找 `*` 模式
3. **默认数据源** — 以上都不匹配时，使用 Spring 上下文中未被配置的 DataSource

### 数据源 Bean 配置

确保 Spring 上下文中存在对应名称的 `DataSource` Bean：

```java
@Configuration
class DataSourceConfig {

    @Bean
    @Primary
    public DataSource orderDataSource() {
        return DataSourceBuilder.create()
            .url("jdbc:mysql://localhost:3306/order_db")
            .build();
    }

    @Bean
    public DataSource userDataSource() {
        return DataSourceBuilder.create()
            .url("jdbc:mysql://localhost:3306/user_db")
            .build();
    }
}
```

### 测试代码无需改动

路由完全透明，测试代码和单数据源时一模一样：

```java
@FlowTest
@SpringBootTest
class CrossDbTest {

    @Autowired TestFlow flow;
    @Autowired OrderService orderService;

    @Test
    void testCrossDbOrder() {
        flow.arrange()
            .add(User.class, UserTraits.vip())          // → 自动路由到 userDataSource
            .add(Product.class, ProductTraits.active())  // → 自动路由到 orderDataSource
            .persist()
            .act(() -> orderService.createOrder(
                flow.get(User.class).getId(),
                flow.get(Product.class).getId()))
            .assertThat()
                .noException()
                .dbChanges(db -> db
                    .table("t_order").hasNewRows(1));    // → 自动路由到 orderDataSource
    }
}
```

### 不配置 datasources 时

当 `application.yml` 中没有 `flowtest.datasources` 配置时，行为与之前完全一致——使用单个 DataSource，零影响。

---

## Mockito 集成

`flowtest-mockito` 模块将 Mock 配置融入 FlowTest 的流式 API。

### 基本用法

```java
@FlowTest
@SpringBootTest
class PaymentTest {

    @Autowired TestFlow flow;

    @Test
    void testPaymentWithMock() {
        MockTestFlow mockFlow = MockTestFlow.wrap(flow);

        mockFlow.arrange()
            // 配置 Mock
            .withMocks()
                .mock(PaymentGateway.class)
                    .when(gw -> gw.charge(any(), any()))
                    .thenReturn(ChargeResult.success())
                .done()
            // 准备数据
            .add(User.class, UserTraits.balance(1000))
            .add(Order.class, OrderTraits.amount(500))
            .persist()
            // 执行
            .act(() -> paymentService.processPayment(
                mockFlow.getMock(PaymentGateway.class),
                flow.get(Order.class).getId()))
            // 断言
            .assertThat()
                .noException()
                .mocks()
                    .verify(PaymentGateway.class)
                        .atLeastOnce()
                        .called(gw -> gw.charge(any(), any()))
                    .done();
    }
}
```

### MockTrait —— 可复用的 Mock 配置

和实体 Trait 一样，Mock 配置也可以抽取为可复用的 MockTrait：

```java
public class PaymentMockTraits {
    public static MockTrait<PaymentGateway> success() {
        return config -> config
            .when(gw -> gw.charge(any(), any()))
            .thenReturn(ChargeResult.success());
    }

    public static MockTrait<PaymentGateway> failure(String reason) {
        return config -> config
            .when(gw -> gw.charge(any(), any()))
            .thenThrow(new PaymentException(reason));
    }
}

// 使用
.withMocks()
    .mock(PaymentGateway.class, PaymentMockTraits.success())
    .done()
```

### 注册已有 Mock / Spy

```java
// 注册外部创建的 Mock
PaymentGateway preConfigured = mock(PaymentGateway.class);
when(preConfigured.charge(any(), any())).thenReturn(result);

.withMocks()
    .register(preConfigured)
    .done()

// Spy
.withMocks()
    .spy(realService)
        .when(s -> s.sendNotification(any()))
        .thenDoNothing()
    .done()
```

### Mock 验证

```java
.assertThat()
    .noException()
    .mocks()
        .verify(PaymentGateway.class)
            .times(1)                                         // 调用 1 次
            .called(gw -> gw.charge(any(), any()))
        .and()
        .verify(NotificationService.class)
            .never()                                          // 未调用
            .called(ns -> ns.sendEmail(any()))
        .done()
```

---

## 配置参考

### application.yml 完整配置

```yaml
flowtest:
  # 默认清理策略
  cleanup-mode: TRANSACTION            # TRANSACTION | COMPENSATING | SNAPSHOT_BASED | NONE

  # COMPENSATING 模式下是否清理 act 阶段数据
  clean-act-data: false

  # 数据填充引擎
  data-filler: instancio               # instancio | easyrandom

  # 随机种子（0 = 每次运行不同）
  seed: 0

  # 自动生成的字符串长度范围
  string-length-min: 5
  string-length-max: 20

  # 自动生成的集合大小范围
  collection-size-min: 1
  collection-size-max: 3

  # 嵌套对象最大深度
  randomization-depth: 3

  # 默认监控的表（空 = 自动从 persist 的实体推断）
  snapshot-tables: []

  # 主键列名回退值（自动检测失败时使用）
  id-column-name: id

  # 多数据源配置（可选，不配置则使用单数据源模式）
  datasources:
    orderDataSource:
      tables: [t_order*, t_product]
    userDataSource:
      tables: [t_user*]
```

### application.properties 格式

如果项目使用 `application.properties`（如 SOFABoot），配置完全等价：

```properties
# 基础配置
flowtest.cleanup-mode=TRANSACTION
flowtest.clean-act-data=false
flowtest.data-filler=instancio
flowtest.seed=0
flowtest.string-length-min=5
flowtest.string-length-max=20
flowtest.collection-size-min=1
flowtest.collection-size-max=3
flowtest.randomization-depth=3
flowtest.snapshot-tables=
flowtest.id-column-name=id

# 多数据源配置
flowtest.datasources.orderDataSource.tables[0]=t_order*
flowtest.datasources.orderDataSource.tables[1]=t_product
flowtest.datasources.userDataSource.tables[0]=t_user*
```

自动发现模式只需声明数据源名称，无需配置 tables：

```properties
# 自动发现 —— 只声明数据源，表映射自动从数据库元数据获取
flowtest.datasources.orderDataSource.tables=
flowtest.datasources.userDataSource.tables=
```

### @FlowTest 注解属性

可加在类或方法上，方法级优先于类级：

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `cleanup` | `CleanupMode` | `TRANSACTION` | 清理策略 |
| `snapshotTables` | `String[]` | `{}` | 监控的表（空 = 自动推断） |
| `cleanActData` | `boolean` | `false` | 仅 COMPENSATING 模式生效 |

```java
// 类级配置 —— 所有测试方法生效
@FlowTest(cleanup = CleanupMode.SNAPSHOT_BASED, snapshotTables = {"t_order", "t_user"})
class OrderServiceTest { ... }

// 方法级覆盖
@FlowTest(cleanup = CleanupMode.NONE)
@Test
void debugTest() { ... }
```

---

## 常见问题

### H2 保留字冲突

`user`、`order` 是 H2 的保留字。建表时加 `t_` 前缀：

```sql
CREATE TABLE t_user (...);    -- OK
CREATE TABLE t_order (...);   -- OK
CREATE TABLE "user" (...);    -- 可以但不推荐
```

### @Transactional 与 SNAPSHOT_BASED/COMPENSATING 冲突

**不要**同时使用默认的 `@Transactional`（REQUIRED 传播级别）和 `SNAPSHOT_BASED`/`COMPENSATING` 清理——事务回滚会让这两种策略无法看到提交的数据。正确做法是禁用事务：

```java
@FlowTest(cleanup = CleanupMode.SNAPSHOT_BASED)
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // 必须加
@Test
void testWithRealCommit() { ... }
```

### 枚举字段持久化

FlowTest 自动将 Java 枚举转为 `.name()` 字符串存储，无需额外处理。

### AUTO_INCREMENT 不随事务回滚重置

H2 的自增计数器不随事务回滚重置。FlowTest 内部使用行数对比（非 MAX(ID)）来计算新增行，不受影响。

### 不加注解的实体

FlowTest 不强制要求 JPA / MyBatis-Plus 注解。没有注解时自动推断：
- `UserInfo` → 表名 `user_info`
- `userName` → 列名 `user_name`
- 名为 `id` 的字段 → 主键

### 并行测试

FlowTest 使用 `ThreadLocal` 隔离每个测试的上下文，天然支持 JUnit 5 和 TestNG 的并行执行。

### Java 版本

FlowTest 目标 Java 8。EasyRandom 4.3.0 和 Instancio 3.7.1 是支持 Java 8 的最后版本，已锁定不升级。

---

## 模块结构

```
flowtest
├── flowtest-core                    核心框架（fixture、persistence、snapshot、assertion、routing）
├── flowtest-assertj-db              AssertJ-DB 集成
├── flowtest-junit5                  JUnit 5 扩展（@FlowTest 注解 + FlowTestExtension）
├── flowtest-testng                  TestNG 监听器（@FlowTest 注解 + FlowTestListener）
├── flowtest-mockito                 Mockito 集成（MockTestFlow、MockTrait）
└── flowtest-spring-boot-starter     Spring Boot 自动配置（推荐引入此模块）
```

## 构建与测试

```bash
# 构建全部模块
mvn clean install

# 跳过测试
mvn clean install -DskipTests

# 运行指定模块的测试
mvn test -pl flowtest-core

# 运行指定测试类
mvn test -pl flowtest-core -Dtest=DataSourceRoutingIntegrationTest

# 运行指定测试方法
mvn test -pl flowtest-core -Dtest=DataSourceRouteTest#wildcardMatchHandlesEdgeCases
```

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
