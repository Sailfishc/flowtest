package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.TaskInstanceInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务实例信息表 Mapper.
 */
@Mapper
public interface TaskInstanceInfoMapper extends BaseMapper<TaskInstanceInfo> {
}
