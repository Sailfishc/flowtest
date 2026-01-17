package com.example.demo.traits;

import com.example.demo.entity.AssetInfo;
import com.flowtest.core.fixture.Trait;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Trait definitions for AssetInfo entity.
 */
public final class AssetInfoTraits {

    private AssetInfoTraits() {
    }

    // ==================== 资产类型 Traits ====================

    /**
     * 余利宝资产.
     */
    public static Trait<AssetInfo> ylb() {
        return asset -> asset.setAssetPdCat("ylb");
    }

    /**
     * 存款资产.
     */
    public static Trait<AssetInfo> deposit() {
        return asset -> asset.setAssetPdCat("dep");
    }

    /**
     * 月度基金资产.
     */
    public static Trait<AssetInfo> fundMonth() {
        return asset -> asset.setAssetPdCat("fund_month");
    }

    /**
     * 定期基金资产.
     */
    public static Trait<AssetInfo> fundTerm() {
        return asset -> asset.setAssetPdCat("fund_term");
    }

    /**
     * 指定资产类型.
     */
    public static Trait<AssetInfo> category(String category) {
        return asset -> asset.setAssetPdCat(category);
    }

    // ==================== 收益 Traits ====================

    /**
     * 设置日收益.
     */
    public static Trait<AssetInfo> dayProfit(double amount) {
        return asset -> asset.setDayProfit(BigDecimal.valueOf(amount));
    }

    /**
     * 设置日收益 (BigDecimal).
     */
    public static Trait<AssetInfo> dayProfit(BigDecimal amount) {
        return asset -> asset.setDayProfit(amount);
    }

    // ==================== 账户状态 Traits ====================

    /**
     * 活跃账户 (参与计算).
     */
    public static Trait<AssetInfo> activeAccount() {
        return asset -> asset.setAccountStatus("1");
    }

    /**
     * 非活跃账户 (不参与计算).
     */
    public static Trait<AssetInfo> inactiveAccount() {
        return asset -> asset.setAccountStatus("0");
    }

    // ==================== 关联 Traits ====================

    /**
     * 关联到用户.
     */
    public static Trait<AssetInfo> belongsTo(String ipId, String ipRoleId, String outChannelId) {
        return asset -> {
            asset.setIpId(ipId);
            asset.setIpRoleId(ipRoleId);
            asset.setOutChannelId(outChannelId);
        };
    }

    // ==================== 基础设置 Traits ====================

    /**
     * 基础有效资产.
     */
    public static Trait<AssetInfo> validAsset() {
        return asset -> {
            String uuid = UUID.randomUUID().toString().replace("-", "");
            asset.setBsnId(uuid);
            asset.setTntInstId("TNT001");
            asset.setAccountStatus("1");
            asset.setDelF("N");
            asset.setCcy("CNY");
            asset.setAssetId("AST" + uuid.substring(0, 10));
            asset.setTaCode("TA01");
            asset.setFundCode("FD01");
            asset.setStatus("NORMAL");
            asset.setDayProfit(BigDecimal.ZERO);
            asset.setProfitDate("20240101");
            asset.setNextExecuteDate("20240101");
            asset.setNextRunTime(LocalDateTime.now());
            asset.setShardId("01");
            asset.setSplitId("01");
            asset.setEnv("prod");
            asset.setGmtCreate(LocalDateTime.now());
            asset.setGmtModified(LocalDateTime.now());
        };
    }

    /**
     * 已删除资产.
     */
    public static Trait<AssetInfo> deleted() {
        return asset -> asset.setDelF("Y");
    }

    /**
     * 设置资产 ID.
     */
    public static Trait<AssetInfo> assetId(String assetId) {
        return asset -> asset.setAssetId(assetId);
    }

    /**
     * 设置基金代码.
     */
    public static Trait<AssetInfo> fundCode(String fundCode) {
        return asset -> asset.setFundCode(fundCode);
    }

    /**
     * 设置 TA 代码.
     */
    public static Trait<AssetInfo> taCode(String taCode) {
        return asset -> asset.setTaCode(taCode);
    }

    // ==================== 组合 Traits ====================

    /**
     * 余利宝资产 (带收益).
     */
    public static Trait<AssetInfo> ylbWithProfit(double profit) {
        return validAsset()
            .and(ylb())
            .and(dayProfit(profit))
            .and(activeAccount());
    }

    /**
     * 存款资产 (带收益).
     */
    public static Trait<AssetInfo> depositWithProfit(double profit) {
        return validAsset()
            .and(deposit())
            .and(dayProfit(profit))
            .and(activeAccount());
    }

    /**
     * 月度基金资产 (带收益).
     */
    public static Trait<AssetInfo> fundMonthWithProfit(double profit) {
        return validAsset()
            .and(fundMonth())
            .and(dayProfit(profit))
            .and(activeAccount());
    }

    /**
     * 定期基金资产 (带收益).
     */
    public static Trait<AssetInfo> fundTermWithProfit(double profit) {
        return validAsset()
            .and(fundTerm())
            .and(dayProfit(profit))
            .and(activeAccount());
    }

    /**
     * 非活跃资产 (不参与计算).
     */
    public static Trait<AssetInfo> inactiveAsset() {
        return validAsset()
            .and(inactiveAccount());
    }
}
