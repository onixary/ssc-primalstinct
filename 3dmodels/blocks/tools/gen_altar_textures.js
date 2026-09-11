'use strict';
/*
 * 由 primal_altar.png（普通态，32x32 图集）派生祭坛的另外两个状态贴图：
 *   - primal_altar_active.png            激活态：苔藓充能、金色纹路发光、凹槽充满光池
 *   - primal_altar_active_emission.png   激活态发光遮罩（供 EndgameEmissionLayer 使用）
 *   - spent_primal_altar.png             失效态：褪色、焦黑、裂纹
 * 普通态贴图本身为唯一源文件，不会被本脚本改写。
 *
 * 用法：node 3dmodels/blocks/tools/gen_altar_textures.js
 */

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const ASSETS = path.resolve(__dirname, '../../../src/main/resources/assets/ssc-primalstinct');
const GECKO = path.join(ASSETS, 'textures/block/gecko');

// ---------------------------------------------------------------- PNG 读写

function decodePng(file) {
  const buf = fs.readFileSync(file);
  if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error('Not a PNG: ' + file);
  let p = 8;
  let width = 0;
  let height = 0;
  let colorType = 0;
  let bitDepth = 0;
  const idat = [];
  while (p < buf.length) {
    const len = buf.readUInt32BE(p);
    const type = buf.toString('ascii', p + 4, p + 8);
    if (type === 'IHDR') {
      width = buf.readUInt32BE(p + 8);
      height = buf.readUInt32BE(p + 12);
      bitDepth = buf[p + 16];
      colorType = buf[p + 17];
      if (buf[p + 20] !== 0) throw new Error('Interlaced PNG unsupported');
    } else if (type === 'IDAT') {
      idat.push(buf.subarray(p + 8, p + 8 + len));
    }
    p += 12 + len;
  }
  if (bitDepth !== 8) throw new Error('Only 8-bit PNG supported');
  const channels = { 0: 1, 2: 3, 4: 2, 6: 4 }[colorType];
  if (!channels) throw new Error('Unsupported color type ' + colorType);
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = width * channels;
  const px = Buffer.alloc(height * stride);
  let o = 0;
  for (let y = 0; y < height; y++) {
    const filter = raw[o++];
    const line = raw.subarray(o, o + stride);
    o += stride;
    const row = y * stride;
    for (let x = 0; x < stride; x++) {
      const a = x >= channels ? px[row + x - channels] : 0;
      const b = y > 0 ? px[row - stride + x] : 0;
      const c = x >= channels && y > 0 ? px[row - stride + x - channels] : 0;
      let v = line[x];
      if (filter === 1) v += a;
      else if (filter === 2) v += b;
      else if (filter === 3) v += (a + b) >> 1;
      else if (filter === 4) {
        const pa = Math.abs(b - c);
        const pb = Math.abs(a - c);
        const pc = Math.abs(a + b - 2 * c);
        v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
      }
      px[row + x] = v & 255;
    }
  }
  // 统一成 RGBA
  const rgba = Buffer.alloc(width * height * 4);
  for (let i = 0; i < width * height; i++) {
    const s = i * channels;
    const d = i * 4;
    if (channels === 1) {
      rgba[d] = rgba[d + 1] = rgba[d + 2] = px[s];
      rgba[d + 3] = 255;
    } else if (channels === 2) {
      rgba[d] = rgba[d + 1] = rgba[d + 2] = px[s];
      rgba[d + 3] = px[s + 1];
    } else if (channels === 3) {
      rgba[d] = px[s];
      rgba[d + 1] = px[s + 1];
      rgba[d + 2] = px[s + 2];
      rgba[d + 3] = 255;
    } else {
      px.copy(rgba, d, s, s + 4);
    }
  }
  return { width, height, data: rgba };
}

function crc32(buf) {
  let c = ~0;
  for (let i = 0; i < buf.length; i++) {
    c ^= buf[i];
    for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
  }
  return ~c >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}

function encodePng(width, height, rgba) {
  const stride = width * 4;
  const raw = Buffer.alloc(height * (stride + 1));
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0;
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// ------------------------------------------------------------ 图集区域划分

/*
 * 普通态祭坛贴图的图集布局（u 轴对应模型 X 或 Z + 8，v 轴对应模型 Y）：
 *   x 0..15  y 0..12   塔身侧面（v = Y）
 *   x 0..15  y 13      碗底侧面（Y 12.5-13.5）
 *   x 0..15  y 14..15  碗壁外侧带
 *   x 0..15  y 16..31  凹槽底面（u = X+8, v = Z+8）
 *   x 16..31 y 0..3    凹槽内壁 / 南北壁顶面
 *   x 16..17 y 4..16   东西壁顶面
 *   x 18..28 y 4..14   台阶顶面
 *   x 18..23 y 15..20  核心顶面
 *   x 24..29 y 15      核心侧面
 *   x 18..28 y 21      台阶侧面
 *   其余为未使用区，用石块色填满以避免 mipmap 采样到透明边。
 */
function region(x, y) {
  if (x < 16) {
    if (y >= 16) return 'CAVITY_FLOOR';
    if (y <= 12) return 'TOWER';
    if (y === 13) return 'BOWL_FLOOR_SIDE';
    return 'BOWL_WALL';
  }
  // 失效态的断面区：破损几何体新切出来的面从这里取样。
  if (y >= 22) return 'BROKEN';
  if (y <= 3) return 'CAVITY_WALL_TOP';
  if (x < 18 && y <= 16) return 'WALL_TOP_EW';
  if (y <= 14 && x >= 18 && x <= 28) return 'STEP_TOP';
  if (y >= 15 && y <= 20 && x >= 18 && x <= 23) return 'CORE_TOP';
  if (y === 15 && x >= 24 && x <= 29) return 'CORE_SIDE';
  if (y === 21 && x >= 18 && x <= 28) return 'STEP_SIDE';
  return 'SPARE';
}

// --------------------------------------------------------------- 工具函数

function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), 1 | t);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];

function mix(a, b, t) {
  return [
    Math.round(a[0] + (b[0] - a[0]) * t),
    Math.round(a[1] + (b[1] - a[1]) * t),
    Math.round(a[2] + (b[2] - a[2]) * t),
  ];
}

/** 逐像素的调色板重映射，未命中的颜色按最近邻处理。 */
function remapPalette(src, table) {
  const keys = Object.keys(table).map((k) => ({ rgb: hex(k), to: hex(table[k]) }));
  const out = Buffer.alloc(src.length);
  for (let i = 0; i < src.length; i += 4) {
    const r = src[i];
    const g = src[i + 1];
    const b = src[i + 2];
    let best = keys[0];
    let bestD = Infinity;
    for (const k of keys) {
      const d = (k.rgb[0] - r) ** 2 + (k.rgb[1] - g) ** 2 + (k.rgb[2] - b) ** 2;
      if (d < bestD) {
        bestD = d;
        best = k;
      }
    }
    out[i] = best.to[0];
    out[i + 1] = best.to[1];
    out[i + 2] = best.to[2];
    out[i + 3] = src[i + 3];
  }
  return out;
}

// --------------------------------------------------------------- 激活态

/*
 * 激活态是"同一块石头苏醒过来"，不是换材质：石材只做很轻的暖化，
 * 苔藓略微提亮，真正抢眼的是金饰与凹槽里的光。配色一旦拉满就会变成卡通绿。
 */
const ACTIVE_PALETTE = {
  '#8A9A7A': '#97A585',
  '#9FAE8E': '#ADBA99',
  '#6E7D60': '#7B896B',
  '#5A6A50': '#66755A',
  '#6B8E3C': '#7CA344',
  '#86A84A': '#96BA58',
  '#55702E': '#638336',
  '#3F5722': '#4A6629',
  '#E8A820': '#FFD24A',
  '#FFD24A': '#FFF2A8',
  '#B87E10': '#E8A820',
  '#8A5C08': '#B87E10',
  '#2E3A2C': '#3C4A36',
  '#4A5A4A': '#5E6E58',
};

const GOLD = hex('#E8A820');
const GOLD_HI = hex('#FFD24A');
const GOLD_PALE = hex('#FFF2A8');
const GOLD_HOT = hex('#FFF6C8');
const GOLD_DK = hex('#8A5C08');

function buildActive(base) {
  const { width: W, height: H } = base;
  const out = remapPalette(base.data, ACTIVE_PALETTE);
  const rnd = mulberry32(20240911);
  // 所有写出的像素一律不透明：图集里未被 UV 引用的区域也要填实，
  // 否则 mipmap 会把边缘采样进透明像素，产生暗边。
  const set = (x, y, c) => {
    const i = (y * W + x) * 4;
    out[i] = c[0];
    out[i + 1] = c[1];
    out[i + 2] = c[2];
    out[i + 3] = 255;
  };
  const get = (x, y) => {
    const i = (y * W + x) * 4;
    return [out[i], out[i + 1], out[i + 2]];
  };

  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      switch (region(x, y)) {
        case 'TOWER': {
          // 只有塔身中段那道腰线变成一环能量光带，其余保持石材。
          if (y === 10) set(x, y, x % 3 === 1 ? GOLD_HI : GOLD);
          break;
        }
        case 'BOWL_FLOOR_SIDE':
          // 碗底侧壁微微透出暖色，像被上方的光烤热。
          set(x, y, mix(get(x, y), GOLD, 0.3));
          break;
        case 'BOWL_WALL': {
          if (y === 14) {
            // 原本的金色虚线整圈点亮，靠三档金色错开来避免糊成一条实心带。
            set(x, y, x % 4 === 3 ? GOLD_PALE : x % 2 === 0 ? GOLD_HI : GOLD);
          } else {
            // 光环下方压一道暖色阴影。
            set(x, y, mix(get(x, y), GOLD_DK, 0.35));
          }
          break;
        }
        case 'CAVITY_WALL_TOP':
          if (y === 0) set(x, y, x % 4 === 2 ? GOLD_HI : GOLD_PALE);
          else if (y === 1) set(x, y, mix(get(x, y), GOLD, 0.25));
          break;
        case 'STEP_SIDE':
          set(x, y, x % 6 === 0 ? GOLD_HI : GOLD);
          break;
        case 'CORE_SIDE':
          set(x, y, x % 3 === 1 ? GOLD_PALE : GOLD_HI);
          break;
        case 'CORE_TOP': {
          const lx = x - 18;
          const ly = y - 15;
          if (lx === 0 || lx === 5 || ly === 0 || ly === 5) {
            set(x, y, GOLD_HI);
          } else {
            const d = Math.hypot(lx - 2.5, ly - 2.5);
            set(x, y, d < 1.4 ? GOLD_HOT : d < 2.2 ? GOLD_PALE : GOLD_HI);
          }
          break;
        }
        case 'STEP_TOP': {
          const lx = x - 18;
          const ly = y - 4;
          if (lx === 0 || lx === 10 || ly === 0 || ly === 10) set(x, y, GOLD_HI);
          else if (lx % 5 === 0 || ly % 5 === 0) set(x, y, mix(get(x, y), GOLD, 0.45));
          break;
        }
        case 'CAVITY_FLOOR': {
          // 凹槽底部化作一池流动的光，中心最亮。
          const lx = x;
          const ly = y - 16;
          if (lx === 0 || lx === 15 || ly === 0 || ly === 15) {
            set(x, y, hex('#4A3210'));
            break;
          }
          const d = Math.hypot(lx - 7.5, ly - 7.5);
          let c;
          if (d < 2.2) c = GOLD_HOT;
          else if (d < 3.6) c = hex('#FFE070');
          else if (d < 4.8) c = GOLD_HI;
          else if (d < 6.2) c = GOLD;
          else if (d < 7) c = hex('#C08A14');
          else c = GOLD_DK;
          // 轻微抖动，避免同心圆显得过于规整。
          if (rnd() < 0.12) c = mix(c, d < 4 ? GOLD : GOLD_HI, 0.5);
          set(x, y, c);
          break;
        }
        case 'SPARE':
        case 'BROKEN':
          // 激活态几何体完好，用不到断面区，填实以免 mipmap 采到透明像素。
          set(x, y, hex('#7E8C6D'));
          break;
        default:
          // 东西壁顶面在普通态就是整片素色，沿用重映射后的结果即可。
          break;
      }
    }
  }
  return out;
}

function buildActiveEmission(active, W, H) {
  const mask = Buffer.alloc(W * H * 4);
  const EMISSIVE = new Set(['CAVITY_FLOOR', 'CORE_TOP', 'CORE_SIDE', 'CAVITY_WALL_TOP', 'STEP_SIDE']);
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      const i = (y * W + x) * 4;
      const r = region(x, y);
      let on = EMISSIVE.has(r);
      if (r === 'TOWER') on = y === 10;
      else if (r === 'BOWL_WALL') on = y === 14;
      else if (r === 'CAVITY_FLOOR') {
        // 外圈是碗壁投下的阴影，不发光。
        const lx = x;
        const ly = y - 16;
        on = !(lx === 0 || lx === 15 || ly === 0 || ly === 15);
      } else if (r === 'CAVITY_WALL_TOP') {
        on = y === 0;
      }
      if (!on) continue;
      mask[i] = active[i];
      mask[i + 1] = active[i + 1];
      mask[i + 2] = active[i + 2];
      mask[i + 3] = 255;
    }
  }
  return mask;
}

// --------------------------------------------------------------- 失效态

/*
 * 失效态要让人看出"还是同一块石头，只是枯了"，所以只抽掉色相与饱和，不大幅压暗明度，
 * 否则一进游戏会像是换了一种材质。
 */
const SPENT_PALETTE = {
  '#8A9A7A': '#7C8074',
  '#9FAE8E': '#8F9384',
  '#6E7D60': '#666A60',
  '#5A6A50': '#545850',
  '#6B8E3C': '#6E6C4A',
  '#86A84A': '#84825C',
  '#55702E': '#5A5840',
  '#3F5722': '#4A4834',
  '#E8A820': '#6E5E42',
  '#FFD24A': '#847250',
  '#B87E10': '#584A34',
  '#8A5C08': '#403628',
  '#2E3A2C': '#303430',
  '#4A5A4A': '#464A46',
};

const CRACK = hex('#232520');
const CRACK_HI = hex('#5E6258');
const ASH = hex('#33302A');

/** 断面区的底色：刚崩开的生石，比风化过的外壁浅、也更冷。 */
const BROKEN_STONE = hex('#84887A');

/**
 * 在贴图上画一条锯齿裂纹。宽度 1px，两端渐隐，一侧带受光高光，
 * 否则整条裂纹首尾等宽会显得像画上去的竖条。
 */
function drawCrack(out, W, H, x0, y0, x1, y1, rnd, jitter) {
  const steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 2;
  let off = 0;
  for (let s = 0; s <= steps; s++) {
    const t = s / steps;
    // 两端 20% 逐段丢弃，让裂纹从无到有再消失。
    const fade = Math.min(t, 1 - t) / 0.2;
    if (fade < 1 && rnd() > fade) continue;
    off += (rnd() - 0.5) * jitter;
    off = Math.max(-jitter * 2, Math.min(jitter * 2, off));
    const x = Math.round(x0 + (x1 - x0) * t + (y1 - y0 === 0 ? 0 : off));
    const y = Math.round(y0 + (y1 - y0) * t + (x1 - x0 === 0 ? 0 : off));
    if (x < 0 || x >= W || y < 0 || y >= H) continue;
    const i = (y * W + x) * 4;
    out[i] = CRACK[0];
    out[i + 1] = CRACK[1];
    out[i + 2] = CRACK[2];
    out[i + 3] = 255;
    // 裂纹一侧的受光高光，让裂口有深度。
    const nx = y1 === y0 ? 0 : 1;
    const ny = x1 === x0 ? 0 : 1;
    const hx = x + (nx ? (off > 0 ? 1 : -1) : 0);
    const hy = y + (ny ? (off > 0 ? 1 : -1) : 0);
    if (hx >= 0 && hx < W && hy >= 0 && hy < H && rnd() < 0.35) {
      const j = (hy * W + hx) * 4;
      out[j] = CRACK_HI[0];
      out[j + 1] = CRACK_HI[1];
      out[j + 2] = CRACK_HI[2];
      out[j + 3] = 255;
    }
  }
}

function buildSpent(base) {
  const { width: W, height: H } = base;
  const out = remapPalette(base.data, SPENT_PALETTE);
  const rnd = mulberry32(19870427);
  const set = (x, y, c) => {
    const i = (y * W + x) * 4;
    out[i] = c[0];
    out[i + 1] = c[1];
    out[i + 2] = c[2];
    out[i + 3] = 255;
  };

  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      switch (region(x, y)) {
        case 'TOWER':
        case 'WALL_TOP_EW':
          // 塔身与东西壁顶面只做褪色处理，纹理细节保留。
          break;
        case 'BOWL_WALL':
          // 曾经的金色腰带只剩斑驳的锈迹，还缺了几块。
          if (y === 14) set(x, y, x % 4 === 2 ? hex('#3E362A') : x % 4 === 0 ? ASH : hex('#4A4034'));
          break;
        case 'BOWL_FLOOR_SIDE':
          set(x, y, x % 3 === 1 ? hex('#52462F') : hex('#40382A'));
          break;
        case 'CAVITY_WALL_TOP':
          if (y === 0) set(x, y, x % 5 === 3 ? hex('#463C2C') : hex('#38322A'));
          break;
        case 'CORE_TOP': {
          // 核心烧成了灰，只剩下碎裂的边框。
          const lx = x - 18;
          const ly = y - 15;
          if ((lx === 0 || lx === 5 || ly === 0 || ly === 5) && !((lx === 5 && ly === 2) || (lx === 2 && ly === 5))) {
            set(x, y, hex('#4A4034'));
          } else {
            set(x, y, rnd() < 0.3 ? hex('#332E26') : ASH);
          }
          break;
        }
        case 'CORE_SIDE':
          set(x, y, x % 3 === 0 ? hex('#3A342C') : ASH);
          break;
        case 'STEP_SIDE':
          set(x, y, x % 6 === 4 ? hex('#3A3428') : hex('#2E2A22'));
          break;
        case 'STEP_TOP':
          // 台阶上落了一层灰烬。
          if (rnd() < 0.16) set(x, y, hex('#4A4C44'));
          break;
        case 'CAVITY_FLOOR': {
          // 光池干涸：中心焦黑，边框断成几截。
          const lx = x;
          const ly = y - 16;
          const onFrame = lx === 1 || lx === 14 || ly === 1 || ly === 14;
          if (onFrame && rnd() > 0.5) set(x, y, hex('#443A28'));
          else {
            const d = Math.hypot(lx - 7.5, ly - 7.5);
            set(x, y, d < 3.5 ? hex('#211E1A') : d < 6 ? hex('#2A2620') : hex('#332C22'));
          }
          break;
        }
        case 'BROKEN': {
          // 各向同性的生石噪声：破损几何体的切面按 1:1 从这块区域取，
          // 坑位不够时环绕复用，所以这里不能有任何大尺度图案。
          const r = rnd();
          set(x, y, r < 0.16 ? hex('#949888') : r < 0.32 ? hex('#747868') : r < 0.38 ? hex('#63675C') : BROKEN_STONE);
          break;
        }
        case 'SPARE':
          set(x, y, hex('#666A60'));
          break;
        default:
          break;
      }
    }
  }

  // 裂纹：塔身自下而上贯通，碗壁与干涸的光池各有一道。
  drawCrack(out, W, H, 3, 13, 6, 0, rnd, 0.45);
  drawCrack(out, W, H, 11, 13, 9, 3, rnd, 0.4);
  drawCrack(out, W, H, 2, 15, 7, 14, rnd, 0.3);
  drawCrack(out, W, H, 4, 17, 12, 23, rnd, 0.5);

  // 核心周围与凹槽上沿的烟熏。
  for (let y = 15; y <= 21; y++) {
    for (let x = 16; x <= 31; x++) {
      if (rnd() < 0.22) {
        const i = (y * W + x) * 4;
        out[i] = Math.max(0, out[i] - 18);
        out[i + 1] = Math.max(0, out[i + 1] - 16);
        out[i + 2] = Math.max(0, out[i + 2] - 12);
      }
    }
  }
  return out;
}

// ------------------------------------------------------------------- 主流程

function main() {
  const source = path.join(GECKO, 'primal_altar.png');
  const base = decodePng(source);
  if (base.width !== 32 || base.height !== 32) throw new Error('Expected a 32x32 atlas');

  const active = buildActive(base);
  const emission = buildActiveEmission(active, base.width, base.height);
  const spent = buildSpent(base);

  // 生成的贴图如果和磁盘上的不一致就跳过：激活态与失效态已经被手工编辑过，
  // 静默覆盖会丢工作。确实要重新生成时加 --force。
  const force = process.argv.includes('--force');
  const write = (name, data) => {
    const file = path.join(GECKO, name);
    const next = encodePng(base.width, base.height, data);
    const before = fs.existsSync(file) ? fs.readFileSync(file) : null;
    if (before && before.equals(next)) {
      console.log('  未变  ', path.relative(ASSETS, file));
      return;
    }
    if (before && !force) {
      console.log('  跳过  ', path.relative(ASSETS, file), '（磁盘内容与本脚本生成的不同，加 --force 才覆盖）');
      return;
    }
    fs.writeFileSync(file, next);
    console.log((before ? '  已更新' : '  新增  '), path.relative(ASSETS, file), next.length, 'bytes');
  };
  write('primal_altar_active.png', active);
  write('primal_altar_active_emission.png', emission);
  write('spent_primal_altar.png', spent);
}

main();
