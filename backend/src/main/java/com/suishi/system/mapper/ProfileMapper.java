package com.suishi.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.suishi.system.entity.Profile;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

public interface ProfileMapper extends BaseMapper<Profile> {
    @Select("SELECT * FROM user_profile WHERE user_id=#{userId}") Profile forUser(Long userId);
    @Select("SELECT CAST(id AS CHAR) AS id,code,name_i18n AS name FROM interest_tag WHERE enabled=TRUE ORDER BY id") List<Map<String,Object>> tags();
    @Select("SELECT CAST(tag_id AS CHAR) FROM user_tag WHERE user_id=#{userId} ORDER BY tag_id") List<String> userTags(Long userId);
    @Select("SELECT COUNT(*) FROM interest_tag WHERE id=#{id} AND enabled=TRUE") int validTag(Long id);
    @Delete("DELETE FROM user_tag WHERE user_id=#{userId}") void clearTags(Long userId);
    @Insert("INSERT INTO user_tag(user_id,tag_id) VALUES(#{userId},#{tagId})") void addTag(@Param("userId") Long userId, @Param("tagId") Long tagId);
    @Update("UPDATE user_profile SET display_name=#{displayName},country=#{country},native_lang=#{nativeLang},learn_lang=#{learnLang},lang_level=#{langLevel},timezone=#{timezone},avail_slots=#{availSlots},privacy=#{privacy},notification_preferences=#{notificationPreferences},version=version+1 WHERE user_id=#{userId} AND version=#{version}")
    int updateOptimistic(Profile profile);
}
