package com.suishi.system.vo;

import com.suishi.system.dto.ProfileRequest;
import java.util.List;
import java.util.Map;

public record ProfileView(String displayName, String country, String nativeLang, String learnLang,
                          Map<String,Integer> langLevel, String timezone, List<ProfileRequest.Slot> availSlots,
                          List<String> tagIds, Map<String,Boolean> privacy, Map<String,Boolean> notificationPreferences, int version) {}
