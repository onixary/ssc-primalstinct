'use strict';
/*
 * 生成原初转化台（primal_conversion_platform）的几何体与贴图。
 *
 * 形体：下半砖（碰撞与轮廓都是原版半砖的 16x8x16），上面浮着一圈半径 1 格的符文阵。
 * 符文阵悬挑出方块边界半格，最外缘正好落在模型坐标 ±16。
 *
 * 配色沿用祭坛的语言：石材偏暗，符文用祭坛激活态的红色系。
 *
 * 用法：node 3dmodels/blocks/tools/gen_conversion_platform.js
 */

const fs = require('fs');
const path = require('path');
const bb = require('./lib/bbmodel.js');
const { encodePng, createCanvas, hex, mix, mulberry32 } = require('./lib/png.js');

const ROOT = path.resolve(__dirname, '../../..');
const SRC_DIR = path.join(ROOT, '3dmodels/blocks');
const GEO_DIR = path.join(ROOT, 'src/main/resources/assets/ssc-primalstinct/geo/block');
const TEX_DIR = path.join(ROOT, 'src/main/resources/assets/ssc-primalstinct/textures/block/gecko');

const NAME = 'primal_conversion_platform';
const TEX = 32;

// ------------------------------------------------------------------ 尺寸

const SLAB_TOP_Y = 8;          // 半砖高度
const RING_Y = 9;              // 符文阵悬浮在台面上方 1 单位
const RING_THICK = 0.5;

const OUTER_R = 16;            // 外环外接半径 = 1 格
const OUTER_WIDTH = 2;         // 环带径向宽度
const INNER_R = 11;
const INNER_WIDTH = 1.5;
const PLATE = 3;               // 符文板边长

// 正八边形：外接半径 R 时边长 s = 2R·sin(22.5°)，边心距 a = R·cos(22.5°)
const OCT = (r) => ({ side: 2 * r * Math.sin(Math.PI / 8), apothem: r * Math.cos(Math.PI / 8) });

// ------------------------------------------------------------------ 几何

const pad = (n) => String(n).padStart(2, '0');

function buildBones() {
  const platform = {
    name: '01_Platform',
    pivot: [0, 0, 0],
    cubes: [
      { name: 'Slab_base', from: [-8, 0, -8], to: [8, 5, 8], color: 0, uv: slabBaseUv() },
      { name: 'Slab_top', from: [-7, 5, -7], to: [7, SLAB_TOP_Y, 7], color: 0, uv: slabTopUv() },
    ],
  };
  for (const [sx, sz] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) {
    const x0 = sx < 0 ? -8 : 6;
    const z0 = sz < 0 ? -8 : 6;
    platform.cubes.push({
      name: 'Stud_' + (sz < 0 ? 'n' : 's') + (sx < 0 ? 'w' : 'e'),
      from: [x0, 5, z0],
      to: [x0 + 2, 7, z0 + 2],
      color: 0,
      uv: uniformUv(R.K),
    });
  }

  const runes = { name: '02_Rune_circle', pivot: [0, RING_Y, 0], cubes: [] };
  const ring = (prefix, radius, width, band, edge) => {
    const { side, apothem } = OCT(radius);
    for (let k = 0; k < 8; k++) {
      const phi = 45 * k;
      const half = width / 2;
      runes.cubes.push({
        name: prefix + '_' + pad(k),
        from: [-side / 2, RING_Y, -apothem - half],
        to: [side / 2, RING_Y + RING_THICK, -apothem + half],
        color: 0,
        uv: ringUv(side, width, band, edge),
        pivot: [0, RING_Y, 0],
        // 记录里存的是"渲染出来的角度"，写进 geo 时 lib 会取负。
        rotation: [0, -phi, 0],
      });
    }
  };
  ring('RingOuter', OUTER_R, OUTER_WIDTH, R.E, R.F);
  ring('RingInner', INNER_R, INNER_WIDTH, R.G, R.H);

  const mid = (OCT(INNER_R).apothem + INNER_WIDTH / 2 + OCT(OUTER_R).apothem - OUTER_WIDTH / 2) / 2;
  for (const [name, dx, dz] of [['n', 0, -1], ['s', 0, 1], ['w', -1, 0], ['e', 1, 0]]) {
    const cx = dx * mid;
    const cz = dz * mid;
    runes.cubes.push({
      name: 'RunePlate_' + name,
      from: [cx - PLATE / 2, RING_Y, cz - PLATE / 2],
      to: [cx + PLATE / 2, RING_Y + RING_THICK, cz + PLATE / 2],
      color: 0,
      uv: ringUv(PLATE, PLATE, R.I, R.J),
    });
  }

  return [platform, runes];
}

// ------------------------------------------------------------------ 图集

/*
 * 32x32 图集分区（先铺满暗色石头，再逐块画）：
 *   A (0,0)   16x5      基座侧面
 *   B (0,5)   14x3      上层侧面
 *   C (0,8)   14x14     上层顶面
 *   D (16,0)  16x16     基座顶面 / 底面
 *   E (0,22)  12.245x2  外环顶面条带
 *   F (0,24)  12.245x.5 外环外侧面
 *   G (0,25)  6.888x1.5 内环顶面条带
 *   H (8,25)  6.888x.5  内环外侧面
 *   I (0,27)  3x3       符文板顶面
 *   J (4,27)  3x0.5     符文板侧面
 *   K (8,27)  2x2       角柱面
 */
const R = {
  A: { x: 0, y: 0, w: 16, h: 5 },
  B: { x: 0, y: 5, w: 14, h: 3 },
  C: { x: 0, y: 8, w: 14, h: 14 },
  D: { x: 16, y: 0, w: 16, h: 16 },
  // 这几块的高度写整数方便绘制；UV 再按面尺寸取子矩形，保证 1 单位 = 1 像素。
  E: { x: 0, y: 22, w: 13, h: 2 },
  F: { x: 0, y: 24, w: 13, h: 1 },
  G: { x: 0, y: 25, w: 7, h: 2 },
  H: { x: 0, y: 27, w: 7, h: 1 },
  I: { x: 16, y: 22, w: PLATE, h: PLATE },
  J: { x: 19, y: 22, w: PLATE, h: 1 },
  K: { x: 22, y: 22, w: 2, h: 2 },
};

const rect = (r) => ({ u0: r.x, v0: r.y, u1: r.x + r.w, v1: r.y + r.h });
const sub = (r, u0, v0, u1, v1) => ({ u0: r.x + u0, v0: r.y + v0, u1: r.x + u1, v1: r.y + v1 });

function slabBaseUv() {
  const side = rect(R.A);
  return { north: side, south: side, east: side, west: side, up: rect(R.D), down: rect(R.D) };
}
function slabTopUv() {
  // 底面被基座顶面盖住，直接复用顶面那格，省得再开一块。
  return { north: rect(R.B), south: rect(R.B), east: rect(R.B), west: rect(R.B), up: rect(R.C), down: rect(R.C) };
}
function uniformUv(r) {
  const q = rect(r);
  return { north: q, south: q, east: q, west: q, up: q, down: q };
}
/**
 * 环带 UV：长边（north/south）用外侧条带，短端（east/west）取同一带子的起始一小段，
 * 顶面用条带区。都按面尺寸取子矩形，拉伸量为零。
 */
function ringUv(width, depth, band, edge) {
  const top = sub(band, 0, 0, width, depth);
  const side = sub(edge, 0, 0, width, RING_THICK);
  const end = sub(edge, 0, 0, depth, RING_THICK);
  return { north: side, south: side, east: end, west: end, up: top, down: top };
}

// ---------------------------------------------------------------- 贴图

/*
 * 石材比祭坛暗一大截，并且往中性灰偏。直接用祭坛那套橄榄绿配红会变成圣诞配色，
 * 所以只在石头里留一点点黄绿倾向，明度压到祭坛的六成左右。
 */
const C = {
  stone: hex('#43443C'),
  stoneHi: hex('#53544A'),
  stoneLo: hex('#35362F'),
  stoneDk: hex('#292A24'),
  groove: hex('#15171A'),
  grooveHi: hex('#1E2126'),
  runeDk: hex('#6E1409'),
  rune: hex('#B4241A'),
  runeHi: hex('#F04A12'),
  runeHot: hex('#FFA43A'),
};

/** 暗色石头底：不规则斑块，避免大片纯色。 */
function paintStone(ctx, r, rnd, base) {
  for (let y = 0; y < r.h; y++) {
    for (let x = 0; x < r.w; x++) {
      const v = rnd();
      ctx.set(r.x + x, r.y + y, v < 0.14 ? C.stoneHi : v < 0.3 ? C.stoneLo : v < 0.36 ? C.stoneDk : base);
    }
  }
}

/**
 * 符文条带：主体是暗凹槽，只在靠近内缘的一行刻断续的亮痕。
 * 大片高饱和色会读成警示条纹，必须压到"暗底 + 细亮线"。
 */
function paintRuneBand(ctx, r, rnd) {
  for (let y = 0; y < r.h; y++) {
    for (let x = 0; x < r.w; x++) {
      // 外侧那行是石头唇口，内侧那行整条压成暗红刻槽
      const c = y === 0 ? C.stoneDk : rnd() < 0.22 ? C.groove : C.runeDk;
      ctx.set(r.x + x, r.y + y, c);
    }
  }
  // 刻槽里每隔一段点一个亮刻痕。密了就会读成红白条纹。
  const row = r.y + r.h - 1;
  for (let u = 3; u < r.w; u += 5) {
    ctx.set(r.x + u, row, C.runeHi);
  }
}

/** 环带外侧的窄边：只在刻痕正下方透出一线红光，其余全暗。 */
function paintRuneEdge(ctx, r, offset) {
  for (let u = 0; u < r.w; u++) {
    ctx.set(r.x + u, r.y, (u + offset) % 5 === 3 ? C.rune : C.groove);
  }
}

function buildTexture() {
  const ctx = createCanvas(TEX, TEX, C.stone);
  const rnd = mulberry32(2718281);

  // 备用区先铺暗石
  paintStone(ctx, { x: 0, y: 0, w: TEX, h: TEX }, rnd, C.stoneLo);

  // A 基座侧面：底暗、上沿有一道受光唇
  paintStone(ctx, R.A, rnd, C.stone);
  ctx.fillRect(R.A.x, R.A.y, R.A.w, 2, C.stoneLo);
  ctx.fillRect(R.A.x, R.A.y + R.A.h - 1, R.A.w, 1, C.stoneHi);
  ctx.fillRect(R.A.x, R.A.y + 3, R.A.w, 1, C.groove);
  for (const x of [0, 15]) ctx.fillRect(R.A.x + x, R.A.y, 1, R.A.h, C.groove);

  // B 上层侧面
  paintStone(ctx, R.B, rnd, C.stone);
  ctx.fillRect(R.B.x, R.B.y, R.B.w, 1, C.stoneHi);
  ctx.fillRect(R.B.x, R.B.y + R.B.h - 1, R.B.w, 1, C.stoneDk);
  for (const x of [0, 13]) ctx.fillRect(R.B.x + x, R.B.y, 1, R.B.h, C.groove);

  // C 上层顶面：下沉的工作面，边上两道凹槽，中央一块浅坑
  paintStone(ctx, R.C, rnd, C.stone);
  const frame = (o, c) => {
    ctx.fillRect(R.C.x + o, R.C.y + o, R.C.w - o * 2, 1, c);
    ctx.fillRect(R.C.x + o, R.C.y + R.C.h - o - 1, R.C.w - o * 2, 1, c);
    ctx.fillRect(R.C.x + o, R.C.y + o, 1, R.C.h - o * 2, c);
    ctx.fillRect(R.C.x + R.C.w - o - 1, R.C.y + o, 1, R.C.h - o * 2, c);
  };
  frame(0, C.stoneDk);
  frame(1, C.groove);
  frame(3, C.grooveHi);
  ctx.fillRect(R.C.x + 5, R.C.y + 5, 4, 4, C.stoneLo);
  for (const [dx, dy] of [[-1, 0], [1, 0], [0, -1], [0, 1]]) {
    ctx.set(R.C.x + 7 + dx * 4, R.C.y + 7 + dy * 4, C.runeDk);
  }

  // D 基座顶面 / 底面
  paintStone(ctx, R.D, rnd, C.stoneLo);
  for (let i = 0; i < R.D.w; i++) {
    ctx.set(R.D.x + i, R.D.y, C.stoneDk);
    ctx.set(R.D.x + R.D.w - 1, R.D.y + i, C.stoneDk);
  }

  // E/F 外环：顶面条带 + 外侧窄边
  paintRuneBand(ctx, R.E, rnd);
  paintRuneEdge(ctx, R.F, 1);

  // G/H 内环
  paintRuneBand(ctx, R.G, rnd);
  paintRuneEdge(ctx, R.H, 0);

  // I/J 符文板：暗底上一个小十字刻痕
  ctx.fillRect(R.I.x, R.I.y, R.I.w, R.I.h, C.groove);
  ctx.fillRect(R.I.x + 1, R.I.y, 1, 3, C.rune);
  ctx.fillRect(R.I.x, R.I.y + 1, 3, 1, C.rune);
  ctx.set(R.I.x + 1, R.I.y + 1, C.runeHi);
  ctx.fillRect(R.J.x, R.J.y, R.J.w, R.J.h, C.groove);
  ctx.set(R.J.x + 1, R.J.y, C.runeHi);

  // K 角柱面：只有一点暗红刻痕，2x2 的面放亮红会变成一大块色斑
  ctx.fillRect(R.K.x, R.K.y, R.K.w, R.K.h, C.grooveHi);
  ctx.set(R.K.x, R.K.y, C.runeDk);
  ctx.set(R.K.x + 1, R.K.y + 1, C.stoneDk);

  return ctx;
}

/** 发光遮罩：只有符文条带、刻痕和符文板亮，石头不亮。 */
function buildEmission() {
  const ctx = createCanvas(TEX, TEX, [0, 0, 0, 0]);
  const put = (r, test) => {
    for (let y = 0; y < r.h; y++) {
      for (let x = 0; x < r.w; x++) {
        const px = (r.x + x) | 0;
        const py = (r.y + y) | 0;
        if (!test(x, y)) continue;
        ctx.set(px, py, [255, 255, 255]);
      }
    }
  };
  put(R.E, (x, y) => y === R.E.h - 1 && x % 5 === 3);
  put(R.F, (x) => (x + 1) % 5 === 3);
  put(R.G, (x, y) => y === R.G.h - 1 && x % 5 === 3);
  put(R.H, (x) => x % 5 === 3);
  put(R.I, (x, y) => x === 1 || y === 1);
  put(R.J, (x) => x === 1);
  // 顶面四角的暗红点缀也带一点辉光
  for (const [dx, dy] of [[-1, 0], [1, 0], [0, -1], [0, 1]]) {
    ctx.set(R.C.x + 7 + dx * 4, R.C.y + 7 + dy * 4, [255, 255, 255]);
  }
  return ctx;
}

// ------------------------------------------------------------------ 主流程

function main() {
  const bones = buildBones();
  const template = { model: JSON.parse(fs.readFileSync(path.join(SRC_DIR, 'primal_altar.bbmodel'), 'utf8')), textureDir: TEX_DIR };

  const texture = buildTexture();
  const emission = buildEmission();
  fs.writeFileSync(path.join(TEX_DIR, NAME + '.png'), encodePng(TEX, TEX, texture.data));
  fs.writeFileSync(path.join(TEX_DIR, NAME + '_emission.png'), encodePng(TEX, TEX, emission.data));
  fs.writeFileSync(path.join(GEO_DIR, NAME + '.geo.json'), bb.geoJson(bb.toGeo(NAME, bones, TEX)));
  fs.writeFileSync(path.join(SRC_DIR, NAME + '.bbmodel'), JSON.stringify(bb.toBbmodel(NAME, NAME, bones, template)));

  // 自检
  let cubes = 0;
  let minAxis = Infinity;
  let lo = [1e9, 1e9, 1e9];
  let hi = [-1e9, -1e9, -1e9];
  for (const bone of bones) {
    for (const cube of bone.cubes) {
      cubes++;
      const size = [cube.to[0] - cube.from[0], cube.to[1] - cube.from[1], cube.to[2] - cube.from[2]];
      minAxis = Math.min(minAxis, ...size);
      if (Math.min(...size) < 0.5) console.log('  ! 轴小于 0.5:', cube.name, size);
      for (const face of bb.FACES) {
        const r = cube.uv[face];
        const want = face === 'up' || face === 'down'
          ? [size[0], size[2]]
          : face === 'north' || face === 'south'
            ? [size[0], size[1]]
            : [size[2], size[1]];
        if (Math.abs(r.u1 - r.u0 - want[0]) > 1e-6 || Math.abs(r.v1 - r.v0 - want[1]) > 1e-6) {
          console.log('  ! UV 比例不符:', cube.name, face, [bb.round(r.u1 - r.u0), bb.round(r.v1 - r.v0)], '应为', want);
        }
        if (r.u0 < 0 || r.v0 < 0 || r.u1 > TEX || r.v1 > TEX) console.log('  ! UV 越界:', cube.name, face, r);
      }
      // 旋转后的真实包围盒：绕 pivot 把 8 个角转一遍
      for (let c = 0; c < 8; c++) {
        let p = [0, 1, 2].map((a) => cube.from[a] + ((c >> a) & 1) * size[a]);
        if (cube.rotation) {
          const pivot = cube.pivot;
          const t = (cube.rotation[1] * Math.PI) / 180;
          const dx = p[0] - pivot[0];
          const dz = p[2] - pivot[2];
          p = [pivot[0] + dx * Math.cos(t) + dz * Math.sin(t), p[1], pivot[2] - dx * Math.sin(t) + dz * Math.cos(t)];
        }
        for (let a = 0; a < 3; a++) {
          lo[a] = Math.min(lo[a], p[a]);
          hi[a] = Math.max(hi[a], p[a]);
        }
      }
    }
  }
  console.log(`转化台：${bones.length} 根骨骼 / ${cubes} 个立方体`);
  console.log(`  最短轴 ${bb.round(minAxis)}，最高 Y ${bb.round(hi[1])}`);
  console.log(`  包围盒 X ${bb.round(lo[0])}~${bb.round(hi[0])}  Z ${bb.round(lo[2])}~${bb.round(hi[2])}`);
}

main();
