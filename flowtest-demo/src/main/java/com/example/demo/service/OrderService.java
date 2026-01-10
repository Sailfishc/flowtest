package com.example.demo.service;

import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Order service with business logic.
 */
@Service
public class OrderService {

    private final JdbcTemplate jdbcTemplate;

    public OrderService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates an order for the given user and product.
     *
     * @param userId the user ID
     * @param productId the product ID
     * @param quantity the quantity to order
     * @return the created order
     * @throws InsufficientBalanceException if user has insufficient balance
     */
    @Transactional
    public Order createOrder(Long userId, Long productId, int quantity) {
        // Get user
        User user = jdbcTemplate.queryForObject(
            "SELECT * FROM t_user WHERE id = ?",
            (rs, rowNum) -> {
                User u = new User();
                u.setId(rs.getLong("id"));
                u.setBalance(rs.getBigDecimal("balance"));
                u.setLevel(User.UserLevel.valueOf(rs.getString("level")));
                return u;
            },
            userId
        );

        // Get product
        Product product = jdbcTemplate.queryForObject(
            "SELECT * FROM t_product WHERE id = ?",
            (rs, rowNum) -> {
                Product p = new Product();
                p.setId(rs.getLong("id"));
                p.setPrice(rs.getBigDecimal("price"));
                p.setStock(rs.getInt("stock"));
                return p;
            },
            productId
        );

        // Calculate total
        BigDecimal totalAmount = product.getPrice().multiply(BigDecimal.valueOf(quantity));

        // Apply VIP discount
        if (user.getLevel() == User.UserLevel.VIP) {
            totalAmount = totalAmount.multiply(BigDecimal.valueOf(0.9));
        } else if (user.getLevel() == User.UserLevel.SVIP) {
            totalAmount = totalAmount.multiply(BigDecimal.valueOf(0.8));
        }

        // Check balance
        if (user.getBalance().compareTo(totalAmount) < 0) {
            throw new InsufficientBalanceException("余额不足，需要 " + totalAmount + " 元，当前余额 " + user.getBalance() + " 元");
        }

        // Deduct balance
        jdbcTemplate.update(
            "UPDATE t_user SET balance = balance - ? WHERE id = ?",
            totalAmount, userId
        );

        // Create order
        Order order = new Order();
        order.setUserId(userId);
        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setTotalAmount(totalAmount);
        order.setStatus(Order.OrderStatus.CREATED);
        order.setCreatedAt(LocalDateTime.now());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO t_order (user_id, product_id, quantity, total_amount, status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, order.getUserId());
            ps.setLong(2, order.getProductId());
            ps.setInt(3, order.getQuantity());
            ps.setBigDecimal(4, order.getTotalAmount());
            ps.setString(5, order.getStatus().name());
            ps.setTimestamp(6, Timestamp.valueOf(order.getCreatedAt()));
            return ps;
        }, keyHolder);

        Number generatedId = null;
        if (keyHolder.getKeys() != null && !keyHolder.getKeys().isEmpty()) {
            Object idValue = keyHolder.getKeys().get("ID");
            if (idValue == null) {
                idValue = keyHolder.getKeys().get("id");
            }
            if (idValue instanceof Number) {
                generatedId = (Number) idValue;
            }
        }
        if (generatedId == null && keyHolder.getKey() != null) {
            generatedId = keyHolder.getKey();
        }
        if (generatedId != null) {
            order.setId(generatedId.longValue());
        }

        return order;
    }
}
