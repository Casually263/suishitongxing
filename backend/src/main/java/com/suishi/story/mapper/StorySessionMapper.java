package com.suishi.story.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.suishi.story.entity.StoryEntities.Session;
import com.suishi.story.entity.StoryEntities.Round;
import org.apache.ibatis.annotations.*;
public interface StorySessionMapper extends BaseMapper<Session> {
    @Select("SELECT * FROM story_session WHERE id=#{id} FOR UPDATE") Session lock(Long id);
    @Select("SELECT * FROM story_session WHERE invite_code=#{code} FOR UPDATE") Session invite(String code);
    @Select("SELECT * FROM story_dual_round WHERE session_id=#{sessionId} AND node_id=#{nodeId} FOR UPDATE")
    Round round(@Param("sessionId") Long sessionId,@Param("nodeId") Long nodeId);
}
