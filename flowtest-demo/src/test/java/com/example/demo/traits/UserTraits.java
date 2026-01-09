package com.example.demo.traits;

import com.example.demo.entity.User;
import com.flowtest.core.fixture.Trait;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Trait definitions for User entity.
 */
public final class UserTraits {

    private UserTraits() {
    }

    /**
     * VIP user with 10% discount.
     */
    public static Trait<User> vip() {
        return user -> {
            user.setLevel(User.UserLevel.VIP);
            user.setStatus(User.UserStatus.ACTIVE);
        };
    }

    /**
     * SVIP user with 20% discount.
     */
    public static Trait<User> svip() {
        return user -> {
            user.setLevel(User.UserLevel.SVIP);
            user.setStatus(User.UserStatus.ACTIVE);
        };
    }

    /**
     * Normal user (no discount).
     */
    public static Trait<User> normal() {
        return user -> {
            user.setLevel(User.UserLevel.NORMAL);
            user.setStatus(User.UserStatus.ACTIVE);
        };
    }

    /**
     * Sets the user's balance.
     */
    public static Trait<User> balance(double amount) {
        return user -> user.setBalance(BigDecimal.valueOf(amount));
    }

    /**
     * Active user.
     */
    public static Trait<User> active() {
        return user -> user.setStatus(User.UserStatus.ACTIVE);
    }

    /**
     * Disabled user.
     */
    public static Trait<User> disabled() {
        return user -> user.setStatus(User.UserStatus.DISABLED);
    }

    /**
     * Sets username.
     */
    public static Trait<User> username(String username) {
        return user -> user.setUsername(username);
    }

    /**
     * Sets email.
     */
    public static Trait<User> email(String email) {
        return user -> user.setEmail(email);
    }

    /**
     * Rich VIP user (common combination).
     */
    public static Trait<User> richVip() {
        return vip().and(balance(10000.00));
    }

    /**
     * Poor user (common combination).
     */
    public static Trait<User> poor() {
        return normal().and(balance(0.00));
    }
}
