package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收益发放明细表实体.
 */
@TableName("t_user_profit_detail")
public class UserProfitDetail {

    @TableId(type = IdType.INPUT)
    private String bsnId;
    private String tntInstId;
    private String ipId;
    private String ipRoleId;
    private String outChannelId;
    private String profitType;
    private BigDecimal profitAmount;
    private String ccy;
    private String profitDate;
    private String delF;
    private String extInfo;
    private LocalDateTime gmtCreate;
    private LocalDateTime gmtModified;

    public UserProfitDetail() {
    }

    public String getBsnId() {
        return bsnId;
    }

    public void setBsnId(String bsnId) {
        this.bsnId = bsnId;
    }

    public String getTntInstId() {
        return tntInstId;
    }

    public void setTntInstId(String tntInstId) {
        this.tntInstId = tntInstId;
    }

    public String getIpId() {
        return ipId;
    }

    public void setIpId(String ipId) {
        this.ipId = ipId;
    }

    public String getIpRoleId() {
        return ipRoleId;
    }

    public void setIpRoleId(String ipRoleId) {
        this.ipRoleId = ipRoleId;
    }

    public String getOutChannelId() {
        return outChannelId;
    }

    public void setOutChannelId(String outChannelId) {
        this.outChannelId = outChannelId;
    }

    public String getProfitType() {
        return profitType;
    }

    public void setProfitType(String profitType) {
        this.profitType = profitType;
    }

    public BigDecimal getProfitAmount() {
        return profitAmount;
    }

    public void setProfitAmount(BigDecimal profitAmount) {
        this.profitAmount = profitAmount;
    }

    public String getCcy() {
        return ccy;
    }

    public void setCcy(String ccy) {
        this.ccy = ccy;
    }

    public String getProfitDate() {
        return profitDate;
    }

    public void setProfitDate(String profitDate) {
        this.profitDate = profitDate;
    }

    public String getDelF() {
        return delF;
    }

    public void setDelF(String delF) {
        this.delF = delF;
    }

    public String getExtInfo() {
        return extInfo;
    }

    public void setExtInfo(String extInfo) {
        this.extInfo = extInfo;
    }

    public LocalDateTime getGmtCreate() {
        return gmtCreate;
    }

    public void setGmtCreate(LocalDateTime gmtCreate) {
        this.gmtCreate = gmtCreate;
    }

    public LocalDateTime getGmtModified() {
        return gmtModified;
    }

    public void setGmtModified(LocalDateTime gmtModified) {
        this.gmtModified = gmtModified;
    }
}
