'use strict';
/*
 * 生成原初传送门框架（primal_portal_frame）的几何体与贴图。
 *
 * 形体参考 primal_pedestal：下面是一样的「苔石脚 + 石身 + 收边」三段，
 * 上半部分不是基座那种围成接收口的空心边框，而是四根方形石柱，
 * 柱间留 2 单位缝隙，俯视是四宫格。
 *
 * 配色：从底部苔石一路过渡到顶部类黑曜石的深暗色。渐变靠 UV 实现——
 * 侧面的 v 直接取模型 Y，所以贴图纵轴就是模型高度，柱子自然接着石身的色走。
 *
 * 用法：node 3dmodels/blocks/tools/gen_portal_frame.js
 */

const fs = require('fs');
const path = require('path');
const bb = require('./lib/bbmodel.js');
const { encodePng, createCanvas, hex, mix, mulberry32 } = require('./lib/png.js');

const ROOT = path.resolve(__dirname, '../../..');
const SRC_DIR = path.join(ROOT, '3dmodels/blocks');
const GEO_DIR = path.join(ROOT, 'src/main/resources/assets/ssc-primalstinct/geo/block');
const TEX_DIR = path.join(ROOT, 'src/main/resources/assets/ssc-primalstinct/textures/block/gecko');

const NAME = 'primal_portal_frame';
const TEX = 32;

// ------------------------------------------------------------------ 尺寸

const FOOT_TOP = 1.5;    // 苔石脚
const BODY_TOP = 6.5;    // 石身
const COLLAR_TOP = 8;    // 收边，柱脚从这里起
const GAP = 2;           // 柱间缝隙，四根柱子各 7 宽

const CUBES = [
  { bone: '01_Stone_base', name: 'Mossy_foot', from: [-8, 0, -8], to: [8, FOOT_TOP, 8] },
  { bone: '01_Stone_base', name: 'Body', from: [-7.5, FOOT_TOP, -7.5], to: [7.5, BODY_TOP, 7.5] },
  { bone: '01_Stone_base', name: 'Collar', from: [-8, BODY_TOP, -8], to: [8, COLLAR_TOP, 8] },
];
for (const [sx, sz] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) {
  const x0 = sx < 0 ? -8 : GAP / 2;
  const z0 = sz < 0 ? -8 : GAP / 2;
  CUBES.push({
    bone: '02_Four_pillars',
    name: 'Pillar_' + (sz < 0 ? 'n' : 's') + (sx < 0 ? 'w' : 'e'),
    from: [x0, COLLAR_TOP, z0],
    to: [x0 + 8 - GAP / 2, 16, z0 + 8 - GAP / 2],
  });
}

// ------------------------------------------------------------------ 图集

/*
 * 32x32 图集：
 *   side      (0,0)   16x16  侧面，v 就是模型 Y，1 单位 = 1 像素
 *   collarTop (16,0)  16x16  收边顶面
 *   bottom    (0,16)  16x16  底面（苔石）
 *   pillarTop (16,16) 8x8    柱顶（黑曜石），四根共用
 */
const ATLAS = {
  side: { x: 0, y: 0, w: 16, h: 16 },
  collarTop: { x: 16, y: 0, w: 16, h: 16 },
  bottom: { x: 0, y: 16, w: 16, h: 16 },
  pillarTop: { x: 16, y: 16, w: 8, h: 8 },
};

const rectOf = (r) => ({ u0: r.x, v0: r.y, u1: r.x + r.w, v1: r.y + r.h });
const subRect = (r, w, h) => ({ u0: r.x, v0: r.y, u1: r.x + w, v1: r.y + h });

/**
 * 侧面 UV 直接取模型坐标：横向 +8，纵向取 Y。
 * 所以上半的柱子和下半的石身共用同一条纵向渐变，接缝处颜色自然连上。
 */
function uvFor(cube, face) {
  if (face === 'north' || face === 'south' || face === 'east' || face === 'west') {
    const axis = face === 'north' || face === 'south' ? 0 : 2;
    return { u0: cube.from[axis] + 8, v0: cube.from[1], u1: cube.to[axis] + 8, v1: cube.to[1] };
  }
  // 顶面 / 底面：按立方体挑一块专用区域，再取和面一样大的子矩形。
  // 石身的上下、柱子的底面其实都被盖住，取错也无所谓，但仍然保持 1:1。
  const w = cube.to[0] - cube.from[0];
  const d = cube.to[2] - cube.from[2];
  let region = ATLAS.collarTop;
  if (face === 'up' && cube.name.startsWith('Pillar')) region = ATLAS.pillarTop;
  else if (cube.name === 'Mossy_foot') region = ATLAS.bottom;
  return subRect(region, Math.min(w, region.w), Math.min(d, region.h));
}

// ---------------------------------------------------------------- 贴图

// 纵向渐变：行号 = 模型 Y。0 是苔石脚，15 是柱顶。
// 明度必须一路往下走，中间段比两头亮的话会读成夹了一条带子而不是渐变。
const GRAD = [
  '#6E7A52', '#6B7654', '#687258', '#666E5C',
  '#636A62', '#5F666A', '#5A6170', '#545A74',
  '#4E5376', '#474B72', '#40436C', '#393B64',
  '#2E2F52', '#232440', '#181830', '#0F0F1E',
];

const MOSS_HI = hex('#7A9448');
const MOSS_DK = hex('#41552A');
const STONE_HI = hex('#7C8478');
const STONE_DK = hex('#4A4E48');
const OBS_HI = hex('#4A4270');
const OBS_DK = hex('#100C1E');

/** 每一行的"材质感"：低=苔石，中=砌石，高=黑曜石。噪声幅度跟着它走，
 *  否则上部的裂纹会把渐变盖掉。 */
function materialWeight(y) {
  if (y <= 2) return { moss: .55, stone: .18, obs: 0 };
  if (y <= 5) return { moss: .55 * (6 - y) / 4, stone: .22, obs: 0 };
  if (y <= 8) return { moss: 0, stone: .26, obs: 0 };
  return { moss: 0, stone: .08, obs: .30 };
}

function paintSide(ctx, rnd) {
  const R = ATLAS.side;
  for (let y = 0; y < R.h; y++) {
    const base = hex(GRAD[y]);
    const w = materialWeight(y);
    for (let x = 0; x < R.w; x++) {
      let c = base;
      const n = rnd();
      if (n < w.moss * .38) c = mix(base, MOSS_HI, .50);
      else if (n < w.moss * .78) c = mix(base, MOSS_DK, .42);
      else if (n < w.moss + w.stone * .5) c = mix(base, STONE_HI, .30);
      else if (n < w.moss + w.stone) c = mix(base, STONE_DK, .30);
      else if (w.obs > 0 && n < w.moss + w.stone + w.obs * .35) c = mix(base, OBS_HI, .50);
      else if (w.obs > 0 && n < w.moss + w.stone + w.obs) c = mix(base, OBS_DK, .30);
      ctx.set(R.x + x, R.y + y, c);
    }
  }
  // 砌层线只在石身段，且很轻——重了会把渐变切断
  for (const y of [4, 7]) {
    for (let x = 0; x < R.w; x++) {
      ctx.set(R.x + x, R.y + y, mix(ctx.get(R.x + x, R.y + y), STONE_DK, .28));
    }
  }
  // 苔藓往下淌：几条竖着的苔痕，停在灰绿段就不往下走
  for (const x of [2, 6, 11, 14]) {
    const len = 2 + Math.round(rnd() * 2);
    for (let k = 0; k < len; k++) {
      const y = 5 - k;
      if (y < 0) break;
      ctx.set(R.x + x, R.y + y, mix(ctx.get(R.x + x, R.y + y), MOSS_DK, .45));
    }
  }
  // 柱身段两道很淡的斜纹，暗示黑曜石的光泽，不做成黑缝
  for (const x0 of [3, 11]) {
    let cx = x0;
    for (let y = 9; y < 16; y++) {
      ctx.set(R.x + cx, R.y + y, mix(ctx.get(R.x + cx, R.y + y), OBS_HI, .35));
      if (rnd() < .5) cx += rnd() < .5 ? -1 : 1;
      if (cx < 0 || cx >= R.w) break;
    }
  }
}

function paintTop(ctx, rnd, region, dark) {
  // 顶上那格的色调要和它所在高度接得上：收边顶面取 GRAD[8]，柱顶取 GRAD[15]。
  const base = hex(dark ? '#12122A' : '#4E5376');
  for (let y = 0; y < region.h; y++) {
    for (let x = 0; x < region.w; x++) {
      const n = rnd();
      let c = base;
      if (n < .18) c = mix(base, dark ? OBS_HI : STONE_HI, .40);
      else if (n < .34) c = mix(base, dark ? OBS_DK : STONE_DK, .35);
      ctx.set(region.x + x, region.y + y, c);
    }
  }
  // 顶面几道放射状的纹
  const cx = region.x + region.w / 2;
  const cy = region.y + region.h / 2;
  for (let k = 0; k < (dark ? 4 : 3); k++) {
    const a = rnd() * Math.PI * 2;
    for (let r = 1; r < region.w / 2; r++) {
      const px = Math.round(cx + Math.cos(a) * r);
      const py = Math.round(cy + Math.sin(a) * r);
      if (px < region.x || py < region.y || px >= region.x + region.w || py >= region.y + region.h) break;
      ctx.set(px, py, dark ? mix(ctx.get(px, py), OBS_HI, .45) : mix(ctx.get(px, py), STONE_DK, .40));
    }
  }
}

function buildTexture() {
  const ctx = createCanvas(TEX, TEX, hex('#2A2A32'));
  const rnd = mulberry32(19700101);
  paintSide(ctx, rnd);
  paintTop(ctx, rnd, ATLAS.collarTop, false);
  paintTop(ctx, rnd, ATLAS.bottom, false);
  paintTop(ctx, rnd, ATLAS.pillarTop, true);
  // 底面盖一层苔
  for (let y = 0; y < ATLAS.bottom.h; y++) {
    for (let x = 0; x < ATLAS.bottom.w; x++) {
      const n = rnd();
      if (n < .34) ctx.set(ATLAS.bottom.x + x, ATLAS.bottom.y + y, mix(ctx.get(ATLAS.bottom.x + x, ATLAS.bottom.y + y), MOSS_DK, .55));
      else if (n < .46) ctx.set(ATLAS.bottom.x + x, ATLAS.bottom.y + y, mix(ctx.get(ATLAS.bottom.x + x, ATLAS.bottom.y + y), MOSS_HI, .40));
    }
  }
  return ctx;
}

// ------------------------------------------------------------------ 主流程

function main() {
  const bones = [];
  for (const cube of CUBES) {
    let bone = bones.find((b) => b.name === cube.bone);
    if (!bone) {
      bone = { name: cube.bone, pivot: cube.bone === '01_Stone_base' ? [0, 0, 0] : [0, COLLAR_TOP, 0], cubes: [] };
      bones.push(bone);
    }
    const uv = {};
    for (const face of bb.FACES) uv[face] = uvFor(cube, face);
    bone.cubes.push({ name: cube.name, from: cube.from, to: cube.to, color: 0, uv });
  }

  const template = { model: JSON.parse(fs.readFileSync(path.join(SRC_DIR, 'primal_altar.bbmodel'), 'utf8')), textureDir: TEX_DIR };
  fs.writeFileSync(path.join(TEX_DIR, NAME + '.png'), encodePng(TEX, TEX, buildTexture().data));
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
          : face === 'north' || face === 'south' ? [size[0], size[1]] : [size[2], size[1]];
        if (Math.abs(r.u1 - r.u0 - want[0]) > 1e-6 || Math.abs(r.v1 - r.v0 - want[1]) > 1e-6) {
          console.log('  ! UV 比例不符:', cube.name, face, [bb.round(r.u1 - r.u0), bb.round(r.v1 - r.v0)], '应为', want);
        }
        if (r.u0 < 0 || r.v0 < 0 || r.u1 > TEX || r.v1 > TEX) console.log('  ! UV 越界:', cube.name, face, r);
      }
      for (let k = 0; k < 8; k++) {
        for (let a = 0; a < 3; a++) {
          const v = cube.from[a] + ((k >> a) & 1) * size[a];
          lo[a] = Math.min(lo[a], v);
          hi[a] = Math.max(hi[a], v);
        }
      }
    }
  }
  console.log(`传送门框架：${bones.length} 根骨骼 / ${cubes} 个立方体`);
  console.log(`  最短轴 ${bb.round(minAxis)}`);
  console.log(`  包围盒 X ${bb.round(lo[0])}~${bb.round(hi[0])}  Y ${bb.round(lo[1])}~${bb.round(hi[1])}  Z ${bb.round(lo[2])}~${bb.round(hi[2])}`);
}

main();
