"""原始化身（primal_avatar）实体贴图生成器。

贴图 256x256，与 bbmodel 的 project resolution / geo 的 texture_width,height 一致。

布局与 3dmodels/entity/primal_avatar.geo.bbmodel 里每个 cube 的 box UV 偏移一致：
UV 偏移由下面的 shelf 装箱算法产生，JS 侧（Blockbench）用完全相同的排序与装箱规则，
所以两边必须保持同步 —— 改动任一处的排序键或间隙都会让贴图错位。

box UV 排布规则（uv_offset=(u,v)，cube 尺寸 (dx,dy,dz)）：
    east  : (u,        v+dz, dz, dy)
    north : (u+dz,     v+dz, dx, dy)
    west  : (u+dz+dx,  v+dz, dz, dy)
    south : (u+dz+2dx, v+dz, dx, dy)
    up    : (u+dz,     v,    dx, dz)
    down  : (u+dz+dx,  v,    dx, dz)
V 轴方向：侧面贴图的上边缘 = 模型的上端，下边缘 = 模型的下端。

配色：手指沿自身长度由底部红色渐变到顶部暗色；眼柄骨白色；
眼球红色；瞳仁亮黄。所有渐变都按模型空间的世界 Y 计算。
"""
from __future__ import annotations

from PIL import Image

SIZE = 256
GAP = 1
MAGENTA = (255, 0, 255, 255)

# ---- 每个 cube 的模型空间世界 Y 范围（由 Blockbench 实测输出，勿手改） ----
WORLD_Y = {
    "finger_a_lower": (-6.3, 50.8),
    "finger_a_upper": (34.9, 73.8),
    "finger_b_lower": (-5.9, 38.0),
    "finger_b_upper": (26.1, 54.5),
    "finger_c_lower": (-5.5, 47.2),
    "finger_c_upper": (36.1, 69.3),
    "finger_d_lower": (-2.0, 48.2),
    "finger_d_upper": (37.3, 75.0),
    "detail_c_stalk1": (43.6, 68.0),
    "detail_c_stalk2": (61.0, 74.5),
    "detail_c_stalk3": (67.8, 75.3),
    "detail_c_eye": (67.5, 75.8),
    "detail_c_pupil": (68.2, 72.7),
    "detail_b_stalk1": (22.2, 35.3),
    "detail_b_stalk2": (29.9, 35.5),
    "detail_d_stalk1": (23.5, 49.9),
    "detail_d_stalk2": (39.1, 54.3),
    "detail_d_stalk3": (39.5, 52.3),
    "detail_d_eye": (36.9, 49.0),
    "detail_d_pupil": (38.0, 44.3),
    "eye_b_ball": (30.7, 40.7),
    "eye_b_pupil": (32.7, 38.7),
    "stalk_a_1": (53.9, 69.2),
    "stalk_a_2": (66.4, 77.3),
    "stalk_a_3": (73.5, 82.4),
    "eye_a_ball": (73.2, 87.2),
    "eye_a_pupil": (72.7, 77.8),
}

# ---- 每根手指的渐变起止世界 Y ----
# 起止取在"破土线"附近而不是手指真正的根部：化身实体嵌在转化台下一格的方块里，
# 模型 y 0..16 整段埋在方块内，只有 y>16 可见，红色必须落在可见段的底端才有意义。
VISIBLE_BASE = 14.0
FINGER_Y = {
    "a": (VISIBLE_BASE, 73.8),
    "b": (VISIBLE_BASE, 54.5),
    "c": (VISIBLE_BASE, 69.3),
    "d": (VISIBLE_BASE, 75.0),
}

# name -> (dx, dy, dz)  必须与 Blockbench 中 cube 的整数尺寸一致
CUBES = {
    "finger_a_lower": (20, 54, 18),
    "finger_a_upper": (16, 35, 16),
    "finger_b_lower": (20, 40, 18),
    "finger_b_upper": (16, 23, 16),
    "finger_c_lower": (16, 51, 18),
    "finger_c_upper": (14, 30, 14),
    "finger_d_lower": (18, 48, 16),
    "finger_d_upper": (14, 35, 14),
    "detail_c_stalk1": (6, 23, 6),
    "detail_c_stalk2": (6, 12, 5),
    "detail_c_stalk3": (5, 11, 5),
    "detail_c_eye": (6, 5, 6),
    "detail_c_pupil": (4, 1, 4),
    "detail_b_stalk1": (6, 12, 5),
    "detail_b_stalk2": (5, 13, 5),
    "detail_d_stalk1": (10, 23, 9),
    "detail_d_stalk2": (9, 15, 8),
    "detail_d_stalk3": (7, 15, 6),
    "detail_d_eye": (10, 7, 10),
    "detail_d_pupil": (6, 2, 6),
    "stalk_a_1": (6, 14, 6),
    "stalk_a_2": (5, 10, 5),
    "stalk_a_3": (5, 9, 5),
    "eye_a_ball": (10, 10, 10),
    "eye_a_pupil": (6, 6, 2),
    "eye_b_ball": (10, 10, 10),
    "eye_b_pupil": (6, 6, 2),
}

# 每个 cube 归属的材质类别
#   手指：沿手指自身长度做 红 -> 暗 的渐变
#   眼柄：(t0,t1) 表示该段在整条眼柄上的起止位置，骨白色渐变
KIND = {
    "finger_a_lower": ("finger", "a"),
    "finger_a_upper": ("finger", "a"),
    "finger_b_lower": ("finger", "b"),
    "finger_b_upper": ("finger", "b"),
    "finger_c_lower": ("finger", "c"),
    "finger_c_upper": ("finger", "c"),
    "finger_d_lower": ("finger", "d"),
    "finger_d_upper": ("finger", "d"),
    "stalk_a_1": ("stalk", (0.00, 0.34)),
    "stalk_a_2": ("stalk", (0.34, 0.68)),
    "stalk_a_3": ("stalk", (0.68, 1.00)),
    "detail_b_stalk1": ("stalk", (0.00, 0.45)),
    "detail_b_stalk2": ("stalk", (0.45, 1.00)),
    "detail_c_stalk1": ("stalk", (0.00, 0.30)),
    "detail_c_stalk2": ("stalk", (0.30, 0.65)),
    "detail_c_stalk3": ("stalk", (0.65, 1.00)),
    "detail_d_stalk1": ("stalk", (0.00, 0.40)),
    "detail_d_stalk2": ("stalk", (0.40, 0.72)),
    "detail_d_stalk3": ("stalk", (0.72, 1.00)),
    "eye_a_ball": ("eye", None),
    "eye_b_ball": ("eye", None),
    "detail_c_eye": ("eye", None),
    "detail_d_eye": ("eye", None),
    "eye_a_pupil": ("pupil", None),
    "eye_b_pupil": ("pupil", None),
    "detail_c_pupil": ("pupil", None),
    "detail_d_pupil": ("pupil", None),
}

FINGER_RAMP = [
    (0.00, (180, 54, 45)),
    (0.25, (156, 44, 40)),
    (0.50, (112, 35, 34)),
    (0.75, (62, 27, 31)),
    (1.00, (26, 21, 27)),
]
STALK_RAMP = [
    (0.00, (208, 196, 180)),
    (0.50, (188, 174, 158)),
    (1.00, (150, 136, 128)),
]
EYEBALL_RAMP = [
    (0.00, (152, 28, 32)),
    (0.35, (208, 48, 44)),
    (0.65, (196, 40, 38)),
    (1.00, (126, 24, 30)),
]
PUPIL_COLOR = (250, 214, 74)
FINGER_FACE_DARKEN = (("up", 0.55), ("down", 0.62))
STALK_FACE_DARKEN = (("up", 0.72), ("down", 0.66))
EYE_FACE_DARKEN = (("up", 0.50), ("down", 0.66))
PUPIL_FACE_DARKEN = (("east", 0.94), ("north", 1.00), ("west", 0.90), ("south", 0.86),
                     ("up", 0.72), ("down", 0.60))

# 期望的装箱结果（与 Blockbench 侧一致），用于自检
EXPECTED_LAYOUT = {
    "finger_a_lower": (0, 0), "finger_c_lower": (77, 0), "finger_d_lower": (146, 0),
    "finger_b_lower": (0, 73), "finger_a_upper": (77, 73), "finger_d_upper": (142, 73),
    "finger_c_upper": (199, 73), "finger_b_upper": (0, 132), "detail_d_stalk1": (65, 132),
    "detail_c_stalk1": (104, 132), "detail_d_stalk2": (129, 132), "detail_d_stalk3": (164, 132),
    "eye_a_ball": (191, 132), "eye_b_ball": (0, 172), "stalk_a_1": (41, 172),
    "detail_b_stalk2": (66, 172), "detail_b_stalk1": (87, 172), "detail_c_stalk2": (110, 172),
    "detail_d_eye": (133, 172), "detail_c_stalk3": (174, 172), "stalk_a_2": (195, 172),
    "stalk_a_3": (216, 172), "detail_c_eye": (0, 193), "detail_d_pupil": (25, 193),
    "eye_a_pupil": (50, 193), "eye_b_pupil": (67, 193), "detail_c_pupil": (84, 193),
}


def pack():
    """shelf 装箱：按 (高降序, 名字升序) 排序，行内左到右，放不下就换行。"""
    items = []
    for name, (dx, dy, dz) in CUBES.items():
        items.append({"n": name, "dx": dx, "dy": dy, "dz": dz,
                      "w": 2 * dx + 2 * dz, "h": dy + dz})
    items.sort(key=lambda e: (-e["h"], e["n"]))
    x = y = row_h = 0
    for e in items:
        if x + e["w"] > SIZE:
            x = 0
            y += row_h + GAP
            row_h = 0
        e["u"], e["v"] = x, y
        x += e["w"] + GAP
        row_h = max(row_h, e["h"])
    return items, y + row_h


def box_regions(u, v, dx, dy, dz):
    return {
        "east": (u, v + dz, u + dz, v + dz + dy),
        "north": (u + dz, v + dz, u + dz + dx, v + dz + dy),
        "west": (u + dz + dx, v + dz, u + dz + 2 * dx, v + dz + dy),
        "south": (u + dz + 2 * dx, v + dz, u + dz + 2 * dx + dz, v + dz + dy),
        "up": (u + dz, v, u + dz + dx, v + dz),
        "down": (u + dz + dx, v, u + dz + 2 * dx, v + dz),
    }


def _hash(x, y, seed):
    h = (x * 374761393 + y * 668265263 + seed * 1442695040888963407) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFFFF


def noise(x, y, seed):
    jx = x + _hash(x, y, seed) % 3
    jy = y + _hash(y, x, seed + 7) % 3
    return _hash(jx, jy, seed + 13) / 65535.0


def ramp(stops, t):
    t = 0.0 if t < 0.0 else (1.0 if t > 1.0 else t)
    for i in range(len(stops) - 1):
        t0, c0 = stops[i]
        t1, c1 = stops[i + 1]
        if t <= t1:
            f = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return tuple(c0[k] + (c1[k] - c0[k]) * f for k in range(3))
    return stops[-1][1]


def shade(rgb, amount, seed, x, y, speckle):
    n = (noise(x, y, seed) - 0.5) * speckle
    return tuple(int(max(0, min(255, (c + n) * amount))) for c in rgb)


def color_for(name, world_y):
    kind, arg = KIND[name]
    if kind == "finger":
        base, tip = FINGER_Y[arg]
        return ramp(FINGER_RAMP, (world_y - base) / (tip - base))
    if kind == "stalk":
        t0, t1 = arg
        lo, hi = WORLD_Y[name]
        f = (world_y - lo) / max(1e-6, hi - lo)
        return ramp(STALK_RAMP, t0 + (t1 - t0) * f)
    if kind == "eye":
        lo, hi = WORLD_Y[name]
        return ramp(EYEBALL_RAMP, (world_y - lo) / max(1e-6, hi - lo))
    return PUPIL_COLOR


def main():
    items, used = pack()
    got = {e["n"]: (e["u"], e["v"]) for e in items}
    if got != EXPECTED_LAYOUT:
        diff = {k: (got.get(k), EXPECTED_LAYOUT.get(k)) for k in set(got) | set(EXPECTED_LAYOUT)
                if got.get(k) != EXPECTED_LAYOUT.get(k)}
        raise SystemExit(f"装箱结果与 Blockbench 不一致，贴图会错位: {diff}")
    if used > SIZE:
        raise SystemExit(f"UV 装不下：需要 {used} 行，贴图只有 {SIZE}")

    img = Image.new("RGBA", (SIZE, SIZE), MAGENTA)
    px = img.load()

    seeds = {name: (i * 37 + 11) for i, name in enumerate(sorted(CUBES))}

    for e in items:
        name = e["n"]
        kind, _ = KIND[name]
        dx, dy, dz = e["dx"], e["dy"], e["dz"]
        u, v = e["u"], e["v"]
        reg = box_regions(u, v, dx, dy, dz)
        lo, hi = WORLD_Y[name]
        seed = seeds[name]
        speckle = {"finger": 30.0, "stalk": 22.0, "eye": 30.0, "pupil": 16.0}[kind]
        darken = {"finger": FINGER_FACE_DARKEN, "stalk": STALK_FACE_DARKEN,
                  "eye": EYE_FACE_DARKEN, "pupil": PUPIL_FACE_DARKEN}[kind]

        for face in ("east", "north", "west", "south"):
            fx0, fy0, fx1, fy1 = reg[face]
            h = fy1 - fy0
            for y in range(fy0, fy1):
                f = (y - fy0) / max(1, h - 1)
                wy = hi + (lo - hi) * f
                base = color_for(name, wy)
                for x in range(fx0, fx1):
                    amount = 1.0
                    if kind == "eye":
                        # 侧面边缘压暗、中间提亮，做出球体感
                        w = fx1 - fx0
                        gx = abs((x - fx0) / max(1, w - 1) - 0.5) * 2.0
                        amount = 1.12 - 0.30 * gx * gx
                    px[x, y] = shade(base, amount, seed, x, y, speckle) + (255,)

        for face, amount in darken:
            fx0, fy0, fx1, fy1 = reg[face]
            base = color_for(name, hi if face == "up" else lo)
            for y in range(fy0, fy1):
                for x in range(fx0, fx1):
                    px[x, y] = shade(base, amount, seed, x, y, speckle) + (255,)

    out = "src/main/resources/assets/ssc-primalstinct/textures/entity/primal_avatar.png"
    img.save(out)
    print(f"wrote {out} {img.size}, UV rows used {used}/{SIZE}")

    leftovers = 0
    for e in items:
        reg = box_regions(e["u"], e["v"], e["dx"], e["dy"], e["dz"])
        for fx0, fy0, fx1, fy1 in reg.values():
            for y in range(fy0, fy1):
                for x in range(fx0, fx1):
                    if px[x, y][:3] == MAGENTA[:3]:
                        leftovers += 1
    print("unpainted pixels inside used regions:", leftovers)


if __name__ == "__main__":
    main()
