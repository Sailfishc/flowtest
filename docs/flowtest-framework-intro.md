# FlowTest 框架介绍与价值说明

## 1. 一句话定位
FlowTest 是一个面向数据库集成测试的 Java 框架，用代码优先（Code-First）的 DSL 把「测试数据构建 + 业务执行 + 多表变更断言 + 清理」标准化为可复用流程。

## 2. 它核心解决什么问题
在真实业务里，一个接口常常会同时影响多张表。传统 JUnit 集成测试主要痛点是：

- 测试前置数据构建复杂，依赖关系多，重复代码多。
- 业务执行后要手工查多张表，断言冗长且容易漏。
- 非事务场景清理困难，容易留下脏数据污染后续用例。
- 新增场景时，改动主要发生在脚手架代码，而不是业务断言本身。

FlowTest 的核心价值是：把这些重复的“数据脚手架工作”抽象掉，让测试代码聚焦业务场景与预期结果。

## 3. 核心能力清单

- Fluent DSL：统一 Arrange -> Act -> Assert 流程，测试意图清晰。
- 自动造数：减少手工 new 对象和字段赋值成本。
- Trait 机制：把常见数据场景（如 VIP 用户、过期券）抽成可组合模板。
- 数据库变更断言：直接断言新增/修改/删除，支持多表联动校验。
- 清理策略：支持事务回滚、补偿清理、快照清理，降低数据污染风险。
- 生态集成：支持 JUnit5、TestNG、Mockito、Spring Boot Starter。

## 4. 代表性案例：一次下单影响 6 张表
`createOrder()` 同时影响：

- `t_order`（订单主表，新增）
- `t_order_item`（订单明细，新增）
- `t_inventory`（库存，扣减）
- `t_user_wallet`（钱包，扣款）
- `t_coupon`（优惠券，状态变更）
- `t_trade_log`（交易日志，新增）

### 4.1 传统方式（JUnit + 手写 SQL）示意
```java
@Test
void createOrder_normal() {
    long userId = insertUser(1000);
    long productId = insertProduct(100, 20);
    long couponId = insertCoupon(userId, 20, "VALID");

    int orderBefore = count("t_order");
    int itemBefore = count("t_order_item");
    int logBefore = count("t_trade_log");
    int stockBefore = queryInt("select stock from t_inventory where product_id=?", productId);
    BigDecimal walletBefore = queryDecimal("select balance from t_user_wallet where user_id=?", userId);
    String couponBefore = queryString("select status from t_coupon where id=?", couponId);

    try {
        Long orderId = orderService.createOrder(userId, productId, 2, couponId);
        assertThat(orderId).isNotNull();

        assertThat(count("t_order") - orderBefore).isEqualTo(1);
        assertThat(count("t_order_item") - itemBefore).isEqualTo(1);
        assertThat(count("t_trade_log") - logBefore).isEqualTo(1);
        assertThat(queryInt("select stock from t_inventory where product_id=?", productId))
            .isEqualTo(stockBefore - 2);
        assertThat(queryDecimal("select balance from t_user_wallet where user_id=?", userId))
            .isEqualByComparingTo(walletBefore.subtract(new BigDecimal("180")));
        assertThat(queryString("select status from t_coupon where id=?", couponId))
            .isEqualTo("USED");

        assertNoUnexpectedTableChanges();
    } finally {
        cleanupByUser(userId);
    }
}
```

### 4.2 FlowTest 方式示意
```java
@Test
void createOrder_normal() {
    flow.arrange()
        .add(User.class, UserTraits.balance(1000))
        .add(Product.class, ProductTraits.price(100), ProductTraits.stock(20))
        .add(Coupon.class, CouponTraits.valid(20))
        .persist()
        .act(() -> orderService.createOrder(
            flow.get(User.class).getId(),
            flow.get(Product.class).getId(),
            2,
            flow.get(Coupon.class).getId()))
        .assertThat()
        .noException()
        .dbChanges(db -> db
            .table("t_order").hasNewRows(1)
            .table("t_order_item").hasNewRows(1)
            .table("t_inventory").hasModifiedRows(1)
            .table("t_user_wallet").hasModifiedRows(1)
            .table("t_coupon").hasModifiedRows(1)
            .table("t_trade_log").hasNewRows(1))
        .onlyChanged(Order.class, OrderItem.class, Inventory.class, UserWallet.class, Coupon.class, TradeLog.class);
}
```

## 5. 多场景下差异更明显
同样是这 6 张表，常见还要覆盖：

- 场景 A：正常下单（成功）
- 场景 B：库存不足（抛异常）
- 场景 C：优惠券过期（抛异常）

传统方式里，三种场景会重复大量“造数/基线/清理/逐表查询”代码；FlowTest 通常只改 Trait 和断言预期。

示意：

- 库存不足：`ProductTraits.stock(1)` + `exception(InsufficientStockException.class)` + `noDatabaseChanges()`
- 优惠券过期：`CouponTraits.expired()` + `exception(CouponExpiredException.class)` + `noDatabaseChanges()`

## 6. 效率与可维护性收益（团队常见体感）

- 复杂多表用例代码量：约从 120-200 行降到 40-70 行。
- 新增业务场景耗时：约从 30-60 分钟降到 10-20 分钟。
- 维护成本：字段变化时主要改 Trait 和 DSL 断言，不用全量改 SQL 脚手架。
- 故障定位：可直接看到哪张表新增/修改/删除不符合预期。

## 7. 适用边界

- 最适合：数据库写入链路复杂、跨多表联动的集成测试。
- 一般适合：需要长期维护的大量业务场景回归测试。
- 不必强上：纯计算逻辑、几乎不涉及数据库的单元测试。

## 8. 60 秒对外讲解稿（可直接使用）
FlowTest 是一个专门解决数据库集成测试效率问题的框架。传统 JUnit 写多表测试时，70% 时间都花在造数据、对账和清理上，而不是业务断言本身。FlowTest 把这些重复工作抽象成 Arrange-Act-Assert DSL，支持自动造数、Trait 复用和多表变更断言。像下单这种一次改 6 张表的场景，测试代码通常能从百行级降到几十行，新增场景也只需要改条件和期望，不用重写大量 SQL 脚手架。结果是测试更短、更稳、更容易维护。
