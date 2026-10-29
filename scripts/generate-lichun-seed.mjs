// Development authoring source. V4 is immutable once Flyway has applied it.
// Future content revisions must use a NEW story/version and migration, not regenerate an applied V4.
import { writeFileSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const output = fileURLToPath(new URL('../backend/src/main/resources/db/migration/V4__lichun_story.sql', import.meta.url))
if (existsSync(output)) throw new Error('V4 already exists. Publish revisions through a new migration/version.')
const bi = (zh, en) => ({ zh, en })
const option = (id, zh, en) => ({ id, label: bi(zh, en) })
const nodes = [], edges = []
const node = (id, key, type, title, body, config = {}, extra = {}) => nodes.push({ id, key, type, content: { title, body, ...extra }, config })
const edge = (from, to, action = 'continue', condition = {}, priority = 0) => edges.push({ from, to, action, condition, priority })
const foodSource = 'https://www.zjskw.gov.cn/art/2024/8/15/art_1229516293_60168.html'
const astronomySource = 'https://eco.mtk.nao.ac.jp/koyomi/faq/24sekki.html.en'
const customSource = 'https://mzj.sh.gov.cn/lnb-wsws/20240219/0fabcb012d7f420d97c6368fe1a6014d.html'
const cards = [
  { id: 1, title: bi('太阳留下的刻度', 'A marker on the Sun’s path'), body: bi('立春是二十四节气之一，对应太阳视黄经315°。它与农历正月初一不是同一个历法概念。这里开放常年体验，不把当天气温当作节气判据。', 'Lichun, Start of Spring, is one of the twenty-four solar terms and corresponds to a solar longitude of 315°. It is a different calendar concept from Lunar New Year’s Day. This chapter is available for practice throughout the year.'), sourceTitle: '日本国立天文台·二十四节气术语表', sourceUrl: astronomySource },
  { id: 2, title: bi('春盘里的一声“新”', 'A plate to welcome renewal'), body: bi('五辛盘与春盘、春饼有历史联系，但并非各地同一种固定菜谱。“辛”与“新”的谐音被赋予迎新的寓意。本篇谜题采用五种辛味蔬菜这一历史线索。', 'The five-pungent-vegetable plate has historical links with spring platters and pancakes. Recipes vary. The similar sounds of xin, pungent, and xin, new, lend a meaning of renewal. Our puzzle uses this historical food motif.'), sourceTitle: '浙江省社会科学界联合会·寻味二十四节气·立春', sourceUrl: foodSource },
  { id: 3, title: bi('把习俗留给具体的人', 'Customs belong to particular communities'), body: bi('历史记述中，春饼、春蔬和萝卜出现在迎春饮食里。习俗因家庭和地域而异。分享时可以说“在我家”，不必把个人经验说成所有人的传统。', 'Historical accounts describe pancakes, spring vegetables and radishes in foods for welcoming spring. Practices vary between families and places. When sharing, say “in my family” rather than speaking for everyone.'), sourceTitle: '上海市民政局·春节传统食品溯源', sourceUrl: customSource },
]
node(1001, 'courtyard', 'narrative', bi('一封未写完的春信', 'An unfinished spring letter'), bi('你推开岁时馆的木门，纸上只有一句：“请把春天带给远方的朋友。”小满是一位脚本故事向导。它递来三张知识卡，邀你和伙伴补完这封信。故事人物与情节为原创虚构。', 'At the House of Seasons, a letter awaits: “Send a little spring to a friend far away.” Xiaoman, a scripted story guide, offers three knowledge cards. Complete the letter together. The characters and plot are original fiction.'), { effects: { knowledge: 1 } })
node(1002, 'first_path', 'choice', bi('你想从哪里寻找春天？', 'Where will you begin?'), bi('窗边有一册历书、一只食盒和一本空白观察簿。不同的起点会留下不同的经历。', 'A calendar, a food basket and an empty notebook lie by the window. Each offers a different beginning.'), { choices: {
  learn: { effects: { knowledge: 2 }, feedback: bi('你先读历书，再把疑问写在信的边角。', 'You consult the calendar and note a question in the margin.') },
  taste: { effects: { knowledge: 1 }, feedback: bi('你决定从日常饮食认识节气。', 'You begin with the everyday language of food.') },
  observe: { effects: { care: 1 }, feedback: bi('你留下自己的观察，也给远方的经验留出位置。', 'You make room for your observations and your friend’s experience.') },
} }, { options: [option('learn', '翻开历书', 'Open the calendar'), option('taste', '看看食盒', 'Explore the food basket'), option('observe', '观察窗外', 'Look outside')] })
node(1003, 'calendar_path', 'narrative', bi('把日子写准确', 'Finding the right calendar'), bi('你在知识卡上读到315°这个刻度。小满提醒：章节按练习模式开放，不宣称今天就是立春，也不猜测今年的准确节气时刻。', 'The card mentions a marker at 315°. Xiaoman explains that this is a practice chapter: it neither claims that today is Lichun nor guesses this year’s exact time.'))
node(1004, 'basket_path', 'narrative', bi('食盒里的问号', 'A question in the basket'), bi('食盒里画着薄饼和蔬菜。你不急着给每个家庭规定同一份菜单，而是准备问伙伴：“你家怎样迎接新季节？”', 'The basket bears pictures of pancakes and vegetables. Rather than prescribe one menu for every household, you prepare to ask: “How does your family welcome a new season?”'))
node(1005, 'window_path', 'narrative', bi('两扇窗，不同的风景', 'Two windows, different views'), bi('你看见一枚新芽，伙伴窗外也许仍是积雪。你把地点写进观察簿，决定比较各自的体验，而不是要求远方也有同样的天气。', 'You notice a new bud; your friend may still see snow. You record your location and compare experiences without expecting the same weather far away.'))
node(1006, 'calendar_question', 'choice', bi('信上的日期说明', 'A note about the date'), bi('你要给不熟悉中国历法的朋友加一句说明。哪一句更准确？', 'Add an accurate calendar note for a friend unfamiliar with Chinese calendars.'), { choices: {
  solar: { effects: { knowledge: 2 }, feedback: bi('节气依太阳运行划分；春节日期属于农历体系，两者不能混为一谈。', 'Solar terms follow the Sun’s motion; Lunar New Year belongs to the lunar calendar system. Keep the concepts distinct.') },
  lunar: { effects: {}, feedback: bi('这一次把两种日期混淆了。看看知识卡，再继续旅程；答错不会让故事中断。', 'These are different calendar concepts. Revisit the card, then continue: a mistake does not end your journey.') },
} }, { options: [option('solar', '立春是节气，与农历新年不是同一个概念', 'Lichun is a solar term, distinct from Lunar New Year'), option('lunar', '立春就是每年的农历正月初一', 'Lichun always means the first day of the first lunar month')] })
node(1007, 'shared_plate', 'dual', bi('一盘菜，两半线索', 'One plate, two halves of a clue'), bi('岁时馆的旧菜单缺了两个词。请各自根据自己的线索提交一项；两份提交齐备后，由服务器一起核对。小满脚本伙伴会代为填写另一侧。', 'Two words are missing from an old menu. Each person submits using their own clue. The server checks the combination only after both submissions arrive. In script mode, Xiaoman supplies the other side.'), {
  answerA: 'five_pungent', answerB: 'renewal', explanation: bi('本题把“五种辛味蔬菜”与“辛、新谐音迎新”组合起来。五辛盘是历史食俗线索，不代表所有地区都使用相同食材。', 'The puzzle combines five pungent vegetables with the xin/new sound association. This historical motif does not imply a universal regional recipe.'),
}, { clueA: bi('你的菜单残页列出五种带辛味的蔬菜。请为这部分菜品选择合适的名称。', 'Your fragment lists five pungent vegetables. Choose a name for this dish.'), clueB: bi('你的笺注解释：名字中的一个字与“新”同音，寄托迎接新开始的心愿。请选出寓意。', 'Your note says that a word in the name sounds like xin, new, expressing a wish for a fresh beginning. Choose the intended meaning.'), optionsA: [option('five_pungent', '五辛盘', 'Five-pungent-vegetable plate'), option('moon_cake', '月饼', 'Mooncake'), option('rice_ball', '汤圆', 'Glutinous rice balls')], optionsB: [option('renewal', '迎新与更新', 'Welcoming renewal'), option('harvest', '秋收庆贺', 'Celebrating autumn harvest'), option('moon', '赏月寄思', 'Remembering others under the moon')] })
node(1008, 'letter_table', 'narrative', bi('答案之外的一句话', 'A sentence beyond the answer'), bi('小满把两张残页叠在一起：“重要的不只是猜中，也是在各自的线索里为对方留一个位置。”你们把这段发现放进信封，准备添上自己的生活。', 'Xiaoman places the fragments side by side. “Finding an answer matters; making room for another perspective matters too.” You add the discovery to the letter and prepare to share something of your own.'))
node(1009, 'spring_gift', 'gift', bi('给远方的文化回礼', 'A cultural gift for a distant friend'), bi('用中文、英文或两种语言写下：你家或你熟悉的社区怎样迎接新季节？写具体的个人经验即可。这份文字先私有保存，不会自动发送或公开。', 'In Chinese, English or both, describe how your family or a community you know welcomes a new season. Share a specific experience. This text is saved privately and is not automatically sent or published.'), { effects: { care: 1 } })
node(1010, 'spring_checkin', 'checkin', bi('今天的一小片春天', 'A small seasonal observation'), bi('观察身边的一种食物、植物或天气，记录地点与一句感想。无须采摘、购买或生食任何植物；也可以只观察窗外。本轮支持私有文字打卡。', 'Observe a food, a plant or the weather nearby. Record the place and a brief reflection. No picking, buying or eating plants is required; looking out of a window is enough. This version supports private text check-ins.'), { effects: { care: 1 } })
node(1011, 'closing_letter', 'choice', bi('为这封信落款', 'Signing the letter'), bi('最后，你想把什么写在信尾？服务器将根据这一路真实积累的知识与关照，选择结局。', 'What will you put at the end of the letter? The server selects an ending from the knowledge and care accumulated along your actual path.'), { choices: {
  study: { effects: { knowledge: 1 }, feedback: bi('你添上来源，愿好奇心继续生长。', 'You add a source and leave room for curiosity.') },
  care: { effects: { care: 1 }, feedback: bi('你给远方的人留下一个开放的问题。', 'You leave an open question for your distant friend.') },
  quiet: { effects: {}, feedback: bi('你留下一页空白，等待下一次相逢。', 'You leave a blank page for your next meeting.') },
} }, { options: [option('study', '附上知识来源', 'Add a knowledge source'), option('care', '邀请对方分享', 'Invite another perspective'), option('quiet', '留下下次见面的约定', 'Leave a promise to meet again')] })
node(1012, 'spring_returns', 'ending', bi('春回大地', 'Spring returns'), bi('你用准确的知识串起了春信，也听见了另一半线索。完成结算后，服务器会记录立春篇章印章。愿下一封信仍从一个好问题开始。', 'Your spring letter connects careful knowledge with another perspective. Finish to receive a server-recorded Lichun chapter stamp. May the next letter begin with another good question.'))
node(1013, 'across_windows', 'ending', bi('两窗共春', 'Spring across two windows'), bi('你把个人经验说得具体，也给差异留下了空间。知识还可以慢慢补充，关照已经在信里生根。完成结算，保存这一次立春相遇。', 'You shared specific experiences and made room for differences. There is still more to learn, and care has already taken root. Finish to record this Lichun encounter.'))
node(1014, 'first_bud', 'ending', bi('初芽待放', 'A bud yet to open'), bi('你带着新的问题走出岁时馆。并非每次探索都需要满分；愿这枚印章提醒你继续观察、查证和倾听。完成结算后仍可回看本次经历。', 'You leave with new questions. Exploration does not require a perfect score. Let this stamp remind you to observe, verify and listen. Finish to record the journey; its history remains available.'))
edge(1001, 1002)
edge(1002, 1003, 'learn'); edge(1002, 1004, 'taste'); edge(1002, 1005, 'observe')
for (const id of [1003, 1004, 1005]) edge(id, 1006)
for (const action of ['solar', 'lunar']) edge(1006, 1007, action)
edge(1007, 1008, 'submit'); edge(1008, 1009); edge(1009, 1010, 'submit'); edge(1010, 1011, 'submit')
for (const action of ['study', 'care', 'quiet']) {
  edge(1011, 1012, action, { var: 'knowledge', op: 'gte', value: 5 }, 20)
  edge(1011, 1013, action, { var: 'care', op: 'gte', value: 3 }, 10)
  edge(1011, 1014, action)
}
const quote = value => `'${String(value).replaceAll("'", "''")}'`
const j = value => quote(JSON.stringify(value))
const sql = ['-- Source-checked bilingual original adaptation; provenance: docs/content/lichun-sources.md.', '-- Practice release: no fabricated yearly solar-term date or in-season badge.', `INSERT INTO chapter(id,code,title_i18n,intro_i18n,status,release_mode) VALUES(1,'LICHUN',${j(bi('立春 · 一封春信', 'Lichun · A letter of spring'))},${j(bi('从春盘的两半线索出发，写一封通往远方的信。', 'Follow two halves of a spring-platter clue and write a letter to a distant friend.'))},'PUBLISHED','PRACTICE');`, `INSERT INTO story(id,chapter_id,version,status,start_node,initial_variables) VALUES(1,1,1,'PUBLISHED',1001,${j({ knowledge: 0, harmony: 0, care: 0 })});`]
for (const c of cards) sql.push(`INSERT INTO knowledge_card(id,chapter_id,content_i18n,source_title,source_url,review_status) VALUES(${c.id},1,${j({ title: c.title, body: c.body })},${quote(c.sourceTitle)},${quote(c.sourceUrl)},'SOURCE_CHECKED');`)
for (const n of nodes) sql.push(`INSERT INTO story_node(id,story_id,node_key,node_type,content_i18n,config) VALUES(${n.id},1,${quote(n.key)},${quote(n.type)},${j(n.content)},${j(n.config)});`)
edges.forEach((e, i) => sql.push(`INSERT INTO story_edge(id,story_id,from_node,to_node,action_key,priority,condition_json) VALUES(${i + 1},1,${e.from},${e.to},${quote(e.action)},${e.priority},${j(e.condition)});`))
writeFileSync(output, sql.join('\n') + '\n', 'utf8')
console.log(`Created V4: ${nodes.length} nodes, ${edges.length} edges, ${cards.length} knowledge cards.`)
