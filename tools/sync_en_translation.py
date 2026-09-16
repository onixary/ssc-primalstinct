# -*- coding: utf-8 -*-
"""以 zh_cn 为基准生成 en_us（跳过形态 instincts/abilities 键）。运行：python tools/sync_en_translation.py"""
import json, collections

zh = json.load(open(r'src/main/resources/assets/ssc-primalstinct/lang/zh_cn.json', encoding='utf-8'),
               object_pairs_hook=lambda pairs: collections.OrderedDict(pairs))
old_en = json.load(open(r'src/main/resources/assets/ssc-primalstinct/lang/en_us.json', encoding='utf-8'))

T = {
 # ---- 睡眠/蜷缩 ----
 "ssc-primalstinct.sleep.unavailable": "You can only curl up to sleep at night.",
 "message.ssc-primalstinct.bed_uncomfortable": "You try to sleep in the bed, but the feel of the fabric is unbearable.\nCurling up on the ground would be far more comfortable.",
 "message.ssc-primalstinct.villager_refuses_trade": "The villager looks at you in confusion, unable to grasp what your cries mean.",
 "ssc-primalstinct.sleep.need_ground": "You must be on the ground to curl up.",
 "ssc-primalstinct.sleep.no_vehicle": "Cannot curl up in a vehicle.",
 # ---- Power 名称/描述 ----
 "power.ssc-primalstinct.bed_spawn_only.name": "Restless Nights",
 "power.ssc-primalstinct.bed_spawn_only.description": "Beds only set your spawn point; you cannot sleep in them.",
 "power.ssc-primalstinct.prevent_villager_trade.name": "Trade Refused",
 "power.ssc-primalstinct.prevent_villager_trade.description": "Villagers refuse to trade with you.",
 # ---- 原始呼唤 ----
 "effect.ssc-primalstinct.primal_call": "Primal Call",
 "item.ssc-primalstinct.primal_instinct_fang": "Primal Instinct Fang",
 "item.ssc-primalstinct.primal_instinct_fang.unavailable": "It has no effect. The primal instinct rejects your current form.",
 "chat.ssc-primalstinct.primal_call.started": "A primal call echoes within your heart... Before it fully awakens, there may still be a chance to dispel its effect.",
 "text.autoconfig.ssc-primalstinct-server.option.primalCallKillChance": "Primal Call chance per mob kill",
 "text.autoconfig.ssc-primalstinct-server.option.primalCallDurationSeconds": "Primal Call duration (seconds)",
 # ---- 调试命令 ----
 "ssc-primalstinct.debug.get.header": "Primalstinct state of %s:",
 "ssc-primalstinct.debug.get.line": "  value=%s  level=%s/%s  locked=%s",
 "ssc-primalstinct.debug.get.ssc": "  SSC: form=%s  instinct=%s  rate=%s  sscLock=%s",
 "ssc-primalstinct.debug.set.done": "Set %s of %s to %s.",
 "ssc-primalstinct.debug.reconcile.done": "Reconciled %s: value=%s  level=%s  locked=%s  (changed: %s)",
 "ssc-primalstinct.debug.reconcile.no_change": "Reconciled %s: nothing to change.",
 "ssc-primalstinct.debug.ssc.header": "SSC instinct snapshot of %s:",
 "ssc-primalstinct.debug.ssc.line": "  form=%s  instinctValue=%s  instinctRate=%s  playerInstinctLock=%s",
 "ssc-primalstinct.debug.ssc.not_loaded": "SSC (shape-shifter-curse) is not loaded; adapter is unavailable.",
 "ssc-primalstinct.debug.ssc.read_failed": "Failed to read SSC state: %s",
 "ssc-primalstinct.debug.roster.header": "Primalstinct roster: revision %s, %s selectable / %s total profiles",
 "ssc-primalstinct.debug.roster.parse_errors": "Last parse errors (previous roster kept): %s",
 "ssc-primalstinct.debug.roster.validation_errors": "Last validation errors (previous roster kept): %s",
 "ssc-primalstinct.debug.resolve.header": "Resolution of %s:",
 "ssc-primalstinct.debug.resolve.none": "%s (%s) has no profile (own or inherited).",
 "ssc-primalstinct.debug.needs_player": "This subcommand needs a player context (run in-game or specify a target player).",
 "ssc-primalstinct.debug.set.rejected": "Modification rejected by kernel rules (locked / non-finite).",
 "ssc-primalstinct.debug.rate.set": "Rate contribution %s of %s set to %s/s.",
 "ssc-primalstinct.debug.rate.removed": "Rate contribution %s removed from %s.",
 "ssc-primalstinct.debug.rate.list_header": "Rate contributions of %s (merged %.5f/s):",
 "ssc-primalstinct.debug.powers.header": "Powers of %s (total %s):",
 # ---- 按键/分类/物品组 ----
 "key.ssc-primalstinct.curl_sleep": "Curl Up / Sleep",
 "key.ssc-primalstinct.form_color_menu": "Form Color Menu",
 "key.ssc-primalstinct.codex_page": "Codex Page",
 "category.ssc-primalstinct": "Shape Shifter's Curse - Primal Instinct",
 "itemGroup.ssc-primalstinct": "Shape Shifter's Curse - Primal Instinct",
 # ---- 升降阶 HUD 与对话 ----
 "hud.ssc-primalstinct.level_up": "The primal instinct within you grows stronger.",
 "hud.ssc-primalstinct.level_up.1": "The primal instinct awakens in your blood. You can feel it gradually growing stronger... This will have consequences.",
 "hud.ssc-primalstinct.level_up.2": "As the primal instinct strengthens, you feel more at home in this body. Perhaps that is not a bad thing...",
 "hud.ssc-primalstinct.level_up.3": "You have grown stronger. Your body occasionally refuses to obey, and reading has become a little harder - but it is nothing you cannot handle... right?",
 "hud.ssc-primalstinct.level_up.4": "You can barely tell the primal instinct apart from your own will anymore. You had better do something about it... unless you wish to become a beast entirely.",
 "hud.ssc-primalstinct.level_up.locked": "Hunt... Forage... Survive... A beast needs no extra thoughts.",
 "hud.ssc-primalstinct.level_down": "You sense the primal instinct within you shrink back; your thoughts grow a little clearer.",
 "ssc-primalstinct.level_up.1.dialog": "A... new... soul..?",
 "ssc-primalstinct.level_up.2.dialog": "Shapeshifter... how... curious...",
 "ssc-primalstinct.level_up.3.dialog": "Follow... the primal instinct...",
 "ssc-primalstinct.level_up.4.dialog": "Almost there... cast off... the last...",
 "ssc-primalstinct.level_up.locked.dialog": "Follow the guidance... beast...",
 # ---- 形态选择 ----
 "ssc-primalstinct.selection.title": "Choose Your Form",
 "ssc-primalstinct.selection.confirm": "Confirm",
 "ssc-primalstinct.selection.waiting": "Waiting for server...",
 "ssc-primalstinct.form.shape-shifter-curse.ocelot_3.name": "Ocelot",
 "ssc-primalstinct.form.shape-shifter-curse.ocelot_3.description": "A hunter that feeds on raw meat, climbs with ease, and hunts with sweeping claws - but burns through its hunger quickly.",
 "ssc-primalstinct.form.shape-shifter-curse.snow_fox_3.name": "Snow Fox",
 "ssc-primalstinct.form.shape-shifter-curse.snow_fox_3.description": "A predator that draws on cold magic, attacks with leaps and empowered snowballs, and springs swiftly across the land - but fears fire greatly.",
 "ssc-primalstinct.form.shape-shifter-curse.familiar_fox_3.name": "Familiar Fox",
 "ssc-primalstinct.form.shape-shifter-curse.familiar_fox_3.description": "A magical creature altered by outside magic, keeping company with witches and illagers, feeding on the energy of other beings.",
 "ssc-primalstinct.form.shape-shifter-curse.anubis_wolf_3.name": "Jackal",
 "ssc-primalstinct.form.shape-shifter-curse.anubis_wolf_3.description": "A pack leader that summons hosts of wolf spirits to fight, turning withering decay into its own strength.",
 "ssc-primalstinct.form.ssc-primalstinct.primal_ocelot.name": "Primal Ocelot",
 "ssc-primalstinct.form_primal_ocelot.model_name": "Primal Ocelot",
 # ---- 配置界面 ----
 "text.ssc-primalstinct.config.title": "Shape Shifter's Curse - Primal Instinct Config",
 "text.ssc-primalstinct.config.client": "Client Config (Instinct Bar)",
 "text.ssc-primalstinct.config.server": "Server Config (Start Route)",
 "text.ssc-primalstinct.config.close": "Close",
 "text.ssc-primalstinct.config.requires_cloth": "Requires Cloth Config",
 "text.ssc-primalstinct.config.server_hint": "Server config applies after restart / re-entering the world; on servers only the server's local file applies",
 # ---- 提示消息 ----
 "chat.ssc-primalstinct.threshold_approach": "Your instinct is about to reach its next stage...",
 "chat.ssc-primalstinct.remnant.no_response": "...There is no answer here.",
 "chat.ssc-primalstinct.remnant.range_exhausted": "The nearby answers have all gone silent. Explore farther away.",
 "effect.ssc-primalstinct.instinct_overheating": "Instinct Overheating",
 "message.ssc-primalstinct.interaction_random": "You suddenly forget what this thing is even for... strange.",
 "message.ssc-primalstinct.interaction_certain": "You rack your memory, but cannot recall how to use this thing...",
 "message.ssc-primalstinct.drop_random": "You blank out for a moment - the tool slips from your grasp unnoticed.",
 "message.ssc-primalstinct.drop_certain": "Unable to stand the feel of wielding a tool, you fling it aside in disgust.",
 # ---- 图鉴 ----
 "codex.ssc-primalstinct.instincts.open_hint": "Open the Primal Instinct page.",
 "codex.ssc-primalstinct.page.title": "Primal Instinct",
 "codex.ssc-primalstinct.page.open": "View Instinct",
 "codex.ssc-primalstinct.page.back": "Back to the Book of the Shapeshifter",
 "codex.ssc-primalstinct.page.instincts": "Instinct",
 "codex.ssc-primalstinct.page.abilities": "Abilities",
 "codex.ssc-primalstinct.page.stage": "Current Stage L%s / %s",
 # ---- 方块/物品/实体 ----
 "block.ssc-primalstinct.primal_pedestal": "Primal Pedestal",
 "block.ssc-primalstinct.primal_energy_wire": "Primal Energy Wire",
 "block.ssc-primalstinct.primal_altar": "Primal Altar",
 "block.ssc-primalstinct.spent_primal_altar": "Spent Primal Altar",
 "block.ssc-primalstinct.primal_portal_frame": "Primal Portal Frame",
 "block.ssc-primalstinct.primal_portal": "Primal Portal",
 "block.ssc-primalstinct.primal_conversion_platform": "Primal Conversion Platform",
 "item.ssc-primalstinct.sedative_fragment": "Clarity Crystal",
 "item.ssc-primalstinct.primal_remnant": "Primal Remnant",
 "entity.ssc-primalstinct.primal_remnant": "Primal Remnant",
 # ---- 终局 ----
 "ssc-primalstinct.endgame.altar.not_eligible": "It does not respond to your touch.",
 "ssc-primalstinct.endgame.altar.claimed": "Very good...\nChoose...\nUse it now... to still... the instinct... and stay mundane...\nOr cast it into... the frame... for an audience... and metamorphosis...",
 "ssc-primalstinct.endgame.info.primal_ocelot": "Awaken, my kin.\n\nA beast's mind is of no use to me. You have been lent a measure of control over the primal instinct.\nGo back, and fulfill your purpose:\nhunt, and spread my will.",
 "ssc-primalstinct.endgame.pedestal.accepted": "The pedestal accepts your offering - ancient energy stirs along the conduit.",
 "ssc-primalstinct.endgame.pedestal.rejected": "...No response.",
 "ssc-primalstinct.endgame.portal.return_fallback": "Something went wrong; you have been led back to the world's origin.",
 "ssc-primalstinct.endgame.portal.dismount": "The portal refuses entry to other entities.",
 "ssc-primalstinct.endgame.portal.fell": "The edge of the void pushes you back to the sanctum.",
 "ssc-primalstinct.endgame.dialog.conversion.50": "The ground begins to resonate. Something ancient has noticed you - it waits somewhere ahead for a hunter whose instinct has burned to its very edge.",
 "ssc-primalstinct.endgame.dialog.conversion.25": "The air grows thick and warm; instinct surges with every heartbeat. The silhouette ahead sharpens: a silent dais, ringed by shadow.",
 "ssc-primalstinct.endgame.dialog.conversion.8": "It stands before you. A vast shade looms over the dais, countless gazes piercing your fur. Step onto the platform, crouch low - the final metamorphosis awaits your consent.",
 "ssc-primalstinct.endgame.dialog.conversion.stand": "The chill of the dais seeps through your pads. You hear it - not a sound, but a whisper rising from the depths of instinct itself:\n\"Crouch. Give yourself back to the Primal.\"",
 # ---- 镇静碎片消息 ----
 "ssc-primalstinct.item.sedative_fragment.used": "The primal instinct within you fades away; at last, your mind feels clear again.",
 "ssc-primalstinct.item.sedative_fragment.no_effect": "It has no effect on you.",
}

out = collections.OrderedDict()
missing = []
for k in zh:
    if '.page.form.' in k:
        out[k] = old_en[k]          # 形态 instincts/abilities：未定稿，保留现有英文占位
    elif k in T:
        out[k] = T[k]
    else:
        missing.append(k)
        out[k] = old_en.get(k, '')

assert not missing, missing
with open(r'src/main/resources/assets/ssc-primalstinct/lang/en_us.json', 'w', encoding='utf-8', newline='\n') as fp:
    json.dump(out, fp, ensure_ascii=False, indent=2)
    fp.write('\n')

# 校验：键集一致、顺序一致、占位符数量一致
zh2 = json.load(open(r'src/main/resources/assets/ssc-primalstinct/lang/zh_cn.json', encoding='utf-8'))
en2 = json.load(open(r'src/main/resources/assets/ssc-primalstinct/lang/en_us.json', encoding='utf-8'))
assert set(zh2) == set(en2), set(zh2) ^ set(en2)
assert list(zh2.keys()) == list(en2.keys())
for k in zh2:
    assert zh2[k].count('%s') == en2[k].count('%s'), (k, zh2[k], en2[k])
print('OK:', len(en2), 'keys, order kept, placeholders matched')
