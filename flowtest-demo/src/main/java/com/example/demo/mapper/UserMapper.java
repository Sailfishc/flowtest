package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * User Mapper interface.
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
