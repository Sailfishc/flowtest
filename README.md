# FlowTest

[![Java](https://img.shields.io/badge/Java-1.8-orange)](https://openjdk.org/projects/jdk8/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen)](https://spring.io/projects/spring-boot)
[![JUnit](https://img.shields.io/badge/JUnit-5.9.3-blue)](https://junit.org/junit5/)

一个专注于数据库测试的 Java 集成测试框架，采用代码优先(Code-First)的设计理念和流畅的 DSL 接口。

## 简介

FlowTest 提供了一种简洁、直观的方式来编写集成测试，特别是针对数据库操作的场景。它遵循 **Arrange-Act-Assert** 模式，支持：

- 自动生成测试数据（基于 EasyRandom）
- 流畅的测试 DSL
- 数据库变更快照和差异检测
- 可组合的实体特征(Traits)
- 多种清理策略

## 核心特性

```infographic
infographic list-grid-badge-card
data
  title 核心特性
  items
    - label 流畅 DSL
      desc Arrange-Act-Assert 模式的链式调用
      icon mdi:script-text-outline
    - label 自动数据生成
      desc 使用 EasyRandom 自动填充实体字段
      icon mdi:auto-fix
    - label 快照差异检测
      desc 检测数据库表的新增、修改、删除
      icon mdi:database-sync
    - label 特征组合
      desc 可复用、可组合的实体配置
      icon mdi:puzzle
    - label 多清理策略
      desc 事务回滚、快照恢复、补偿删除
      icon mdi:trash-can-outline
    - label Spring Boot 集成
      desc 开箱即用的自动配置
      icon mdi:spring
```

## 模块结构

```infographic
infographic hierarchy-mindmap-curved-line-compact-card
data
  title 模块架构
  items
    - label flowtest-parent
      desc Maven 父项目
      children
        - label flowtest-core
          desc 核心框架
        - label flowtest-assertj-db
          desc AssertJ-DB 集成
        - label flowtest-junit5
          desc JUnit 5 扩展
        - label flowtest-spring-boot-starter
          desc Spring Boot 自动配置
        - label flowtest-demo
          desc 示例应用
```

## 快速开始

### 1. 添加依赖

**Maven:**

```xml
<dependency>
    <groupId>com.flowtest</groupId>
    <artifactId>flowtest-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

**Gradle:**

```groovy
testImplementation 'com.flowtest:flowtest-spring-boot-starter:1.0.0-SNAPSHOT'
```

### 2. 编写测试

```java
@FlowTest
@SpringBootTest
@Transactional
class OrderServiceTest {

    @Autowired TestFlow flow;
    @Autowired OrderService orderService;

    @Test
    void testNormalUserCreateOrder() {
        flow.arrange()
                .add(User.class, UserTraits.normal(), UserTraits.balance(1000.00))
                .add(Product.class, ProductTraits.price(100.00), ProductTraits.inStock(10))
            .persist()
            .act(() -> orderService.createOrder(
                    flow.get(User.class).getId(),
                    flow.get(Product.class).getId(),
                    2))
            .assertThat()
                .noException()
                .dbChanges(db -> db
                    .table("t_order").hasNewRows(1));
    }
}
```

## 核心概念

### Arrange-Act-Assert 模式

FlowTest 遵循 AAA 测试模式：

```infographic
infographic sequence-snake-steps-simple
data
  title 测试流程
  items
    - label Arrange
      desc 准备测试数据和环境
    - label Act
      desc 执行被测的业务逻辑
    - label Assert
      desc 验证结果和数据库变更
```

### 特征 (Traits)

Traits 是可复用的实体配置函数：

```java
public class UserTraits {
    public static Trait<User> vip() {
        return user -> user.setLevel(UserLevel.VIP);
    }

    public static Trait<User> balance(double amount) {
        return user -> user.setBalance(BigDecimal.valueOf(amount));
    }

    // 组合多个特征
    public static Trait<User> richVip() {
        return vip().and(balance(10000.00));
    }
}
```

### 实体管理

```java
// 获取第一个实体
User user = flow.get(User.class);

// 通过别名获取
User rich = flow.get("richUser", User.class);

// 通过索引获取
User first = flow.get(User.class, 0);

// 获取所有实体
List<User> allUsers = flow.getAll(User.class);
```

### 数据库变更断言

```java
.assertThat()
    .dbChanges(db -> db
        .table("t_order").hasNewRows(1)
        .table("t_user").hasModifiedRows(1)
            .modifiedRow(0)
                .column("balance").changedFrom(1000.00).to(800.00)
        .table("t_product").hasDeletedRows(1)
    );
```

## 完整示例

### 定义实体

```java
@Entity
@Table(name = "t_user")
public class User {
    @Id
    private Long id;
    private BigDecimal balance;
    private UserLevel level;
    // getters and setters
}
```

### 定义特征

```java
public class UserTraits {
    public static Trait<User> normal() {
        return user -> user.setLevel(UserLevel.NORMAL);
    }

    public static Trait<User> vip() {
        return user -> user.setLevel(UserLevel.VIP);
    }

    public static Trait<User> balance(double amount) {
        return user -> user.setBalance(BigDecimal.valueOf(amount));
    }

    public static Trait<User> richVip() {
        return vip().and(balance(10000.00));
    }
}
```

### 编写测试

```java
@FlowTest
@SpringBootTest
@Transactional
class OrderServiceTest {

    @Autowired TestFlow flow;
    @Autowired OrderService orderService;

    @Test
    @DisplayName("普通用户创建订单")
    void testNormalUserCreateOrder() {
        flow.arrange()
                .add(User.class, UserTraits.normal(), UserTraits.balance(1000.00))
                .add(Product.class, ProductTraits.price(100.00), ProductTraits.inStock(10))
            .persist()
            .act(() -> orderService.createOrder(
                    flow.get(User.class).getId(),
                    flow.get(Product.class).getId(),
                    2))
            .assertThat()
                .noException()
                .returnValue(order -> {
                    assertThat(order).isNotNull();
                    assertThat(order.getTotalAmount()).isEqualByComparingTo("200.00");
                })
                .dbChanges(db -> db
                    .table("t_order").hasNewRows(1)
                    .table("t_user").hasModifiedRows(1)
                        .modifiedRow(0)
                            .column("balance").changedFrom(1000.00).to(800.00)
                );
    }

    @Test
    @DisplayName("VIP用户享受折扣")
    void testVipUserDiscount() {
        flow.arrange()
                .add(User.class, UserTraits.vip(), UserTraits.balance(1000.00))
                .add(Product.class, ProductTraits.price(100.00), ProductTraits.inStock(10))
            .persist()
            .act(() -> orderService.createOrder(
                    flow.get(User.class).getId(),
                    flow.get(Product.class).getId(),
                    2))
            .assertThat()
                .noException()
                .returnValue(order -> {
                    // 200 * 0.9 = 180
                    assertThat(order.getTotalAmount()).isEqualByComparingTo("180.00");
                });
    }

    @Test
    @DisplayName("余额不足抛出异常")
    void testInsufficientBalance() {
        flow.arrange()
                .add(User.class, UserTraits.normal(), UserTraits.balance(10.00))
                .add(Product.class, ProductTraits.price(100.00), ProductTraits.inStock(10))
            .persist()
            .act(() -> orderService.createOrder(
                    flow.get(User.class).getId(),
                    flow.get(Product.class).getId(),
                    1))
            .assertThat()
                .exception(InsufficientBalanceException.class)
                    .hasMessageContaining("余额不足")
                .dbChanges(db -> db
                    .table("t_order").hasNoChanges()
                );
    }
}
```

## 高级特性

### 批量创建实体

```java
flow.arrange()
        .addMany(User.class, 5, UserTraits.normal())
        .persist();

List<User> users = flow.getAll(User.class);
assertThat(users).hasSize(5);
```

### 使用别名区分实体

```java
flow.arrange()
        .add("richUser", User.class, UserTraits.richVip())
        .add("poorUser", User.class, UserTraits.poor())
        .persist();

User rich = flow.get("richUser", User.class);
User poor = flow.get("poorUser", User.class);
```

### 自定义 AutoFiller

```java
// 配置 EasyRandom 参数
AutoFiller customFiller = AutoFiller.builder()
    .seed(12345)
    .stringLengthRange(5, 10)
    .collectionSizeRange(0, 3)
    .build();
```

### 数据清理策略

#### 1. 事务回滚 (默认)

```java
@FlowTest
@SpringBootTest
@Transactional
class TransactionalTest {
    // 测试完成后自动回滚
}
```

#### 2. 快照恢复

```java
@FlowTest(cleanup = CleanupMode.SNAPSHOT_BASED)
@SpringBootTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SnapshotBasedTest {
    // 测试完成后根据快照恢复数据
}
```

#### 3. 补偿删除

```java
@FlowTest(cleanup = CleanupMode.COMPENSATING)
@SpringBootTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CompensatingTest {
    // 测试完成后删除创建的数据
}
```

#### 4. 手动清理

```java
@Transactional(propagation = Propagation.NOT_SUPPORTED)
void testWithManualCleanup() {
    Long baseline = jdbcTemplate.queryForObject(
        "SELECT COALESCE(MAX(id), 0) FROM t_order", Long.class);

    try {
        flow.arrange()
            .add(User.class, UserTraits.normal())
            .persist()
            .act(() -> /* test logic */);
    } finally {
        jdbcTemplate.update("DELETE FROM t_order WHERE id > ?", baseline);
        flow.cleanup();
    }
}
```

### 使用 AssertJ-DB 进行详细断言

```java
@Transactional(propagation = Propagation.NOT_SUPPORTED)
void testWithFlowTestChanges() {
    Long maxOrderId = jdbcTemplate.queryForObject(
        "SELECT COALESCE(MAX(id), 0) FROM t_order", Long.class);

    FlowTestChanges changes = new FlowTestChanges(dataSource, "t_order")
        .setStartPointNow();

    try {
        // 执行测试
        flow.arrange()...
    } finally {
        changes.setEndPointNow();
        changes.assertChanges()
            .hasNumberOfChanges(1)
            .changeOnTable("t_order").isCreation();
        
        jdbcTemplate.update("DELETE FROM t_order WHERE id > ?", maxOrderId);
        flow.cleanup();
    }
}
```

## 构建和运行

### 编译项目

```bash
# 构建所有模块
mvn clean install

# 跳过测试构建
mvn clean install -DskipTests
```

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定模块的测试
mvn test -pl flowtest-demo

# 运行特定测试类
mvn test -pl flowtest-demo -Dtest=OrderServiceTest

# 运行特定测试方法
mvn test -pl flowtest-demo -Dtest=OrderServiceTest#testNormalUserCreateOrder
```

## 技术栈

| 技术 | 版本 |
|------|------|
| Java | 1.8+ |
| Spring Boot | 2.7.18 |
| JUnit Jupiter | 5.9.3 |
| EasyRandom | 5.0.0 |
| AssertJ Core | 3.24.2 |
| AssertJ DB | 2.0.2 |
| SLF4J | 1.7.36 |

## 最佳实践

### 1. 特征复用

将常用的实体配置定义为 Traits，提高复用性：

```java
// ✅ 好的做法
.add(User.class, UserTraits.vip(), UserTraits.balance(1000.00))

// ❌ 避免硬编码
.add(User.class, user -> {
    user.setLevel(UserLevel.VIP);
    user.setBalance(new BigDecimal("1000.00"));
})
```

### 2. 合理使用 @Transactional

大多数测试使用 `@Transactional` 即可，只有需要真实提交的场景才使用 `NOT_SUPPORTED`。

### 3. 注意数据库保留字

H2 数据库中 `user` 和 `order` 是保留字，使用 `t_` 前缀避免冲突。

### 4. 使用 try-finally 确保清理

非事务测试必须使用 try-finally 确保数据被清理：

```java
try {
    // 测试逻辑
} finally {
    flow.cleanup();
}
```

## 常见问题

### Q: 如何调试测试数据？

A: 可以使用 `build()` 方法代替 `persist()` 来查看生成的实体而不持久化：

```java
flow.arrange()
    .add(User.class, UserTraits.normal())
    .build();  // 只构建不持久化

User user = flow.get(User.class);
System.out.println(user);
```

### Q: 如何处理自定义 ID 生成？

A: AutoFiller 默认排除 `@Id` 字段，可以通过自定义配置覆盖：

```java
AutoFiller filler = AutoFiller.builder()
    .excludeField(field -> !field.getName().equals("customId"))
    .build();
```

### Q: 如何测试非数据库操作？

A: FlowTest 主要针对数据库场景，对于纯业务逻辑测试，可以使用普通的 JUnit 断言。

## 贡献指南

欢迎贡献代码、报告问题或提出建议！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 许可证

本项目采用 Apache 2.0 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 联系方式

- 提交 Issue: [GitHub Issues](https://github.com/yourusername/flowtest/issues)
- 文档: [Wiki](https://github.com/yourusername/flowtest/wiki)
