package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * Order Mapper interface.
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
