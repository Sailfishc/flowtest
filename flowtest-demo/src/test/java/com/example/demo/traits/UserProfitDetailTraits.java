package com.example.demo.traits;

import com.example.demo.entity.UserProfitDetail;
import com.flowtest.core.fixture.Trait;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Trait definitions for UserProfitDetail entity.
 */
public final class UserProfitDetailTraits {

    private UserProfitDetailTraits() {
    }

    // ==================== 收益类型 Traits ====================

    /**
     * 日收益类型.
     */
    public static Trait<UserProfitDetail> dayProfitType() {
        return detail -> detail.setProfitType("DAY");
    }

    /**
     * 月收益类型.
     */
    public static Trait<UserProfitDetail> monthProfitType() {
        return detail -> detail.setProfitType("MONTH");
    }

    /**
     * 指定收益类型.
     */
    public static Trait<UserProfitDetail> profitType(String type) {
        return detail -> detail.setProfitType(type);
    }

    // ==================== 金额 Traits ====================

    /**
     * 设置收益金额.
     */
    public static Trait<UserProfitDetail> amount(double amount) {
        return detail -> detail.setProfitAmount(BigDecimal.valueOf(amount));
    }

    /**
     * 设置收益金额 (BigDecimal).
     */
    public static Trait<UserProfitDetail> amount(BigDecimal amount) {
        return detail -> detail.setProfitAmount(amount);
    }

    // ==================== 关联 Traits ====================

    /**
     * 关联到用户.
     */
    public static Trait<UserProfitDetail> belongsTo(String ipId, String ipRoleId, String outChannelId) {
        return detail -> {
            detail.setIpId(ipId);
            detail.setIpRoleId(ipRoleId);
            detail.setOutChannelId(outChannelId);
        };
    }

    // ==================== 基础设置 Traits ====================

    /**
     * 基础有效收益明细.
     */
    public static Trait<UserProfitDetail> validDetail() {
        return detail -> {
            detail.setBsnId(UUID.randomUUID().toString().replace("-", ""));
            detail.setTntInstId("TNT001");
            detail.setDelF("N");
            detail.setCcy("CNY");
            detail.setGmtCreate(LocalDateTime.now());
            detail.setGmtModified(LocalDateTime.now());
        };
    }

    /**
     * 设置收益日期.
     */
    public static Trait<UserProfitDetail> profitDate(String date) {
        return detail -> detail.setProfitDate(date);
    }

    /**
     * 设置货币.
     */
    public static Trait<UserProfitDetail> currency(String ccy) {
        return detail -> detail.setCcy(ccy);
    }

    /**
     * 设置扩展信息.
     */
    public static Trait<UserProfitDetail> extInfo(String extInfo) {
        return detail -> detail.setExtInfo(extInfo);
    }

    /**
     * 设置租户 ID.
     */
    public static Trait<UserProfitDetail> tenant(String tntInstId) {
        return detail -> detail.setTntInstId(tntInstId);
    }

    // ==================== 组合 Traits ====================

    /**
     * 日收益明细.
     */
    public static Trait<UserProfitDetail> dayProfitDetail(double amount) {
        return validDetail()
            .and(dayProfitType())
            .and(amount(amount));
    }

    /**
     * 月收益明细.
     */
    public static Trait<UserProfitDetail> monthProfitDetail(double amount) {
        return validDetail()
            .and(monthProfitType())
            .and(amount(amount));
    }
}
