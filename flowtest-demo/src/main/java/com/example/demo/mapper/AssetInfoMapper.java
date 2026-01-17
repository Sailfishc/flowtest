package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.AssetInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资产信息表 Mapper.
 */
@Mapper
public interface AssetInfoMapper extends BaseMapper<AssetInfo> {
}
