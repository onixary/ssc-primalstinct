import json, os

zh = json.load(open(r'src/main/resources/assets/ssc-primalstinct/lang/zh_cn.json', encoding='utf-8'))
en = json.load(open(r'src/main/resources/assets/ssc-primalstinct/lang/en_us.json', encoding='utf-8'))

corpus_parts = []
for root, dirs, files in os.walk('src/main'):
    parts = root.replace('\\', '/').split('/')
    if 'lang' in parts:
        continue
    for fn in files:
        p = os.path.join(root, fn)
        try:
            corpus_parts.append(open(p, encoding='utf-8', errors='ignore').read())
        except Exception:
            pass
corpus = '\n'.join(corpus_parts)

# 动态拼接键族：前缀出现在代码/资源中即视为活
DYNAMIC_PREFIXES = [
    'codex.ssc-primalstinct.page.',        # PrimalstinctCodexText.section 动态拼接
    'ssc-primalstinct.form.',              # formName() 动态拼接
    'text.autoconfig.ssc-primalstinct',    # Cloth Config 按字段名自动生成
    'ssc-primalstinct.level_up.',          # PrimalstinctPresentation.stageDialogKey 动态拼接（.N.dialog / locked.dialog）
]

dead, live = [], []
for key in sorted(zh):
    if key in corpus or any(key.startswith(p) and p in corpus for p in DYNAMIC_PREFIXES):
        live.append(key)
    else:
        dead.append(key)

print('=== 无引用（候选死键） ===')
for k in dead:
    print(' ', k, '=', repr(zh[k])[:70])
print('共', len(dead), '个')
print()
print('zh-en 不对称:', set(zh) ^ set(en))
