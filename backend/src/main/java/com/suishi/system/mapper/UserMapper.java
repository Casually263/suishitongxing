package com.suishi.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.suishi.system.entity.User;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT id FROM sys_user WHERE id=#{id} FOR UPDATE") Long lockStoryUser(Long id);
    @Select("SELECT * FROM sys_user WHERE username_normalized=#{value} OR email_lookup=#{lookup} LIMIT 1")
    User findByIdentifier(@Param("value") String value, @Param("lookup") String lookup);
    @Select("SELECT r.code FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{id} AND r.enabled=TRUE ORDER BY r.id")
    List<String> roles(Long id);
    @Select("SELECT DISTINCT m.permission_code FROM sys_menu m JOIN sys_role_menu rm ON rm.menu_id=m.id JOIN sys_role r ON r.id=rm.role_id JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{id} AND r.enabled=TRUE AND m.enabled=TRUE ORDER BY m.permission_code")
    List<String> permissions(Long id);
    @Insert("INSERT INTO sys_user_role(user_id,role_id) VALUES(#{userId},#{roleId})")
    void addRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
    @Update("UPDATE sys_user SET auth_version=auth_version+1 WHERE id=#{id}")
    int revoke(Long id);
    @Update("UPDATE sys_user SET auth_version=auth_version+1 WHERE id=#{id} AND auth_version=#{version}")
    int revokeIfVersion(@Param("id") Long id,@Param("version") int version);
    @Select("SELECT * FROM sys_user ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<User> page(@Param("limit") int limit, @Param("offset") int offset);
    @Select("SELECT COUNT(*) FROM sys_user") long countUsers();
    @Select("SELECT id FROM sys_role WHERE id=3 FOR UPDATE") Long lockAdminRole();
    @Select("SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id WHERE ur.role_id=3 AND u.status='ACTIVE'")
    long activeAdmins();
    @Update("UPDATE sys_user SET status=#{status},auth_version=auth_version+1 WHERE id=#{id} AND auth_version=#{version}")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("version") int version);
}
