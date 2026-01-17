package com.example.demo.service;

import com.example.demo.entity.AssetInfo;
import com.example.demo.entity.UserInfo;
import com.example.demo.entity.UserProfitDetail;
import com.example.demo.mapper.UserInfoMapper;
import com.example.demo.traits.AssetInfoTraits;
import com.example.demo.traits.UserInfoTraits;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowtest.core.TestFlow;
import com.flowtest.junit5.FlowTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for UserProfitService using FlowTest framework.
 */
@FlowTest
@SpringBootTest
@Transactional
@DisplayName("用户收益汇总服务测试")
class UserProfitServiceTest {

    @Autowired
    TestFlow flow;

    @Autowired
    UserProfitService userProfitService;

    @Autowired
    UserInfoMapper userInfoMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 场景1: 单用户单资产收益计算 ====================

    @Nested
    @DisplayName("场景: 单用户单资产收益计算")
    class SingleAssetProfit {

        @Test
        @DisplayName("单个余利宝资产收益计算")
        void testSingleYlbAsset() {
            String ipId = "IP001";
            String ipRoleId = "ROLE001";
            String outChannelId = "CH001";

            flow.arrange()
                    .add(UserInfo.class,
                            UserInfoTraits.pendingUser(),
                            UserInfoTraits.identity(ipId, ipRoleId, outChannelId),
                            UserInfoTraits.profitDate("20240101"))
                    .add(AssetInfo.class,
                            AssetInfoTraits.ylbWithProfit(10.50),
                            AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                    .persist()

                    .act(() -> userProfitService.calculateUserProfit(flow.get(UserInfo.class)))

                    .assertThat()
                    .noException()
                    .returnValue(detail -> {
                        assertThat(detail).isNotNull();
                        assertThat(detail.getProfitAmount()).isEqualByComparingTo("10.50");
                        assertThat(detail.getProfitType()).isEqualTo("DAY");
                        assertThat(detail.getIpId()).isEqualTo(ipId);
                    });

            // Verify user status changed
            UserInfo updatedUser = userInfoMapper.selectById(flow.get(UserInfo.class).getBsnId());
            assertThat(updatedUser.getStatus()).isEqualTo("SUCC");
        }

        @Test
        @DisplayName("单个存款资产收益计算")
        void testSingleDepositAsset() {
            String ipId = "IP002";
            String ipRoleId = "ROLE002";
            String outChannelId = "CH002";

            flow.arrange()
                    .add(UserInfo.class,
                            UserInfoTraits.pendingUser(),
                            UserInfoTraits.identity(ipId, ipRoleId, outChannelId),
                            UserInfoTraits.profitDate("20240101"))
                    .add(AssetInfo.class,
                            AssetInfoTraits.depositWithProfit(25.80),
                            AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                    .persist()

                    .act(() -> userProfitService.calculateUserProfit(flow.get(UserInfo.class)))

                    .assertThat()
                    .noException()
                    .returnValue(detail -> {
                        assertThat(detail.getProfitAmount()).isEqualByComparingTo("25.80");
                    });
        }
    }

    // ==================== 场景2: 多资产收益汇总 ====================

    @Nested
    @DisplayName("场景: 多资产收益汇总")
    class MultiAssetProfit {

        @Test
        @DisplayName("多种资产类型收益汇总并验证 extInfo")
        void testMultiAssetProfitWithExtInfo() throws Exception {
            String ipId = "IP003";
            String ipRoleId = "ROLE003";
            String outChannelId = "CH003";

            flow.arrange()
                    .add(UserInfo.class,
                            UserInfoTraits.pendingUser(),
                            UserInfoTraits.identity(ipId, ipRoleId, outChannelId),
                            UserInfoTraits.profitDate("20240101"))
                    .add("ylbAsset", AssetInfo.class,
                            AssetInfoTraits.ylbWithProfit(10.50),
                            AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                    .add("depAsset", AssetInfo.class,
                            AssetInfoTraits.depositWithProfit(20.30),
                            AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                    .add("fundMonthAsset", AssetInfo.class,
                            AssetInfoTraits.fundMonthWithProfit(5.00),
                            AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                    .add("fundTermAsset", AssetInfo.class,
                            AssetInfoTraits.fundTermWithProfit(0.00),
                            AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                    .persist()

                    .act(() -> userProfitService.calculateUserProfit(flow.get(UserInfo.class)))

                    .assertThat()
                    .noException()
                    .returnValue(detail -> {
                        // Total = 10.50 + 20.30 + 5.00 + 0.00 = 35.80
                        assertThat(detail.getProfitAmount()).isEqualByComparingTo("35.80");

                        // Verify extInfo JSON
                        try {
                            Map<String, BigDecimal> extInfo = objectMapper.readValue(
                                    detail.getExtInfo(),
                                    new TypeReference<Map<String, BigDecimal>>() {
                                    }
                            );
                            assertThat(extInfo.get("ylb")).isEqualByComparingTo("10.50");
                            assertThat(extInfo.get("dep")).isEqualByComparingTo("20.30");
                            assertThat(extInfo.get("fund_month")).isEqualByComparingTo("5.00");
                            assertThat(extInfo.get("fund_term")).isEqualByComparingTo("0.00");
                        } catch (Exception e) {
                            throw new AssertionError("Failed to parse extInfo", e);
                        }
                    });
        }

        @Test
        @DisplayName("同类型多资产收益汇总")
        void testSameCategoryMultiAssets() {
            String ipId = "IP004";
            String ipRoleId = "ROLE004";
            String outChannelId = "CH004";

            flow.arrange()
                    .add(UserInfo.class,
                            UserInfoTraits.pendingUser(),
                            UserInfoTraits.identity(ipId, ipRoleId, outChannelId),
                            UserInfoTraits.profitDate("20240101"))
                    .addMany(AssetInfo.class, 3, (asset, index) -> {
                        AssetInfoTraits.ylbWithProfit(10.00 * (index + 1)).apply(asset);
                        AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId).apply(asset);
                    })
                    .persist()

                    .act(() -> userProfitService.calculateUserProfit(flow.get(UserInfo.class)))

                    .assertThat()
                    .noException()
                    .returnValue(detail -> {
                        // Total = 10 + 20 + 30 = 60
                        assertThat(detail.getProfitAmount()).isEqualByComparingTo("60.00");
                    });
        }
    }

    // ==================== 场景2.5: 多资产收益汇总（非事务模式） ====================

    @Nested
    @DisplayName("场景: 多资产收益汇总（非事务模式）")
    class MultiAssetProfitNonTransactional {

        @Test
        @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
        @DisplayName("多种资产类型收益汇总 - 使用自动清理")
        void testMultiAssetProfitWithAutoCleanup() {
            String ipId = "IP_NT_001";
            String ipRoleId = "ROLE_NT_001";
            String outChannelId = "CH_NT_001";

            try {
                flow.arrange()
                        .add(UserInfo.class,
                                UserInfoTraits.pendingUser(),
                                UserInfoTraits.identity(ipId, ipRoleId, outChannelId),
                                UserInfoTraits.profitDate("20240101"))
                        .add("ylbAsset", AssetInfo.class,
                                AssetInfoTraits.ylbWithProfit(15.00),
                                AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                        .add("depAsset", AssetInfo.class,
                                AssetInfoTraits.depositWithProfit(25.00),
                                AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                        .persist()

                        .act(() -> userProfitService.calculateUserProfit(flow.get(UserInfo.class)))

                        .assertThat()
                        .noException()
                        .returnValue(detail -> {
                            // Total = 15.00 + 25.00 = 40.00
                            assertThat(detail.getProfitAmount()).isEqualByComparingTo("40.00");
                        })
                        // 直接通过 entity() 断言状态变更，无需手动查询
                        .entity(UserInfo.class)
                        .has(UserInfo::getStatus, "SUCC")
                        .and()
                        // 断言新增的 UserProfitDetail 记录
                        .newRow(UserProfitDetail.class)
                        .has(UserProfitDetail::getIpId, ipId)
                        .has(UserProfitDetail::getIpRoleId, ipRoleId)
                        .has(UserProfitDetail::getOutChannelId, outChannelId)
                        .has(UserProfitDetail::getProfitType, "DAY")
                        .has(UserProfitDetail::getProfitAmount, new BigDecimal("40.00"))
                        .has(UserProfitDetail::getProfitDate, "20240101")
                        .and();
            } finally {
                // Ensure cleanup happens even if test fails
                flow.cleanup();
            }
        }
    }

    // ==================== 场景3: 非活跃账户排除 ====================

    @Nested
    @DisplayName("场景: 非活跃账户资产排除")
    class InactiveAccountExclusion {

        @Test
        @DisplayName("非活跃账户资产不参与收益计算")
        void testInactiveAccountExcluded() {
            String ipId = "IP005";
            String ipRoleId = "ROLE005";
            String outChannelId = "CH005";

            flow.arrange()
                    .add(UserInfo.class,
                            UserInfoTraits.pendingUser(),
                            UserInfoTraits.identity(ipId, ipRoleId, outChannelId),
                            UserInfoTraits.profitDate("20240101"))
                    .add("activeAsset", AssetInfo.class,
                            AssetInfoTraits.ylbWithProfit(10.00),
                            AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                    .add("inactiveAsset", AssetInfo.class,
                            AssetInfoTraits.validAsset(),
                            AssetInfoTraits.deposit(),
                            AssetInfoTraits.dayProfit(100.00),
                            AssetInfoTraits.inactiveAccount(),
                            AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                    .persist()

                    .act(() -> userProfitService.calculateUserProfit(flow.get(UserInfo.class)))

                    .assertThat()
                    .noException()
                    .returnValue(detail -> {
                        // Only active account's 10.00 is included
                        assertThat(detail.getProfitAmount()).isEqualByComparingTo("10.00");
                    });
        }

        @Test
        @DisplayName("已删除资产不参与收益计算")
        void testDeletedAssetExcluded() {
            String ipId = "IP006";
            String ipRoleId = "ROLE006";
            String outChannelId = "CH006";

            flow.arrange()
                    .add(UserInfo.class,
                            UserInfoTraits.pendingUser(),
                            UserInfoTraits.identity(ipId, ipRoleId, outChannelId),
                            UserInfoTraits.profitDate("20240101"))
                    .add("normalAsset", AssetInfo.class,
                            AssetInfoTraits.ylbWithProfit(15.00),
                            AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                    .add("deletedAsset", AssetInfo.class,
                            AssetInfoTraits.validAsset(),
                            AssetInfoTraits.deposit(),
                            AssetInfoTraits.dayProfit(200.00),
                            AssetInfoTraits.deleted(),
                            AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                    .persist()

                    .act(() -> userProfitService.calculateUserProfit(flow.get(UserInfo.class)))

                    .assertThat()
                    .noException()
                    .returnValue(detail -> {
                        assertThat(detail.getProfitAmount()).isEqualByComparingTo("15.00");
                    });
        }
    }

    // ==================== 场景4: 无资产用户 ====================

    @Nested
    @DisplayName("场景: 无资产用户")
    class NoAssetUser {

        @Test
        @DisplayName("无资产用户收益为零")
        void testNoAssetUserZeroProfit() {
            String ipId = "IP007";
            String ipRoleId = "ROLE007";
            String outChannelId = "CH007";

            flow.arrange()
                    .add(UserInfo.class,
                            UserInfoTraits.pendingUser(),
                            UserInfoTraits.identity(ipId, ipRoleId, outChannelId),
                            UserInfoTraits.profitDate("20240101"))
                    .persist()

                    .act(() -> userProfitService.calculateUserProfit(flow.get(UserInfo.class)))

                    .assertThat()
                    .noException()
                    .returnValue(detail -> {
                        assertThat(detail.getProfitAmount()).isEqualByComparingTo("0.00");
                        assertThat(detail.getExtInfo()).contains("\"ylb\"");
                    });

            // Verify status changed to SUCC even with zero profit
            UserInfo updatedUser = userInfoMapper.selectById(flow.get(UserInfo.class).getBsnId());
            assertThat(updatedUser.getStatus()).isEqualTo("SUCC");
        }
    }

    // ==================== 场景5: 失败用户重试 ====================

    @Nested
    @DisplayName("场景: 失败用户重试")
    class FailedUserRetry {

        @Test
        @DisplayName("失败用户重试成功")
        void testFailedUserRetrySuccess() {
            String ipId = "IP008";
            String ipRoleId = "ROLE008";
            String outChannelId = "CH008";

            flow.arrange()
                    .add(UserInfo.class,
                            UserInfoTraits.retryUser(),
                            UserInfoTraits.identity(ipId, ipRoleId, outChannelId),
                            UserInfoTraits.profitDate("20240101"))
                    .add(AssetInfo.class,
                            AssetInfoTraits.ylbWithProfit(8.88),
                            AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                    .persist()

                    .act(() -> userProfitService.calculateUserProfit(flow.get(UserInfo.class)))

                    .assertThat()
                    .noException()
                    .returnValue(detail -> {
                        assertThat(detail.getProfitAmount()).isEqualByComparingTo("8.88");
                    });

            // Verify status changed from FAIL to SUCC
            UserInfo updatedUser = userInfoMapper.selectById(flow.get(UserInfo.class).getBsnId());
            assertThat(updatedUser.getStatus()).isEqualTo("SUCC");
        }
    }

    // ==================== 场景6: 待处理用户查询 ====================

    @Nested
    @DisplayName("场景: 待处理用户查询")
    class PendingUserQuery {

        @Test
        @DisplayName("已成功用户不在待处理列表中")
        void testSuccessUserNotInPendingList() {
            String ipId = "IP009";
            String ipRoleId = "ROLE009";
            String outChannelId = "CH009";

            flow.arrange()
                    .add("pendingUser", UserInfo.class,
                            UserInfoTraits.pendingUser(),
                            UserInfoTraits.identity(ipId, ipRoleId, outChannelId))
                    .add("successUser", UserInfo.class,
                            UserInfoTraits.completedUser(),
                            UserInfoTraits.identity(ipId + "_S", ipRoleId + "_S", outChannelId + "_S"))
                    .persist()

                    .act(() -> userProfitService.queryPendingUsers("prod", "01", "20240101"))

                    .assertThat()
                    .noException()
                    .returnValue(users -> {
                        assertThat(users).hasSize(1);
                        assertThat(users.get(0).getIpId()).isEqualTo(ipId);
                    });
        }

        @Test
        @DisplayName("根据环境和分片查询待处理用户")
        void testQueryByEnvAndSplit() {
            flow.arrange()
                    // prod env, split 01
                    .add("prodUser1", UserInfo.class,
                            UserInfoTraits.pendingUser(),
                            UserInfoTraits.randomIdentity())
                    // pre env, split 01
                    .add("preUser", UserInfo.class,
                            UserInfoTraits.pendingUser(),
                            UserInfoTraits.pre(),
                            UserInfoTraits.randomIdentity())
                    // prod env, split 02
                    .add("prodUser2", UserInfo.class,
                            UserInfoTraits.pendingUser(),
                            UserInfoTraits.splitId("02"),
                            UserInfoTraits.randomIdentity())
                    .persist()

                    .act(() -> userProfitService.queryPendingUsers("prod", "01", "20240101"))

                    .assertThat()
                    .noException()
                    .returnValue(users -> {
                        // Only prodUser1 matches
                        assertThat(users).hasSize(1);
                    });
        }
    }

    // ==================== 场景7: 批量处理 ====================

    @Nested
    @DisplayName("场景: 批量处理多用户")
    class BatchProcessing {

        @Test
        @DisplayName("批量处理多个待处理用户")
        void testBatchCalculateProfit() {
            flow.arrange()
                    .add("user1", UserInfo.class,
                            UserInfoTraits.pendingUser(),
                            UserInfoTraits.identity("IP101", "ROLE101", "CH101"),
                            UserInfoTraits.profitDate("20240101"))
                    .add("asset1", AssetInfo.class,
                            AssetInfoTraits.ylbWithProfit(10.00),
                            AssetInfoTraits.belongsTo("IP101", "ROLE101", "CH101"))
                    .add("user2", UserInfo.class,
                            UserInfoTraits.pendingUser(),
                            UserInfoTraits.identity("IP102", "ROLE102", "CH102"),
                            UserInfoTraits.profitDate("20240101"))
                    .add("asset2", AssetInfo.class,
                            AssetInfoTraits.depositWithProfit(20.00),
                            AssetInfoTraits.belongsTo("IP102", "ROLE102", "CH102"))
                    .add("user3", UserInfo.class,
                            UserInfoTraits.retryUser(),
                            UserInfoTraits.identity("IP103", "ROLE103", "CH103"),
                            UserInfoTraits.profitDate("20240101"))
                    .add("asset3", AssetInfo.class,
                            AssetInfoTraits.fundMonthWithProfit(30.00),
                            AssetInfoTraits.belongsTo("IP103", "ROLE103", "CH103"))
                    .persist()

                    .act(() -> userProfitService.batchCalculateProfit("prod", "01", "20240101"))

                    .assertThat()
                    .noException()
                    .returnValue(result -> {
                        int successCount = result[0];
                        int failCount = result[1];
                        assertThat(successCount).isEqualTo(3);
                        assertThat(failCount).isEqualTo(0);
                    });

            // Verify all users have status SUCC
            UserInfo user1 = userInfoMapper.selectById(flow.get("user1", UserInfo.class).getBsnId());
            UserInfo user2 = userInfoMapper.selectById(flow.get("user2", UserInfo.class).getBsnId());
            UserInfo user3 = userInfoMapper.selectById(flow.get("user3", UserInfo.class).getBsnId());
            assertThat(user1.getStatus()).isEqualTo("SUCC");
            assertThat(user2.getStatus()).isEqualTo("SUCC");
            assertThat(user3.getStatus()).isEqualTo("SUCC");
        }
    }

    // ==================== 场景8: extInfo JSON 验证 ====================

    @Nested
    @DisplayName("场景: extInfo JSON 内容验证")
    class ExtInfoJsonValidation {

        @Test
        @DisplayName("验证 extInfo 包含所有品类金额")
        void testExtInfoContainsAllCategories() throws Exception {
            String ipId = "IP400";
            String ipRoleId = "ROLE400";
            String outChannelId = "CH400";

            flow.arrange()
                    .add(UserInfo.class,
                            UserInfoTraits.pendingUser(),
                            UserInfoTraits.identity(ipId, ipRoleId, outChannelId),
                            UserInfoTraits.profitDate("20240101"))
                    .add("ylb", AssetInfo.class,
                            AssetInfoTraits.ylbWithProfit(100.00),
                            AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                    .add("dep", AssetInfo.class,
                            AssetInfoTraits.depositWithProfit(200.00),
                            AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                    .add("fundMonth", AssetInfo.class,
                            AssetInfoTraits.fundMonthWithProfit(50.00),
                            AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                    .add("fundTerm", AssetInfo.class,
                            AssetInfoTraits.fundTermWithProfit(25.00),
                            AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                    .persist()

                    .act(() -> userProfitService.calculateUserProfit(flow.get(UserInfo.class)))

                    .assertThat()
                    .noException()
                    .returnValue(detail -> {
                        try {
                            Map<String, BigDecimal> extInfo = objectMapper.readValue(
                                    detail.getExtInfo(),
                                    new TypeReference<Map<String, BigDecimal>>() {
                                    }
                            );

                            assertThat(extInfo).hasSize(4);
                            assertThat(extInfo.get("ylb")).isEqualByComparingTo("100.00");
                            assertThat(extInfo.get("dep")).isEqualByComparingTo("200.00");
                            assertThat(extInfo.get("fund_month")).isEqualByComparingTo("50.00");
                            assertThat(extInfo.get("fund_term")).isEqualByComparingTo("25.00");

                            assertThat(detail.getProfitAmount())
                                    .isEqualByComparingTo("375.00");
                        } catch (Exception e) {
                            throw new AssertionError("Failed to parse extInfo: " + detail.getExtInfo(), e);
                        }
                    });
        }

        @Test
        @DisplayName("无对应品类资产时 extInfo 值为零")
        void testExtInfoZeroForMissingCategories() throws Exception {
            String ipId = "IP401";
            String ipRoleId = "ROLE401";
            String outChannelId = "CH401";

            flow.arrange()
                    .add(UserInfo.class,
                            UserInfoTraits.pendingUser(),
                            UserInfoTraits.identity(ipId, ipRoleId, outChannelId),
                            UserInfoTraits.profitDate("20240101"))
                    .add(AssetInfo.class,
                            AssetInfoTraits.ylbWithProfit(88.88),
                            AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                    .persist()

                    .act(() -> userProfitService.calculateUserProfit(flow.get(UserInfo.class)))

                    .assertThat()
                    .noException()
                    .returnValue(detail -> {
                        try {
                            Map<String, BigDecimal> extInfo = objectMapper.readValue(
                                    detail.getExtInfo(),
                                    new TypeReference<Map<String, BigDecimal>>() {
                                    }
                            );

                            assertThat(extInfo.get("ylb")).isEqualByComparingTo("88.88");
                            assertThat(extInfo.get("dep")).isEqualByComparingTo("0.00");
                            assertThat(extInfo.get("fund_month")).isEqualByComparingTo("0.00");
                            assertThat(extInfo.get("fund_term")).isEqualByComparingTo("0.00");
                        } catch (Exception e) {
                            throw new AssertionError("Failed to parse extInfo", e);
                        }
                    });
        }
    }

    // ==================== 场景9: 返回值断言 ====================

    @Nested
    @DisplayName("场景: 返回值断言")
    class ReturnValueAssertions {

        @Test
        @DisplayName("使用 result 断言收益明细")
        void testResultAssertion() {
            String ipId = "IP500";
            String ipRoleId = "ROLE500";
            String outChannelId = "CH500";

            flow.arrange()
                    .add(UserInfo.class,
                            UserInfoTraits.pendingUser(),
                            UserInfoTraits.identity(ipId, ipRoleId, outChannelId),
                            UserInfoTraits.profitDate("20240101"))
                    .add(AssetInfo.class,
                            AssetInfoTraits.ylbWithProfit(66.66),
                            AssetInfoTraits.belongsTo(ipId, ipRoleId, outChannelId))
                    .persist()

                    .act(() -> userProfitService.calculateUserProfit(flow.get(UserInfo.class)))

                    .assertThat()
                    .noException()
                    .result()
                    .has(UserProfitDetail::getProfitType, "DAY")
                    .has(UserProfitDetail::getProfitAmount, new BigDecimal("66.66"))
                    .and();
        }
    }
}
