package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户信息表 Mapper.
 */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {
}
