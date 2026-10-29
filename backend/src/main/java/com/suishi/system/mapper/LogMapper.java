package com.suishi.system.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface LogMapper {
    @Insert("INSERT INTO sys_login_log(id,user_id,channel,success,reason_code) VALUES(#{id},#{userId},#{channel},#{success},#{reason})")
    void login(@Param("id") Long id, @Param("userId") Long userId, @Param("channel") String channel, @Param("success") boolean success, @Param("reason") String reason);
    @Insert("INSERT INTO sys_oper_log(id,actor_id,action,resource_id) VALUES(#{id},#{actor},#{action},#{resource})")
    void operation(@Param("id") Long id, @Param("actor") Long actor, @Param("action") String action, @Param("resource") Long resource);
}
