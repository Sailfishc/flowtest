package com.example.demo.service;

import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.entity.User;
import com.example.demo.traits.ProductTraits;
import com.example.demo.traits.UserTraits;
import com.flowtest.assertj.FlowTestChanges;
import com.flowtest.core.TestFlow;
import com.flowtest.junit5.FlowTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
        @DisplayName("普通用户创建订单 - 使用 FlowTestChanges 断言")
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void testNormalUserCreateOrderWithFlowTestChanges() {
            FlowTestChanges changes = new FlowTestChanges(dataSource, "t_order").setStartPointNow();
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
}
