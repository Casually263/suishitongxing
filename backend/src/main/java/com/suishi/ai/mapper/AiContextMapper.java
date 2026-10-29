package com.suishi.ai.mapper;

import org.apache.ibatis.annotations.*;
import java.util.Map;

/** No query in this mapper selects config, options, answers, history, profile or private submissions. */
public interface AiContextMapper {
    @Select("""
        SELECT p.id progressId,p.session_id sessionId,p.current_node nodeId,p.chapter_id chapterId,
               p.perspective,s.mode partnerMode,n.node_type nodeType,
               JSON_EXTRACT(n.content_i18n,'$.title') nodeTitle,
               CASE WHEN n.node_type='dual' THEN
                 JSON_EXTRACT(n.content_i18n,CASE
                   WHEN s.mode='SCRIPT' AND p.perspective='A' THEN '$.clueB'
                   WHEN s.mode='SCRIPT' AND p.perspective='B' THEN '$.clueA'
                   WHEN p.perspective='A' THEN '$.clueA' ELSE '$.clueB' END)
                 ELSE JSON_OBJECT('zh','','en','') END allowedClue
        FROM story_progress p JOIN story_node n ON n.id=p.current_node AND n.story_id=p.story_id
        JOIN story_session s ON s.id=p.session_id
        JOIN sys_user u ON u.id=p.user_id AND u.status='ACTIVE'
        WHERE p.user_id=#{userId} AND p.chapter_id=#{chapterId}
        AND (s.user_a=p.user_id OR s.user_b=p.user_id)
        """)
    Map<String,Object> current(@Param("userId") Long userId,@Param("chapterId") Long chapterId);
    @Select("""
        SELECT COUNT(*) FROM story_progress p JOIN sys_user u ON u.id=p.user_id
        WHERE p.id=#{progressId} AND p.user_id=#{userId} AND p.current_node=#{nodeId}
          AND p.session_id=#{sessionId} AND u.status='ACTIVE' AND u.auth_version=#{authVersion}
        """)
    int stillAllowed(@Param("progressId") Long progressId,@Param("userId") Long userId,@Param("nodeId") Long nodeId,@Param("sessionId") Long sessionId,@Param("authVersion") int authVersion);
}
