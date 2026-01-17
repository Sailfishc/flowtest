package com.example.demo.service;

import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.entity.User;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order service with business logic.
 */
@Service
public class OrderService {

    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;

    public OrderService(UserMapper userMapper, ProductMapper productMapper, OrderMapper orderMapper) {
        this.userMapper = userMapper;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
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
        User user = userMapper.selectById(userId);

        // Get product
        Product product = productMapper.selectById(productId);

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
        user.setBalance(user.getBalance().subtract(totalAmount));
        userMapper.updateById(user);

        // Create order
        Order order = new Order();
        order.setUserId(userId);
        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setTotalAmount(totalAmount);
        order.setStatus(Order.OrderStatus.CREATED);
        order.setCreatedAt(LocalDateTime.now());

        orderMapper.insert(order);

        return order;
    }
}
