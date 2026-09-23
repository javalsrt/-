package com.znxsgl.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.znxsgl.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联表 Mapper（RBAC）。
 * 说明：源项目 aiStudy 未提供独立 SysUserRoleMapper，这里创建空 BaseMapper 以满足模块完整性，便于后续按 userId 操作角色关联。
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}