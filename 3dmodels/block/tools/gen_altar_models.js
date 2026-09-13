'use strict';
/*
 * 由 primal_altar.bbmodel 派生出祭坛的另外两个状态：
 *   - primal_altar_active  : 几何体与 UV 与普通态完全一致，只换贴图（Java 侧要求两态几何相同）
 *   - spent_primal_altar   : 破损几何体，凹槽缺失、塔身被啃掉一块、核心开裂
 * 同时把普通态的 .geo.json 一并按 .bbmodel 重算，避免两者再次脱节。
 *
 * UV 规则：
 *   1. 若某个面仍落在原立方体对应的平面上，取该面在原模型 UV 矩形中的等比子矩形
 *      （因为原 UV 就是按 1:1 铺的，子矩形必然也是 1:1，不会拉伸）；
 *   2. 若这个面是新切出来的断面，则从 32x32 图集的断面区 (16,22)-(32,32) 里分配一块。
 *      断面区是各向同性的生石噪声，允许环绕复用，视觉上不可分辨。
 *
 * 用法：node 3dmodels/blocks/tools/gen_altar_models.js
 */

const fs = require('fs');
const path = require('path');
const bb = require('./lib/bbmodel.js');

const ROOT = path.resolve(__dirname, '../../..');
const SRC_DIR = path.join(ROOT, '3dmodels/blocks');
const GEO_DIR = path.join(ROOT, 'src/main/resources/assets/ssc-primalstinct/geo/block');
const TEX_DIR = path.join(ROOT, 'src/main/resources/assets/ssc-primalstinct/textures/block/gecko');

const FACES = bb.FACES;
const round = bb.round;

// 断面区：图集中未被任何 UV 引用的空白块（x 16..31, y 22..31）。
const BROKEN = { x: 16, y: 22, w: 16, h: 10 };

/** 每个面在图集里对应的两条模型轴：[u 轴, v 轴]。 */
const FACE_AXES = { north: [0, 1], south: [0, 1], east: [2, 1], west: [2, 1], up: [0, 2], down: [0, 2] };
const PLANE_AXIS = { north: [2, 0], south: [2, 1], west: [0, 0], east: [0, 1], down: [1, 0], up: [1, 1] };

// ------------------------------------------------------------ 断面区分配

function createBrokenAllocator() {
  let sx = 0;
  let sy = 0;
  let shelf = 0;
  return function alloc(w, h) {
    if (sx + w > BROKEN.w) {
      sx = 0;
      sy += shelf;
      shelf = 0;
    }
    if (sy + h > BROKEN.h) {
      // 坑位用尽就绕回开头。断面区是噪声，重叠取样看不出来。
      sx = 0;
      sy = 0;
      shelf = 0;
    }
    const rect = { u0: BROKEN.x + sx, v0: BROKEN.y + sy, u1: BROKEN.x + sx + w, v1: BROKEN.y + sy + h };
    sx += w;
    shelf = Math.max(shelf, h);
    return rect;
  };
}

// --------------------------------------------------------------- 几何工具

/** 面在模型里的尺寸（宽, 高），宽的轴对应 u，高的轴对应 v。 */
function faceSize(box, face) {
  const [au, av] = FACE_AXES[face];
  return [box.to[au] - box.from[au], box.to[av] - box.from[av]];
}

/** 判断 box 的某个面是否还贴在原立方体对应的那个平面上。 */
function isOriginalPlane(orig, box, face) {
  const [axis, side] = PLANE_AXIS[face];
  const plane = side === 0 ? box.from[axis] : box.to[axis];
  const ref = side === 0 ? orig.from[axis] : orig.to[axis];
  return Math.abs(plane - ref) < 1e-6;
}

/** 把原 UV 矩形按 box 在平面内所占的比例切成子矩形。 */
function subRect(rect, orig, box, face) {
  const [au, av] = FACE_AXES[face];
  const lerp = (axis, value, a, b) => {
    const span = orig.to[axis] - orig.from[axis];
    return a + ((value - orig.from[axis]) / span) * (b - a);
  };
  return {
    u0: lerp(au, box.from[au], rect.u0, rect.u1),
    u1: lerp(au, box.to[au], rect.u0, rect.u1),
    v0: lerp(av, box.from[av], rect.v0, rect.v1),
    v1: lerp(av, box.to[av], rect.v0, rect.v1),
  };
}

// ------------------------------------------------------------- 破损方案

/*
 * 祭坛从上到下依次是：基座 -> 三段塔身 -> 束腰 -> 外扩 -> 碗底 -> 四面碗壁 -> 碗内台阶 -> 核心。
 * 破损刻意集中在东南角，形成一整块被啃掉的缺口；碗沿少一段、东侧碗沿塌了半截；核心裂成两半。
 */
const DAMAGE = {
  Base: [
    { name: 'Base_a', from: [-4.5, 0, -4.5], to: [4.5, 2, 1.5] },
    { name: 'Base_b', from: [-4.5, 0, 1.5], to: [1.5, 2, 4.5] },
  ],
  Shaft_lower: [
    { name: 'Shaft_lower_a', from: [-4, 2, -4], to: [4, 5, 1] },
    { name: 'Shaft_lower_b', from: [-4, 2, 1], to: [1, 5, 4] },
  ],
  Shaft_mid: [
    { name: 'Shaft_mid_a', from: [-5, 5, -5], to: [5, 7.5, 0.5] },
    { name: 'Shaft_mid_b', from: [-5, 5, 0.5], to: [0.5, 7.5, 5] },
  ],
  Bowl_wall_n: [
    { name: 'Bowl_wall_n_w', from: [-8, 13.5, -8], to: [-2, 16, -6.5] },
    { name: 'Bowl_wall_n_e', from: [2, 13.5, -8], to: [8, 16, -6.5] },
  ],
  Bowl_wall_e: [
    { name: 'Bowl_wall_e_n', from: [6.5, 13.5, -6.5], to: [8, 16, -1.5] },
    { name: 'Bowl_wall_e_s', from: [6.5, 13.5, -1.5], to: [8, 15, 6.5] },
  ],
  Bowl_step_1: [
    { name: 'Bowl_step_1_a', from: [-5.5, 13.5, -5.5], to: [5.5, 14, -1] },
    { name: 'Bowl_step_1_b', from: [-5.5, 13.5, -1], to: [-1, 14, 5.5] },
  ],
  Core: [
    { name: 'Core_a', from: [-3, 14, -3], to: [3, 14.5, -0.5] },
    { name: 'Core_b', from: [-3, 14, 0.5], to: [3, 14.5, 3] },
  ],
};

// --------------------------------------------------------------- 载入源文件

function loadSource() {
  const model = JSON.parse(fs.readFileSync(path.join(SRC_DIR, 'primal_altar.bbmodel'), 'utf8'));
  const elements = new Map(model.elements.map((e) => [e.name, e]));
  for (const bone of model.groups) {
    const entry = model.outliner.find((o) => o.uuid === bone.uuid);
    bone.elementNames = entry.children.map((uuid) => model.elements.find((e) => e.uuid === uuid).name);
  }
  return { model, elements, template: { model, textureDir: TEX_DIR } };
}

/** bbmodel 的面记录是 {uv:[x1,y1,x2,y2], texture:0}。 */
const faceRect = (face) => {
  const [u0, v0, u1, v1] = face.uv;
  return { u0, v0, u1, v1 };
};

// --------------------------------------------------------- 生成破损几何体

function buildSpent(source) {
  const alloc = createBrokenAllocator();
  const bones = [];
  for (const bone of source.model.groups) {
    const cubes = [];
    for (const name of bone.elementNames) {
      const orig = source.elements.get(name);
      const plan = DAMAGE[name] || [{ name, from: orig.from, to: orig.to }];
      for (const box of plan) {
        const uv = {};
        for (const face of FACES) {
          if (isOriginalPlane(orig, box, face)) {
            uv[face] = subRect(faceRect(orig.faces[face]), orig, box, face);
          } else {
            const [w, h] = faceSize(box, face);
            uv[face] = alloc(round(w), round(h));
          }
        }
        cubes.push({ name: box.name, from: box.from, to: box.to, color: orig.color, uv });
      }
    }
    bones.push({ name: bone.name, pivot: bone.origin, cubes });
  }
  return bones;
}

// ------------------------------------------------------------------- 主流程

// 生成脚本只应改写它自己生成的东西。祭坛的另两个状态已经被手工编辑过，
// 所以内容一旦对不上就停下来，要覆盖得显式加 --force。
const FORCE = process.argv.includes('--force');

function writeGuarded(full, text, label) {
  const before = fs.existsSync(full) ? fs.readFileSync(full, 'utf8') : null;
  if (before === text) {
    console.log('  未变    ' + label);
    return;
  }
  if (before !== null && !FORCE) {
    console.log('  跳过    ' + label + '（磁盘内容与本脚本生成的不同，加 --force 才覆盖）');
    return;
  }
  fs.writeFileSync(full, text);
  console.log((before === null ? '  新增    ' : '  已更新  ') + label);
}

function main() {
  const source = loadSource();

  // 普通态几何体直接由 .bbmodel 推出，保证两者不会脱节。
  const altarBones = source.model.groups.map((bone) => ({
    name: bone.name,
    pivot: bone.origin,
    cubes: bone.elementNames.map((name) => {
      const el = source.elements.get(name);
      const uv = {};
      for (const face of FACES) uv[face] = faceRect(el.faces[face]);
      return { name, from: el.from, to: el.to, color: el.color, uv };
    }),
  }));

  const write = (file, text) => writeGuarded(path.join(GEO_DIR, file), text, 'geo/block/' + file);

  write('primal_altar.geo.json', bb.geoJson(bb.toGeo('primal_altar', altarBones, 32)));
  // 激活态与普通态共用同一份几何体数据，序列化结果必然逐字节一致。
  write('primal_altar_active.geo.json', bb.geoJson(bb.toGeo('primal_altar_active', altarBones, 32)));
  writeGuarded(
    path.join(SRC_DIR, 'primal_altar_active.bbmodel'),
    JSON.stringify(bb.toBbmodel('primal_altar_active', 'primal_altar_active', altarBones, source.template)),
    'primal_altar_active.bbmodel',
  );

  // 失效态：破损几何体。
  const spentBones = buildSpent(source);
  write('spent_primal_altar.geo.json', bb.geoJson(bb.toGeo('spent_primal_altar', spentBones, 32)));
  writeGuarded(
    path.join(SRC_DIR, 'spent_primal_altar.bbmodel'),
    JSON.stringify(bb.toBbmodel('spent_primal_altar', 'spent_primal_altar', spentBones, source.template)),
    'spent_primal_altar.bbmodel',
  );

  const cubes = spentBones.reduce((n, b) => n + b.cubes.length, 0);
  console.log(`失效态：${spentBones.length} 根骨骼 / ${cubes} 个立方体`);

  // 自检：最短轴、包围盒、UV 比例。
  let minAxis = Infinity;
  let maxY = -Infinity;
  let mismatch = 0;
  for (const bone of spentBones) {
    for (const cube of bone.cubes) {
      const size = [cube.to[0] - cube.from[0], cube.to[1] - cube.from[1], cube.to[2] - cube.from[2]];
      minAxis = Math.min(minAxis, ...size);
      maxY = Math.max(maxY, cube.to[1]);
      if (Math.min(...size) < 0.5) console.log('  ! 轴小于 0.5:', cube.name, size);
      for (const face of FACES) {
        const [w, h] = faceSize(cube, face);
        const r = cube.uv[face];
        if (Math.abs(r.u1 - r.u0 - w) > 1e-6 || Math.abs(r.v1 - r.v0 - h) > 1e-6) {
          mismatch++;
          console.log('  ! UV 比例不符:', cube.name, face, [round(r.u1 - r.u0), round(r.v1 - r.v0)], '应为', [w, h]);
        }
        if (r.u0 < 0 || r.v0 < 0 || r.u1 > 32 || r.v1 > 32) {
          console.log('  ! UV 越界:', cube.name, face, r);
        }
      }
    }
  }
  console.log(`自检：最短轴 ${minAxis}，最高 Y ${maxY}，UV 比例不符 ${mismatch} 处`);
}

main();
