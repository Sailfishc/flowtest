package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.entity.AssetInfo;
import com.example.demo.entity.UserInfo;
import com.example.demo.entity.UserProfitDetail;
import com.example.demo.mapper.AssetInfoMapper;
import com.example.demo.mapper.UserInfoMapper;
import com.example.demo.mapper.UserProfitDetailMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 用户收益汇总服务.
 * <p>
 * 调度系统查询状态为 I 或 FAIL 的用户，汇总所有资产的日收益，
 * 生成一条 UserProfitDetail 明细记录。
 */
@Service
public class UserProfitService {

    private static final String STATUS_INITIAL = "I";
    private static final String STATUS_FAIL = "FAIL";
    private static final String STATUS_SUCCESS = "SUCC";

    private static final String ACCOUNT_STATUS_ACTIVE = "1";
    private static final String DEL_FLAG_NO = "N";

    private static final String PROFIT_TYPE_DAY = "DAY";

    private static final String ASSET_CAT_YLB = "ylb";
    private static final String ASSET_CAT_DEPOSIT = "dep";
    private static final String ASSET_CAT_FUND_MONTH = "fund_month";
    private static final String ASSET_CAT_FUND_TERM = "fund_term";

    private final UserInfoMapper userInfoMapper;
    private final AssetInfoMapper assetInfoMapper;
    private final UserProfitDetailMapper userProfitDetailMapper;
    private final ObjectMapper objectMapper;

    public UserProfitService(UserInfoMapper userInfoMapper,
                             AssetInfoMapper assetInfoMapper,
                             UserProfitDetailMapper userProfitDetailMapper) {
        this.userInfoMapper = userInfoMapper;
        this.assetInfoMapper = assetInfoMapper;
        this.userProfitDetailMapper = userProfitDetailMapper;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 查询待处理用户列表.
     *
     * @param env         环境标识
     * @param splitId     分片标识
     * @param executeDate 执行日期
     * @return 待处理用户列表
     */
    public List<UserInfo> queryPendingUsers(String env, String splitId, String executeDate) {
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getEnv, env)
               .eq(UserInfo::getSplitId, splitId)
               .eq(UserInfo::getNextExecuteDate, executeDate)
               .eq(UserInfo::getDelF, DEL_FLAG_NO)
               .in(UserInfo::getStatus, STATUS_INITIAL, STATUS_FAIL);
        return userInfoMapper.selectList(wrapper);
    }

    /**
     * 查询用户有效资产列表.
     *
     * @param ipId         用户 IP ID
     * @param ipRoleId     用户角色 ID
     * @param outChannelId 外部渠道 ID
     * @return 用户有效资产列表
     */
    public List<AssetInfo> queryUserAssets(String ipId, String ipRoleId, String outChannelId) {
        LambdaQueryWrapper<AssetInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AssetInfo::getIpId, ipId)
               .eq(AssetInfo::getIpRoleId, ipRoleId)
               .eq(AssetInfo::getOutChannelId, outChannelId)
               .eq(AssetInfo::getAccountStatus, ACCOUNT_STATUS_ACTIVE)
               .eq(AssetInfo::getDelF, DEL_FLAG_NO);
        return assetInfoMapper.selectList(wrapper);
    }

    /**
     * 计算单个用户的收益并生成明细.
     *
     * @param userInfo 用户信息
     * @return 生成的收益明细
     */
    @Transactional
    public UserProfitDetail calculateUserProfit(UserInfo userInfo) {
        try {
            // 1. 查询用户所有有效资产
            List<AssetInfo> assets = queryUserAssets(
                userInfo.getIpId(),
                userInfo.getIpRoleId(),
                userInfo.getOutChannelId()
            );

            // 2. 按品类分组汇总收益
            Map<String, BigDecimal> categoryProfits = new HashMap<>();
            categoryProfits.put(ASSET_CAT_YLB, BigDecimal.ZERO);
            categoryProfits.put(ASSET_CAT_DEPOSIT, BigDecimal.ZERO);
            categoryProfits.put(ASSET_CAT_FUND_MONTH, BigDecimal.ZERO);
            categoryProfits.put(ASSET_CAT_FUND_TERM, BigDecimal.ZERO);

            BigDecimal totalProfit = BigDecimal.ZERO;
            for (AssetInfo asset : assets) {
                BigDecimal dayProfit = asset.getDayProfit();
                if (dayProfit == null) {
                    dayProfit = BigDecimal.ZERO;
                }
                totalProfit = totalProfit.add(dayProfit);

                String category = asset.getAssetPdCat();
                if (category != null && categoryProfits.containsKey(category)) {
                    categoryProfits.merge(category, dayProfit, BigDecimal::add);
                }
            }

            // 3. 构建 extInfo JSON
            String extInfo = buildExtInfo(categoryProfits);

            // 4. 创建 UserProfitDetail 记录
            UserProfitDetail detail = new UserProfitDetail();
            detail.setBsnId(UUID.randomUUID().toString().replace("-", ""));
            detail.setTntInstId(userInfo.getTntInstId());
            detail.setIpId(userInfo.getIpId());
            detail.setIpRoleId(userInfo.getIpRoleId());
            detail.setOutChannelId(userInfo.getOutChannelId());
            detail.setProfitType(PROFIT_TYPE_DAY);
            detail.setProfitAmount(totalProfit);
            detail.setCcy(userInfo.getCcy());
            detail.setProfitDate(userInfo.getProfitDate());
            detail.setDelF(DEL_FLAG_NO);
            detail.setExtInfo(extInfo);
            detail.setGmtCreate(LocalDateTime.now());
            detail.setGmtModified(LocalDateTime.now());

            userProfitDetailMapper.insert(detail);

            // 5. 更新用户状态为成功
            updateUserStatus(userInfo.getBsnId(), STATUS_SUCCESS);

            return detail;

        } catch (Exception e) {
            // 6. 异常时更新状态为失败
            updateUserStatus(userInfo.getBsnId(), STATUS_FAIL);
            throw new ProfitCalculationException("收益计算失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量计算用户收益.
     *
     * @param env         环境标识
     * @param splitId     分片标识
     * @param executeDate 执行日期
     * @return 处理结果统计 (成功数, 失败数)
     */
    @Transactional
    public int[] batchCalculateProfit(String env, String splitId, String executeDate) {
        List<UserInfo> pendingUsers = queryPendingUsers(env, splitId, executeDate);

        int successCount = 0;
        int failCount = 0;

        for (UserInfo user : pendingUsers) {
            try {
                calculateUserProfit(user);
                successCount++;
            } catch (Exception e) {
                failCount++;
            }
        }

        return new int[]{successCount, failCount};
    }

    /**
     * 更新用户状态.
     */
    private void updateUserStatus(String bsnId, String status) {
        LambdaUpdateWrapper<UserInfo> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserInfo::getBsnId, bsnId)
               .set(UserInfo::getStatus, status)
               .set(UserInfo::getGmtModified, LocalDateTime.now());
        userInfoMapper.update(null, wrapper);
    }

    /**
     * 构建 extInfo JSON.
     */
    private String buildExtInfo(Map<String, BigDecimal> categoryProfits) {
        try {
            return objectMapper.writeValueAsString(categoryProfits);
        } catch (Exception e) {
            return "{}";
        }
    }
}
