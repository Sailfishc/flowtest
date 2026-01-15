package com.example.demo.service;

import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.entity.User;
import com.example.demo.traits.ProductTraits;
import com.example.demo.traits.UserTraits;
import com.flowtest.assertj.FlowTestChanges;
import com.flowtest.core.TestFlow;
import com.flowtest.core.lifecycle.CleanupMode;
import com.flowtest.junit5.FlowTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OrderService using FlowTest framework.
 */
@FlowTest
@SpringBootTest
@Transactional
@DisplayName("订单服务测试")
class OrderServiceTest {

    @Autowired
    TestFlow flow;

    @Autowired
    OrderService orderService;

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("场景: 创建订单成功")
    class CreateOrderSuccess {

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
                        assertThat(order.getId()).isNotNull();
                        assertThat(order.getTotalAmount()).isEqualByComparingTo("200.00");
                        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CREATED);
                    })
                    .dbChanges(db -> db
                        .table("t_order").hasNewRows(1)
                    );
        }

        @Test
        @DisplayName("VIP用户享受9折优惠")
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
                    })
                    .dbChanges(db -> db
                        .table("t_order").hasNewRows(1)
                    );
        }

        @Test
        @DisplayName("SVIP用户享受8折优惠")
        void testSvipUserDiscount() {
            flow.arrange()
                    .add(User.class, UserTraits.svip(), UserTraits.balance(1000.00))
                    .add(Product.class, ProductTraits.price(100.00), ProductTraits.inStock(10))
                .persist()

                .act(() -> orderService.createOrder(
                        flow.get(User.class).getId(),
                        flow.get(Product.class).getId(),
                        2))

                .assertThat()
                    .noException()
                    .returnValue(order -> {
                        // 200 * 0.8 = 160
                        assertThat(order.getTotalAmount()).isEqualByComparingTo("160.00");
                    });
        }
    }

    @Nested
    @DisplayName("场景: 余额不足")
    class InsufficientBalance {

        @Test
        @DisplayName("普通用户余额不足抛出异常")
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

        @Test
        @DisplayName("VIP用户即使有折扣也余额不足")
        void testVipInsufficientBalance() {
            // VIP 价格 100 * 0.9 = 90, 但用户只有 50
            flow.arrange()
                    .add(User.class, UserTraits.vip(), UserTraits.balance(50.00))
                    .add(Product.class, ProductTraits.price(100.00), ProductTraits.inStock(10))
                .persist()

                .act(() -> orderService.createOrder(
                        flow.get(User.class).getId(),
                        flow.get(Product.class).getId(),
                        1))

                .assertThat()
                    .exception(InsufficientBalanceException.class)
                    .dbChanges(db -> db
                        .table("t_order").hasNoChanges()
                    );
        }
    }

    @Nested
    @DisplayName("场景: 批量创建多个用户")
    class MultipleEntities {

        @Test
        @DisplayName("使用别名区分多个实体")
        void testWithAlias() {
            flow.arrange()
                    .add("richUser", User.class, UserTraits.richVip())
                    .add("poorUser", User.class, UserTraits.poor())
                    .add(Product.class, ProductTraits.cheap())
                .persist();

            User richUser = flow.get("richUser", User.class);
            User poorUser = flow.get("poorUser", User.class);
            Product product = flow.get(Product.class);

            assertThat(richUser.getBalance()).isEqualByComparingTo("10000.00");
            assertThat(poorUser.getBalance()).isEqualByComparingTo("0.00");

            // Rich user can order - just verify the entities exist
            // The ordering logic is already tested in other test cases
            // Here we just verify that we can retrieve entities with aliases
        }

        @Test
        @DisplayName("使用 addMany 批量创建实体")
        void testAddMany() {
            flow.arrange()
                    .addMany(User.class, 3, UserTraits.normal(), UserTraits.balance(100.00))
                    .add(Product.class, ProductTraits.cheap())
                .persist();

            assertThat(flow.getAll(User.class)).hasSize(3);
            assertThat(flow.get(User.class, 0)).isNotNull();
            assertThat(flow.get(User.class, 1)).isNotNull();
            assertThat(flow.get(User.class, 2)).isNotNull();
        }
    }

    @Nested
    @DisplayName("场景: 修改检测")
    class ModificationDetection {

        @Test
        @DisplayName("创建订单时检测用户余额修改")
        void testUserBalanceModified() {
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
                        .table("t_order").hasNewRows(1)
                        .table("t_user").hasModifiedRows(1)
                            .modifiedRow(0)
                                .column("balance").changedFrom(1000.00).to(800.00)
                    );
        }

        @Test
        @DisplayName("VIP用户创建订单时检测余额修改(含折扣)")
        void testVipUserBalanceModified() {
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
                    .dbChanges(db -> db
                        .table("t_order").hasNewRows(1)
                        .table("t_user").hasModifiedRows(1)
                            .modifiedRow(0)
                                // 200 * 0.9 = 180, 1000 - 180 = 820
                                .column("balance").changedFrom(1000.00).to(820.00)
                    );
        }

        @Test
        @DisplayName("余额不足时无修改")
        void testNoModificationOnInsufficientBalance() {
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
                    .dbChanges(db -> db
                        .table("t_order").hasNoChanges()
                        .table("t_user").hasNoChanges()
                    );
        }
    }

    @Nested
    @DisplayName("场景: FlowTestChanges 断言 (非事务)")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    class FlowTestChangesAssertion {

        @Test
        @DisplayName("普通用户创建订单 - 使用 FlowTestChanges 断言")
        void testNormalUserCreateOrderWithFlowTestChanges() {
            // 记录 baseline 用于清理 act 产生的数据
            Long maxOrderId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM t_order", Long.class);

            FlowTestChanges changes = new FlowTestChanges(dataSource, "t_order").setStartPointNow();
            try {
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
                            assertThat(order.getId()).isNotNull();
                            assertThat(order.getTotalAmount()).isEqualByComparingTo("200.00");
                            assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CREATED);
                        });

                changes.setEndPointNow();
                changes.assertChanges()
                    .hasNumberOfChanges(1)
                    .changeOnTable("t_order")
                        .isCreation();
            } finally {
                // 清理 act 产生的 Order 数据
                jdbcTemplate.update("DELETE FROM t_order WHERE id > ?", maxOrderId);
                // 清理 persist 产生的数据
                flow.cleanup();
            }
        }
    }

    @Nested
    @DisplayName("场景: 数据清理")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    class DataCleanup {

        @Test
        @DisplayName("手动调用 cleanup() 清理 persist 数据")
        void testManualCleanupPersistData() {
            // 记录测试前的数据量
            Long userCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_user", Long.class);

            try {
                // 创建测试数据
                flow.arrange()
                    .add(User.class, UserTraits.normal(), UserTraits.balance(500.00))
                    .persist();

                // 验证数据已创建
                Long userCountDuring = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_user", Long.class);
                assertThat(userCountDuring).isGreaterThan(userCountBefore);

                // 手动清理
                flow.cleanup();

                // 验证 persist 数据已清理
                Long userCountAfter = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_user", Long.class);
                assertThat(userCountAfter).isEqualTo(userCountBefore);
            } finally {
                // 确保即使测试失败也能清理
                flow.cleanup();
            }
        }
    }

    // ==================== 新 API 演示 ====================

    @Nested
    @DisplayName("场景: Lambda Trait (新 API)")
    class LambdaTraitDemo {

        @Test
        @DisplayName("使用 Lambda 配置实体")
        void testLambdaTrait() {
            flow.arrange()
                // 使用 Lambda 直接配置，无需预定义 Trait 类
                .add(User.class, user -> {
                    user.setLevel(User.UserLevel.VIP);
                    user.setBalance(java.math.BigDecimal.valueOf(1000));
                })
                .add(Product.class, product -> {
                    product.setPrice(java.math.BigDecimal.valueOf(100));
                    product.setStock(10);
                })
                .persist()

                .act(() -> orderService.createOrder(
                        flow.get(User.class).getId(),
                        flow.get(Product.class).getId(),
                        2))

                .assertThat()
                    .noException()
                    .returnValue(order -> {
                        // VIP 9折: 200 * 0.9 = 180
                        assertThat(order.getTotalAmount()).isEqualByComparingTo("180.00");
                    });
        }

        @Test
        @DisplayName("使用带索引的 Lambda 批量创建")
        void testAddManyWithIndex() {
            flow.arrange()
                // 批量创建带索引配置
                .addMany(User.class, 3, (user, index) -> {
                    user.setUsername("user_" + index);
                    user.setLevel(User.UserLevel.NORMAL);
                    user.setBalance(java.math.BigDecimal.valueOf(100 * (index + 1)));
                })
                .persist();

            // 验证索引化配置
            assertThat(flow.getAll(User.class)).hasSize(3);
            assertThat(flow.get(User.class, 0).getBalance()).isEqualByComparingTo("100");
            assertThat(flow.get(User.class, 1).getBalance()).isEqualByComparingTo("200");
            assertThat(flow.get(User.class, 2).getBalance()).isEqualByComparingTo("300");
        }
    }

    @Nested
    @DisplayName("场景: 快捷断言方法 (新 API)")
    class ShortcutAssertionDemo {

        @Test
        @DisplayName("使用 created() 快捷断言")
        void testCreatedShortcut() {
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
                    // 新 API: 自动推断表名
                    .created(Order.class)        // 等价于 .dbChanges(db -> db.table("t_order").hasNewRows(1))
                    .modified(User.class);       // 等价于 .dbChanges(db -> db.table("t_user").hasModifiedRows(1))
        }

        @Test
        @DisplayName("使用 unchanged() 快捷断言")
        void testUnchangedShortcut() {
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
                    // 新 API: 快捷验证无变化
                    .unchanged(Order.class)     // 等价于 .dbChanges(db -> db.table("t_order").hasNoChanges())
                    .unchanged(User.class);
        }

        @Test
        @DisplayName("使用 onlyChanged() 验证仅特定表变化")
        void testOnlyChangedShortcut() {
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
                    // 新 API: 仅 Order 和 User 表有变化，Product 表无变化
                    .onlyChanged(Order.class, User.class);
        }
    }
}
