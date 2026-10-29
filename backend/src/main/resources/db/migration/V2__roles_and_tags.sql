INSERT INTO sys_role(id,code,name) VALUES (1,'GUIDE','文化向导'),(2,'PARTNER','文化伙伴'),(3,'ADMIN','管理员'),(4,'EDITOR','内容编辑'),(5,'REVIEWER','校对审核');
INSERT INTO sys_menu(id,code,permission_code,name,route) VALUES
 (1,'admin-access','admin:access','管理工作台','/admin'),
 (2,'user-read','user:read','用户管理','/admin/users'),
 (3,'user-write','user:write','用户状态管理',NULL),
 (4,'profile-write','profile:write','我的档案','/profile');
INSERT INTO sys_role_menu(role_id,menu_id) VALUES (1,4),(2,4),(3,1),(3,2),(3,3),(3,4);
INSERT INTO interest_tag(id,code,name_i18n) VALUES
 (1,'food',JSON_OBJECT('zh','时令饮食','en','Seasonal food')),
 (2,'poetry',JSON_OBJECT('zh','诗词文学','en','Poetry')),
 (3,'craft',JSON_OBJECT('zh','传统手作','en','Traditional crafts')),
 (4,'nature',JSON_OBJECT('zh','自然观察','en','Nature')),
 (5,'language',JSON_OBJECT('zh','语言交流','en','Languages'));
