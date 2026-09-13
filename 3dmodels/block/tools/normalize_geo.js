'use strict';
/*
 * 从 Blockbench 导出 .geo.json 之后的收尾处理。
 *
 * Blockbench 的 "Export Bedrock Geometry" 会写新版 Bedrock 默认的 format_version
 * （当前是 1.21.110），而 GeckoLib 4 的 FormatVersion 只认到 1.21.20，
 * 直接放进资源目录会让模型解析失败、游戏里整个方块不渲染。
 * 同时它输出的排版（制表符、每个数组元素单独一行）也和项目约定不一致。
 *
 * 本脚本把这两件事一次做完，幂等：
 *   - format_version 改成 1.12.0
 *   - 重新排版成两空格缩进 + CRLF + 末尾换行
 *
 * 用法：
 *   node 3dmodels/blocks/tools/normalize_geo.js            # 处理 geo/block 下全部
 *   node 3dmodels/blocks/tools/normalize_geo.js --dry-run  # 只看会改什么
 */

const fs = require('fs');
const path = require('path');
const bb = require('./lib/bbmodel.js');

const GEO_DIR = path.resolve(__dirname, '../../../src/main/resources/assets/ssc-primalstinct/geo/block');
const TARGET_VERSION = '1.12.0';
const DRY = process.argv.includes('--dry-run');

function main() {
  let changed = 0;
  let files = 0;
  for (const name of fs.readdirSync(GEO_DIR).sort()) {
    if (!name.endsWith('.geo.json')) continue;
    files++;
    const full = path.join(GEO_DIR, name);
    const raw = fs.readFileSync(full, 'utf8');
    const doc = JSON.parse(raw);
    const before = doc.format_version;
    doc.format_version = TARGET_VERSION;
    const next = bb.geoJson(doc);
    if (next === raw) {
      console.log('  未变    ' + name);
      continue;
    }
    changed++;
    const notes = [];
    if (before !== TARGET_VERSION) notes.push(`format_version ${before} → ${TARGET_VERSION}`);
    if (JSON.parse(raw).format_version === TARGET_VERSION) notes.push('仅排版');
    console.log((DRY ? '  会改    ' : '  已修正  ') + name + '  (' + notes.join('，') + ')');
    if (!DRY) fs.writeFileSync(full, next);
  }
  console.log(`${files} 个文件，${changed} 个需要处理${DRY ? '（--dry-run，未写入）' : ''}`);
}

main();
