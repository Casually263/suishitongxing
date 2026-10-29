package com.suishi.story.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.suishi.story.entity.StoryEntities.Progress;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
public interface StoryProgressMapper extends BaseMapper<Progress> {
    @Select("SELECT * FROM story_progress WHERE user_id=#{userId} AND chapter_id=#{chapterId}")
    Progress find(@Param("userId") Long userId,@Param("chapterId") Long chapterId);
    @Select("SELECT * FROM story_progress WHERE id=#{id} FOR UPDATE") Progress lock(Long id);
    @Select("SELECT * FROM story_progress WHERE session_id=#{sessionId} ORDER BY id FOR UPDATE") List<Progress> members(Long sessionId);
    @Select("SELECT request_hash FROM story_action WHERE progress_id=#{progressId} AND request_id=#{requestId} FOR UPDATE")
    String actionHash(@Param("progressId") Long progressId,@Param("requestId") String requestId);
    @Insert("INSERT INTO story_action(id,progress_id,request_id,request_hash,from_node,to_node) VALUES(#{id},#{progressId},#{requestId},#{hash},#{from},#{to})")
    void action(@Param("id") Long id,@Param("progressId") Long progressId,@Param("requestId") String requestId,@Param("hash") String hash,@Param("from") Long from,@Param("to") Long to);
    @Insert("INSERT INTO story_submission(id,progress_id,node_id,kind,content_i18n) VALUES(#{id},#{progressId},#{nodeId},#{kind},#{content})")
    void submission(@Param("id") Long id,@Param("progressId") Long progressId,@Param("nodeId") Long nodeId,@Param("kind") String kind,@Param("content") String content);
    @Select("SELECT CAST(node_id AS CHAR) nodeId,kind,content_i18n content,visibility,audit_status auditStatus FROM story_submission WHERE progress_id=#{id} ORDER BY created_at")
    List<Map<String,Object>> submissions(Long id);
}
