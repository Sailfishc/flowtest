package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 稳健理财指标数据表实体.
 */
@TableName("t_zb_data_trm")
public class ZbDataTrm {

    @TableId
    private String bsnId;
    private String tntInstId;
    private String ipId;
    private String ipRoleId;
    private LocalDateTime gmtCreate;
    private LocalDateTime gmtModified;
    @TableField("`date`")
    private String date;
    private String assetId;
    private String primAc;
    private String assetPdId;
    private String assetPdCat;
    private String zbCode;
    private String zbId;
    private BigDecimal zbValue;
    private String ccy;
    private String calcPrecision;
    private Long dataVersion;
    private String prevDate;
    private String nextDate;
    private Long shardIdFirst;
    private Long shardIdSecond;
    private Long delF;
    private String calcSource;
    private String extInfo;

    public ZbDataTrm() {
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

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public String getPrimAc() {
        return primAc;
    }

    public void setPrimAc(String primAc) {
        this.primAc = primAc;
    }

    public String getAssetPdId() {
        return assetPdId;
    }

    public void setAssetPdId(String assetPdId) {
        this.assetPdId = assetPdId;
    }

    public String getAssetPdCat() {
        return assetPdCat;
    }

    public void setAssetPdCat(String assetPdCat) {
        this.assetPdCat = assetPdCat;
    }

    public String getZbCode() {
        return zbCode;
    }

    public void setZbCode(String zbCode) {
        this.zbCode = zbCode;
    }

    public String getZbId() {
        return zbId;
    }

    public void setZbId(String zbId) {
        this.zbId = zbId;
    }

    public BigDecimal getZbValue() {
        return zbValue;
    }

    public void setZbValue(BigDecimal zbValue) {
        this.zbValue = zbValue;
    }

    public String getCcy() {
        return ccy;
    }

    public void setCcy(String ccy) {
        this.ccy = ccy;
    }

    public String getCalcPrecision() {
        return calcPrecision;
    }

    public void setCalcPrecision(String calcPrecision) {
        this.calcPrecision = calcPrecision;
    }

    public Long getDataVersion() {
        return dataVersion;
    }

    public void setDataVersion(Long dataVersion) {
        this.dataVersion = dataVersion;
    }

    public String getPrevDate() {
        return prevDate;
    }

    public void setPrevDate(String prevDate) {
        this.prevDate = prevDate;
    }

    public String getNextDate() {
        return nextDate;
    }

    public void setNextDate(String nextDate) {
        this.nextDate = nextDate;
    }

    public Long getShardIdFirst() {
        return shardIdFirst;
    }

    public void setShardIdFirst(Long shardIdFirst) {
        this.shardIdFirst = shardIdFirst;
    }

    public Long getShardIdSecond() {
        return shardIdSecond;
    }

    public void setShardIdSecond(Long shardIdSecond) {
        this.shardIdSecond = shardIdSecond;
    }

    public Long getDelF() {
        return delF;
    }

    public void setDelF(Long delF) {
        this.delF = delF;
    }

    public String getCalcSource() {
        return calcSource;
    }

    public void setCalcSource(String calcSource) {
        this.calcSource = calcSource;
    }

    public String getExtInfo() {
        return extInfo;
    }

    public void setExtInfo(String extInfo) {
        this.extInfo = extInfo;
    }
}
