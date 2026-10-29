package com.suishi.ai.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

public interface AiKnowledgeMapper {
    String HASH="SHA2(CONCAT(CAST(k.content_i18n AS CHAR),CHAR(0),k.source_title,CHAR(0),k.source_url),256)";
    @Select("SELECT CAST(k.id AS CHAR) id,k.content_i18n content,k.source_title sourceTitle,k.source_url sourceUrl,"+HASH+" contentHash,k.review_status reviewStatus,r.decision,r.note,r.reviewed_at reviewedAt FROM knowledge_card k LEFT JOIN ai_knowledge_review r ON r.card_id=k.id AND r.content_hash="+HASH+" ORDER BY k.chapter_id,k.id")
    List<Map<String,Object>> all();
    @Select("SELECT CAST(k.id AS CHAR) id,k.content_i18n content,k.source_title sourceTitle,k.source_url sourceUrl,"+HASH+" contentHash FROM knowledge_card k JOIN ai_knowledge_review r ON r.card_id=k.id AND r.content_hash="+HASH+" AND r.decision='APPROVED' WHERE k.chapter_id=#{chapterId} AND k.review_status='APPROVED' ORDER BY k.id LIMIT 3")
    List<Map<String,Object>> approved(Long chapterId);
    @Select("SELECT k.id,"+HASH+" contentHash,k.content_i18n content,k.source_url sourceUrl FROM knowledge_card k WHERE k.id=#{id} FOR UPDATE")
    Map<String,Object> lock(Long id);
    @Insert("INSERT INTO ai_knowledge_review(card_id,content_hash,decision,reviewed_by,note) VALUES(#{id},#{hash},#{decision},#{actor},#{note}) ON DUPLICATE KEY UPDATE content_hash=VALUES(content_hash),decision=VALUES(decision),reviewed_by=VALUES(reviewed_by),note=VALUES(note),reviewed_at=UTC_TIMESTAMP(3)")
    void review(@Param("id") Long id,@Param("hash") String hash,@Param("decision") String decision,@Param("actor") Long actor,@Param("note") String note);
    @Update("UPDATE knowledge_card SET review_status=#{status} WHERE id=#{id}") void status(@Param("id") Long id,@Param("status") String status);
}
