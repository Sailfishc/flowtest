package com.example.demo.traits;

import com.example.demo.entity.UserInfo;
import com.flowtest.core.fixture.Trait;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Trait definitions for UserInfo entity.
 */
public final class UserInfoTraits {

    private UserInfoTraits() {
    }

    // ==================== 状态 Traits ====================

    /**
     * 初始状态 (待处理).
     */
    public static Trait<UserInfo> initial() {
        return user -> user.setStatus("I");
    }

    /**
     * 失败状态.
     */
    public static Trait<UserInfo> failed() {
        return user -> user.setStatus("FAIL");
    }

    /**
     * 成功状态.
     */
    public static Trait<UserInfo> success() {
        return user -> user.setStatus("SUCC");
    }

    // ==================== 环境 Traits ====================

    /**
     * 生产环境.
     */
    public static Trait<UserInfo> prod() {
        return user -> user.setEnv("prod");
    }

    /**
     * 预发环境.
     */
    public static Trait<UserInfo> pre() {
        return user -> user.setEnv("pre");
    }

    /**
     * 灰度环境.
     */
    public static Trait<UserInfo> gray() {
        return user -> user.setEnv("gray");
    }

    /**
     * 指定环境.
     */
    public static Trait<UserInfo> env(String env) {
        return user -> user.setEnv(env);
    }

    // ==================== 身份标识 Traits ====================

    /**
     * 设置用户身份标识.
     */
    public static Trait<UserInfo> identity(String ipId, String ipRoleId, String outChannelId) {
        return user -> {
            user.setIpId(ipId);
            user.setIpRoleId(ipRoleId);
            user.setOutChannelId(outChannelId);
        };
    }

    /**
     * 使用随机 UUID 生成身份标识.
     */
    public static Trait<UserInfo> randomIdentity() {
        return user -> {
            String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            user.setIpId("IP" + uuid);
            user.setIpRoleId("ROLE" + uuid);
            user.setOutChannelId("CH" + uuid);
        };
    }

    // ==================== 分片/执行 Traits ====================

    /**
     * 设置分片 ID.
     */
    public static Trait<UserInfo> splitId(String splitId) {
        return user -> user.setSplitId(splitId);
    }

    /**
     * 设置下次执行日期.
     */
    public static Trait<UserInfo> nextExecuteDate(String date) {
        return user -> user.setNextExecuteDate(date);
    }

    /**
     * 设置收益日期.
     */
    public static Trait<UserInfo> profitDate(String date) {
        return user -> user.setProfitDate(date);
    }

    /**
     * 可执行状态 (设置关键必填字段).
     */
    public static Trait<UserInfo> readyToRun() {
        return user -> {
            user.setBsnId(UUID.randomUUID().toString().replace("-", ""));
            user.setTntInstId("TNT001");
            user.setDelF("N");
            user.setCcy("CNY");
            user.setShardId("01");
            user.setProfitDate("20240101");
            user.setDayProfit(BigDecimal.ZERO);
            user.setNextRunTime(LocalDateTime.now());
            user.setGmtCreate(LocalDateTime.now());
            user.setGmtModified(LocalDateTime.now());
        };
    }

    // ==================== 组合 Traits ====================

    /**
     * 待处理用户 (初始状态 + 可执行).
     */
    public static Trait<UserInfo> pendingUser() {
        return readyToRun()
            .and(initial())
            .and(prod())
            .and(splitId("01"))
            .and(nextExecuteDate("20240101"));
    }

    /**
     * 重试用户 (失败状态 + 可执行).
     */
    public static Trait<UserInfo> retryUser() {
        return readyToRun()
            .and(failed())
            .and(prod())
            .and(splitId("01"))
            .and(nextExecuteDate("20240101"));
    }

    /**
     * 已完成用户 (成功状态).
     */
    public static Trait<UserInfo> completedUser() {
        return readyToRun()
            .and(success())
            .and(prod())
            .and(splitId("01"))
            .and(nextExecuteDate("20240101"));
    }

    // ==================== 其他 Traits ====================

    /**
     * 设置日收益.
     */
    public static Trait<UserInfo> dayProfit(double amount) {
        return user -> user.setDayProfit(BigDecimal.valueOf(amount));
    }

    /**
     * 设置货币.
     */
    public static Trait<UserInfo> currency(String ccy) {
        return user -> user.setCcy(ccy);
    }

    /**
     * 设置租户 ID.
     */
    public static Trait<UserInfo> tenant(String tntInstId) {
        return user -> user.setTntInstId(tntInstId);
    }
}
