# -*- coding: utf-8 -*-
"""卡15：生成 Primalstinct HUD 贴图 primal_instinct_bar.png。

布局 160x35（七行 x 5px，条宽 80，原版式左→右填充：左为 0、右为满值）——
沿用旧 SSC instinct_bar 的"每行左半空槽 + 右半填充"结构，使外框/填充随速率分档整体变化
（保留旧系统意图；填充绘制时取右半自 u=80 起的左端段，增长行渐变为左暗右亮＝越接近满值越警示）：
  row 0 (v=0)  下降      钢蓝外框 / 青蓝填充
  row 1 (v=5)  平稳      中性外框 / 灰绿填充（基础自然增长即此档，保持平静外观）
  row 2 (v=10) 微增      暖灰金外框 / 淡琥珀填充      base < rate <= base+0.005
  row 3 (v=15) 增长I     金外框 / 琥珀填充            base+0.005 < rate <= base+0.01
  row 4 (v=20) 增长II    橙外框 / 橙琥珀填充          base+0.01 < rate <= base+0.1
  row 5 (v=25) 增长III   红外框 / 红橙填充            rate > base+0.1
  row 6 (v=30) 满值锁定  金边框+右下斜纹（半透明覆盖，用左半 80px）

分级刻度不烘焙进贴图：阈值随 S2C 快照同步（服务端等级表可自定义），
由 PrimalInstinctHud 按阈值位置程序化绘制（左 0 右满 → 阈值像素 = 左端 + 比例宽）。

运行：python tools/gen_primal_bar_texture.py
"""
from PIL import Image

HALF, ROW_H = 80, 5          # 每行左右各 80px：左=空槽，右=填充
W, ROWS = HALF * 2, 7
OUT = r"src/main/resources/assets/ssc-primalstinct/textures/gui/primal_instinct_bar.png"

img = Image.new("RGBA", (W, ROW_H * ROWS), (0, 0, 0, 0))
px = img.load()


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def shade(c, f):
    """f>1 提亮，f<1 压暗（alpha 不变）。"""
    return tuple(min(255, int(round(c[i] * f))) if i < 3 else c[i] for i in range(len(c)))


def draw_half(v, x0, border, fill, bevel=True):
    """绘制一个 80x5 半段：1px 外框 + 内部（fill=interior 颜色）。"""
    for x in range(HALF):
        for y in range(ROW_H):
            gx = x0 + x
            if x in (0, HALF - 1) or y in (0, ROW_H - 1):
                px[gx, v + y] = border
            else:
                base = fill
                if bevel:
                    if y == 1:
                        base = shade(fill, 1.22)
                    elif y == ROW_H - 2:
                        base = shade(fill, 0.68)
                # 左右端 2px 渐暗（圆头感）；填充半段右端是条头，保持最亮
                edge = min(1.0, (x if x0 == 0 else HALF - 1 - x) / 2.0)
                base = lerp(base, shade(base, 0.75), 1.0 - edge) if edge < 1.0 else base
                px[gx, v + y] = tuple(base)


def draw_tier_row(v, border, fill, trough_tint=0.35):
    """一行完整分档：左半空槽（内饰染上分档色）+ 右半填充。"""
    interior = lerp((26, 26, 32, 208), tuple(border[:3]) + (208,), trough_tint)
    draw_half(v, 0, border, tuple(int(interior[i]) for i in range(4)))
    draw_half(v, HALF, border, fill)


# 各分档：外框颜色 / 填充颜色（与 PrimalInstinctHud 的行常量成对维护）
draw_tier_row(0 * ROW_H, (56, 84, 110, 235), (92, 158, 190, 235))    # 下降：钢蓝
draw_tier_row(1 * ROW_H, (14, 14, 18, 235), (118, 148, 126, 235))    # 平稳：中性
draw_tier_row(2 * ROW_H, (96, 84, 44, 235), (176, 152, 86, 235))     # 微增：暖灰金
draw_tier_row(3 * ROW_H, (128, 98, 26, 235), (202, 150, 62, 235))    # 增长I：金
draw_tier_row(4 * ROW_H, (146, 82, 18, 235), (222, 132, 48, 235))    # 增长II：橙
draw_tier_row(5 * ROW_H, (156, 44, 26, 235), (238, 98, 52, 235))     # 增长III：红

# row 6 满值锁定覆盖：金色边框 + 右下斜纹（半透明，叠在整条上；仅左半 80px 使用）
v = 6 * ROW_H
gold_border = (255, 214, 84, 245)
gold_stripe = (255, 200, 60, 165)
for x in range(HALF):
    for y in range(ROW_H):
        if x in (0, HALF - 1) or y in (0, ROW_H - 1):
            px[x, v + y] = gold_border
        elif (x - y) % 8 in (0, 1):
            px[x, v + y] = gold_stripe
        # 其余保持透明（透出下层填充）

img.save(OUT)
print("written", OUT, img.size)
