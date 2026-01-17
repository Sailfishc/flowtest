-- User table (using t_user to avoid reserved word)
CREATE TABLE IF NOT EXISTS t_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(200),
    balance DECIMAL(10,2) DEFAULT 0,
    level VARCHAR(20) DEFAULT 'NORMAL',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Product table
CREATE TABLE IF NOT EXISTS t_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    stock INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'IN_STOCK'
);

-- Order table
CREATE TABLE IF NOT EXISTS t_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'CREATED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Audit log table
CREATE TABLE IF NOT EXISTS t_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(100),
    entity_id BIGINT,
    message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS `t_asset_info` (
                                     `tnt_inst_id`        VARCHAR(16) NOT NULL comment '租户实例ID',
                                     `bsn_id`            VARCHAR(64) NOT NULL comment '业务ID',
                                     `ip_id`             VARCHAR(32) NOT NULL comment 'IP ID',
                                     `ip_role_id`        VARCHAR(32) NOT NULL comment 'IP角色ID',
                                     `out_channel_id`    VARCHAR(64) NOT NULL comment '外部渠道ID',
                                     `asset_id`          VARCHAR(64) NOT NULL comment '资产ID（余利宝主账户/周月稳增子账户/存款ipRoleId）',
                                     `asset_pd_cat`      VARCHAR(50) NOT NULL comment '资产产品类目',
                                     `ta_code`           VARCHAR(32) NOT NULL comment '机构编码：存款固定标识',
                                     `fund_code`         VARCHAR(32) NOT NULL comment '基金代码：存款固定标识',
                                     `status`            VARCHAR(16) NOT NULL comment '状态',
                                     `day_profit`        decimal(20,8) NOT NULL comment '日收益',
                                     `ccy`               VARCHAR(10) NOT NULL comment '币种',
                                     `profit_date`       VARCHAR(8) NOT NULL comment '收益日期：yyyyMMdd格式',
                                     `next_execute_date` VARCHAR(8) NOT NULL comment '下次执行日期(yyyyMMdd格式)',
                                     `next_run_time`     datetime NOT NULL comment '下次运行时间(yyyy-MM-dd hh:mi:ss格式)',
                                     `account_status`    VARCHAR(8) NOT NULL comment '账户状态:1:正常，0异常',
                                     `shard_id`          VARCHAR(2) NOT NULL comment '分库ID：00-99',
                                     `split_id`          VARCHAR(2) NOT NULL comment '拆分ID：00-99',
                                     `del_f`             VARCHAR(1) NOT NULL comment '删除标识',
                                     `env`               VARCHAR(16) NOT NULL comment '环境：pre/gray/prod',
                                     `ext_info`          VARCHAR(5120)  comment '扩展信息',
                                     `gmt_create`        datetime NOT NULL comment '创建时间',
                                     `gmt_modified`      datetime NOT NULL comment '修改时间',
                                     PRIMARY KEY(`bsn_id`),
                                     UNIQUE KEY `uk_tnt_asset_cat_ip_role_out_channel_asset_env` (`tnt_inst_id`, `asset_pd_cat`, `ip_role_id`, `out_channel_id`, `asset_id`, `env`),
                                     key `idx_status_env_split_id_execute_date_run_time` (`status`, `env`, `split_id`, `next_execute_date`, `next_run_time`)
) comment = '资产信息表';

-- 净值信息表
-- create TABLE `t_net_value_info` (
--     `tnt_inst_id` VARCHAR(16) NOT NULL comment '租户实例ID',
--     `bsn_id` VARCHAR(64) NOT NULL comment '业务ID',
--     `ta_code` VARCHAR(32) NOT NULL comment '机构编码',
--     `fund_code` VARCHAR(32) NOT NULL comment '基金代码',
--     `net_date` VARCHAR(8) NOT NULL comment '净值日期',
--     `update_date` VARCHAR(8) NOT NULL comment '更新日期',
--     `nav` DECIMAL(20,8) NOT NULL comment '净值',
--     `wp_income` VARCHAR(64) NOT NULL comment '万份收益',
--     `wp_income_flag` VARCHAR(16) NOT NULL comment '万份收益标识',
--     `del_f` VARCHAR(1) NOT NULL comment '删除标识',
--     `version_id` VARCHAR(16) NOT NULL comment '版本号',
--     `gmt_create` DATETIME NOT NULL comment '创建时间',
--     `gmt_modified` DATETIME NOT NULL comment '修改时间',
--     PRIMARY KEY (`bsn_id`),
--     UNIQUE KEY `uk_fund_code` ( `fund_code`, `net_date`),
--     key `idx_tnt_update_date` (`fund_code`, `update_date`)
-- ) comment = '净值信息表';

-- 任务实例表
CREATE TABLE IF NOT EXISTS `t_task_instance_info` (
    `tnt_inst_id` VARCHAR(16) NOT NULL comment '租户实例ID',
    `bsn_id` VARCHAR(64) NOT NULL comment '业务ID',
    `ta_code` VARCHAR(32) NOT NULL comment '机构编码',
    `biz_date` VARCHAR(8) NOT NULL comment '业务日期',
    `task_key` VARCHAR(64) NOT NULL comment '任务键',
    `status` VARCHAR(16) NOT NULL comment '状态',
    `del_f` VARCHAR(1) NOT NULL comment '删除标识',
    `version_id` VARCHAR(16) NOT NULL comment '版本号',
    `gmt_create` DATETIME NOT NULL comment '创建时间',
    `gmt_modified` DATETIME NOT NULL comment '修改时间',
    PRIMARY KEY (`bsn_id`),
    key `uk_tnt_ta_biz_date` (`ta_code`, `task_key`, `biz_date`)
) comment = '任务实例信息表';

CREATE TABLE IF NOT EXISTS `t_user_info` (
                                     `tnt_inst_id`        VARCHAR(16) NOT NULL comment '租户实例ID',
                                     `bsn_id`            VARCHAR(64) NOT NULL comment '业务ID',
                                     `ip_id`             VARCHAR(32) NOT NULL comment 'IP ID',
                                     `ip_role_id`        VARCHAR(32) NOT NULL comment 'IP角色ID',
                                     `out_channel_id`    VARCHAR(64) NOT NULL comment '外部渠道ID',
                                     `status`            VARCHAR(16) NOT NULL comment '状态',
                                     `day_profit`        decimal(20,8) NOT NULL comment '日收益',
                                     `ccy`               VARCHAR(10) NOT NULL comment '币种',
                                     `profit_date`       VARCHAR(8) NOT NULL comment '收益日期：yyyyMMdd格式',
                                     `next_execute_date` VARCHAR(8) NOT NULL comment '下次执行日期(yyyyMMdd格式)',
                                     `next_run_time`     datetime NOT NULL comment '下次运行时间(yyyy-MM-dd hh:mi:ss格式)',
                                     `shard_id`          VARCHAR(2) NOT NULL comment '分库ID：00-99',
                                     `split_id`          VARCHAR(2) NOT NULL comment '拆分ID：00-99',
                                     `del_f`             VARCHAR(1) NOT NULL comment '删除标识',
                                     `env`               VARCHAR(16) NOT NULL comment '环境：pre/gray/prod',
                                     `ext_info`          VARCHAR(5120)  comment '扩展信息',
                                     `gmt_create`        datetime NOT NULL comment '创建时间',
                                     `gmt_modified`      datetime NOT NULL comment '修改时间',
                                     PRIMARY KEY(`bsn_id`),
                                     UNIQUE KEY `uk_user_ip_role_out_channel_env` (`ip_role_id`, `out_channel_id`, `env`),
                                     key `idx_user_status_env_split_execute_run` (`status`, `env`, `split_id`, `next_execute_date`, `next_run_time`)
) comment = '用户信息表';


CREATE TABLE IF NOT EXISTS `t_user_profit_detail` (
                                     `tnt_inst_id`        VARCHAR(16) NOT NULL comment '租户实例ID',
                                     `bsn_id`            VARCHAR(64) NOT NULL comment '业务ID',
                                     `ip_id`             VARCHAR(32) NOT NULL comment 'IP ID',
                                     `ip_role_id`        VARCHAR(32) NOT NULL comment 'IP角色ID',
                                     `out_channel_id`    VARCHAR(64) NOT NULL comment '外部渠道ID',
                                     `profit_type`    VARCHAR(64) NOT NULL comment '收益类型',
                                     `profit_amount`        decimal(20,8) NOT NULL comment '日收益',
                                     `ccy`               VARCHAR(10) NOT NULL comment '币种',
                                     `profit_date`       VARCHAR(8) NOT NULL comment '收益日期：yyyyMMdd格式',
                                     `del_f`             VARCHAR(1) NOT NULL comment '删除标识',
                                     `ext_info`          VARCHAR(5120)  comment '扩展信息',
                                     `gmt_create`        datetime NOT NULL comment '创建时间',
                                     `gmt_modified`      datetime NOT NULL comment '修改时间',
                                     PRIMARY KEY(`bsn_id`),
                                     UNIQUE KEY `uk_tnt_ip_role_out_channel` (`ip_role_id`, `out_channel_id`, `profit_date`, `profit_type`)
) comment = '收益发放明细表';

CREATE TABLE IF NOT EXISTS `t_zb_data_trm` (
                                      `tnt_inst_id` varchar(8) NOT NULL comment '租户',
                                      `bsn_id` varchar(64) NOT NULL comment '业务单号',
                                      `ip_id` varchar(32) NOT NULL comment '参与者ID',
                                      `ip_role_id` varchar(32) NOT NULL comment '参与者角色ID',
                                      `gmt_create` timestamp NOT NULL comment '创建时间',
                                      `gmt_modified` timestamp NOT NULL comment '修改时间',
                                      `date` varchar(8) NOT NULL comment '日期',
                                      `asset_id` varchar(64) NOT NULL comment '资产唯一标识，各品种决定编码方式',
                                      `prim_ac` varchar(50) NULL comment '主账户，部分业务有，没有空着，仅用于查询',
                                      `asset_pd_id` varchar(50) NOT NULL comment '资产品种ID，比如稳健理财的fundCode',
                                      `asset_pd_cat` varchar(50) NOT NULL comment '资产品种分类',
                                      `zb_code` varchar(20) NOT NULL comment '指标代码',
                                      `zb_id` varchar(64) NOT NULL comment '指标Id',
                                      `zb_value` decimal(20,8) NOT NULL comment '指标值',
                                      `ccy` varchar(10) DEFAULT '156' comment '币种，默认人民币-156',
                                      `calc_precision` varchar(20) DEFAULT NULL comment '计算精度',
                                      `data_version` BIGINT(20) NOT NULL comment '数据版本',
                                      `prev_date` varchar(8) DEFAULT NULL comment '前一个数据的日期,00000101代表没有前一个',
                                      `next_date` varchar(8) DEFAULT NULL comment '后一个数据的日期,99991231代表没有后一个',
                                      `shard_id_first` bigint(10)  DEFAULT 0 comment '第一分片id',
                                      `shard_id_second` bigint(10) DEFAULT 0 comment '第二分片id',
                                      `del_f` bigint(10) DEFAULT 0 comment '删除标识',
                                      `calc_source` varchar(16) DEFAULT NULL comment '计算来源',
                                      `ext_info` varchar(51200) comment '拓展字段',
                                      PRIMARY KEY(`bsn_id`),
                                      KEY `idx_role_id` (`ip_role_id`),
                                      KEY `idx_pd_cat_zb_code` (`ip_role_id`, `date`, `asset_pd_cat`, `zb_code`),
                                      KEY `idx_zb_code` (`ip_role_id`, `date`, `zb_code`),
                                      KEY `idx_shard_id_load` (`date`, `asset_pd_cat`, `zb_code`, `shard_id_first`, `shard_id_second`),
                                      UNIQUE KEY `uk_asset_id_zb_id`(`asset_id`, `date`, `zb_id`)
)comment = '稳健理财指标数据';
