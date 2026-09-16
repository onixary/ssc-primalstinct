# -*- coding: utf-8 -*-
"""按所属类型整理语言键顺序（zh_cn 与 en_us 同步应用同一顺序）。运行：python tools/sort_lang_keys.py"""
import json, collections

PATHS = [
    r'src/main/resources/assets/ssc-primalstinct/lang/zh_cn.json',
    r'src/main/resources/assets/ssc-primalstinct/lang/en_us.json',
]

# 分区谓词（按显示顺序）：命中第一个分区即归属；组内按 key 字母序
def item_group(k):      return k.startswith('itemGroup.') or k.startswith('category.')
def keybind(k):         return k.startswith('key.')
def config_ui(k):       return k.startswith('text.ssc-primalstinct.config.')
def config_auto(k):     return k.startswith('text.autoconfig.')
def blocks(k):          return k.startswith('block.')
def items(k):           return k.startswith('item.ssc-primalstinct.')
def entities(k):        return k.startswith('entity.')
def effects(k):         return k.startswith('effect.')
def powers(k):          return k.startswith('power.')
def selection(k):       return k.startswith('ssc-primalstinct.selection.')
def form_names(k):      return k.startswith('ssc-primalstinct.form.') or k == 'ssc-primalstinct.form_primal_ocelot.model_name'
def hud(k):             return k.startswith('hud.')
def dialogs(k):         return k.startswith('ssc-primalstinct.level_up.')
def chat(k):            return k.startswith('chat.')
def messages(k):        return k.startswith('message.')
def sleep(k):           return k.startswith('ssc-primalstinct.sleep.')
def item_use(k):        return k.startswith('ssc-primalstinct.item.')
def codex_common(k):    return k.startswith('codex.') and '.page.form.' not in k
def codex_forms(k):     return k.startswith('codex.') and '.page.form.' in k
def endgame(k):         return k.startswith('ssc-primalstinct.endgame.')
def debug(k):           return k.startswith('ssc-primalstinct.debug.')

BUCKETS = [
    ('物品组与按键分类', item_group),
    ('按键绑定', keybind),
    ('配置界面', config_ui),
    ('服务端配置项', config_auto),
    ('方块', blocks),
    ('物品', items),
    ('实体', entities),
    ('状态效果', effects),
    ('Power', powers),
    ('形态选择界面', selection),
    ('形态名称与描述', form_names),
    ('本能HUD（升降阶）', hud),
    ('升阶对话', dialogs),
    ('聊天消息', chat),
    ('操作反馈消息', messages),
    ('蜷缩睡眠', sleep),
    ('物品使用消息', item_use),
    ('图鉴（通用）', codex_common),
    ('图鉴（形态页面）', codex_forms),
    ('终局玩法', endgame),
    ('调试命令', debug),
]

def reorder(d):
    buckets = {name: [] for name, _ in BUCKETS}
    leftovers = []
    for k, v in d.items():
        for name, pred in BUCKETS:
            if pred(k):
                buckets[name].append(k)
                break
        else:
            leftovers.append(k)
    assert not leftovers, '未分类的键: %s' % leftovers
    out = collections.OrderedDict()
    for name, _ in BUCKETS:
        for k in sorted(buckets[name]):
            out[k] = d[k]
    assert len(out) == len(d)
    return out, {name: len(keys) for name, keys in buckets.items() if keys}

for path in PATHS:
    d = json.load(open(path, encoding='utf-8'), object_pairs_hook=lambda pairs: collections.OrderedDict(pairs))
    ordered, stats = reorder(d)
    with open(path, 'w', encoding='utf-8', newline='\n') as fp:
        json.dump(ordered, fp, ensure_ascii=False, indent=2)
        fp.write('\n')
    print('==', path.split('/')[-1], '==')
    for name, n in stats.items():
        print('  %-20s %d' % (name, n))
    print('  合计', len(ordered))

# 两侧顺序必须一致
zh = json.load(open(PATHS[0], encoding='utf-8'))
en = json.load(open(PATHS[1], encoding='utf-8'))
assert list(zh.keys()) == list(en.keys()), 'zh/en 顺序不一致'
print('OK: zh_cn 与 en_us 键顺序完全一致')
