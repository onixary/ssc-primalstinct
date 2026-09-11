'use strict';
/*
 * .bbmodel / .geo.json 的共用写出器。
 *
 * 立方体记录的形状：
 *   { name, from:[x,y,z], to:[x,y,z], color, uv:{面: {u0,v0,u1,v1}}, rotation?:[x,y,z], pivot?:[x,y,z] }
 * rotation / pivot 省略时不写成旋转立方体。
 *
 * 旋转约定：记录里的角度用的是"渲染出来看到的角度"（也就是 Blockbench 编辑器里显示的角度）。
 * GeckoLib 的 BakedModelFactory 在读取 geo 时会把 X、Y 取负（Z 保持），
 * 所以写进 .geo.json 的必须是相反数；.bbmodel 则原样写。
 * 参照物是项目里已有的 primal_energy_wire：bbmodel 写 [0,-45,0]，geo 写 [0,45,0]。
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const CRLF = '\r\n';
const FACES = ['north', 'east', 'south', 'west', 'up', 'down'];

const round = (n) => Math.round(n * 1e6) / 1e6;

/** 项目约定：两空格缩进 + CRLF + 末尾换行。 */
function geoJson(obj) {
  return JSON.stringify(obj, null, 2).split('\n').join(CRLF) + CRLF;
}

/** 由名字派生稳定的 UUID，保证脚本重复运行不会改动文件内容。 */
function stableUuid(name) {
  const hex = crypto.createHash('sha1').update('ssc-primalstinct/' + name).digest('hex');
  return [hex.slice(0, 8), hex.slice(8, 12), '4' + hex.slice(13, 16), '8' + hex.slice(17, 20), hex.slice(20, 32)].join('-');
}

/** 把矩形转成 Bedrock 的 uv 写法：四个侧面用正向尺寸，上下面沿用负尺寸镜像的惯例。 */
function toBedrockUv(rect, face) {
  const w = round(rect.u1 - rect.u0);
  const h = round(rect.v1 - rect.v0);
  if (face === 'up' || face === 'down') {
    return { uv: [round(rect.u1), round(rect.v1)], uv_size: [-w, -h] };
  }
  return { uv: [round(rect.u0), round(rect.v0)], uv_size: [w, h] };
}

const isRotated = (cube) => !!cube.rotation && cube.rotation.some((v) => v !== 0);

function toGeo(identifier, bones, textureSize) {
  return {
    format_version: '1.12.0',
    'minecraft:geometry': [
      {
        description: {
          identifier: 'geometry.' + identifier,
          texture_width: textureSize,
          texture_height: textureSize,
          visible_bounds_width: 2,
          visible_bounds_height: 2.5,
          visible_bounds_offset: [0, 0.75, 0],
        },
        bones: bones.map((bone) => {
          const out = { name: bone.name, pivot: bone.pivot };
          if (bone.parent) out.parent = bone.parent;
          out.cubes = bone.cubes.map((cube) => {
            const uv = {};
            for (const face of FACES) uv[face] = toBedrockUv(cube.uv[face], face);
            const entry = {
              origin: cube.from,
              size: [round(cube.to[0] - cube.from[0]), round(cube.to[1] - cube.from[1]), round(cube.to[2] - cube.from[2])],
              uv,
            };
            if (isRotated(cube)) {
              // geo 侧 X/Y 取负，Z 原样
              entry.pivot = cube.pivot;
              entry.rotation = [round(-cube.rotation[0]), round(-cube.rotation[1]), round(cube.rotation[2])];
            }
            return entry;
          });
          return out;
        }),
      },
    ],
  };
}

function toBbmodel(identifier, textureName, bones, template) {
  const elements = [];
  const groups = [];
  const outliner = [];

  for (const bone of bones) {
    const groupUuid = bone.uuid || stableUuid('group/' + bone.name);
    const children = bone.cubes.map((cube) => {
      const faces = {};
      for (const face of FACES) {
        faces[face] = {
          uv: [round(cube.uv[face].u0), round(cube.uv[face].v0), round(cube.uv[face].u1), round(cube.uv[face].v1)],
          texture: 0,
        };
      }
      const element = {
        name: cube.name,
        box_uv: false,
        render_order: 'default',
        locked: false,
        export: true,
        scope: 0,
        allow_mirror_modeling: true,
        from: cube.from,
        to: cube.to,
        autouv: 0,
        color: cube.color === undefined ? 0 : cube.color,
        origin: bone.pivot,
        faces,
        type: 'cube',
        uuid: cube.uuid || stableUuid(cube.name),
      };
      if (isRotated(cube)) {
        element.origin = cube.pivot;
        element.rotation = cube.rotation;
      }
      elements.push(element);
      return element.uuid;
    });

    groups.push({
      name: bone.name,
      uuid: groupUuid,
      export: true,
      locked: false,
      scope: 0,
      selected: false,
      _static: { properties: {}, temp_data: {} },
      origin: bone.pivot,
      rotation: [0, 0, 0],
      color: 0,
      children: [],
      reset: false,
      shade: true,
      mirror_uv: false,
      visibility: true,
      autouv: 0,
      isOpen: false,
      primary_selected: false,
    });
    outliner.push({ uuid: groupUuid, isOpen: false, children });
  }

  const texture = Object.assign({}, template.model.textures[0], {
    name: textureName + '.png',
    relative_path: '../../src/main/resources/assets/ssc-primalstinct/textures/block/gecko/' + textureName + '.png',
    uuid: stableUuid('texture/' + textureName),
    source: 'data:image/png;base64,' + fs.readFileSync(path.join(template.textureDir, textureName + '.png')).toString('base64'),
  });

  return Object.assign({}, template.model, {
    name: identifier,
    model_identifier: identifier,
    elements,
    groups,
    outliner,
    textures: [texture],
  });
}

/**
 * 读一个已有 .bbmodel 当模板：保留 display、resolution、geckolib_model_type 等字段。
 * textureDir 指向要内嵌的 PNG 所在目录。
 */
function loadTemplate(file, textureDir) {
  const model = JSON.parse(fs.readFileSync(file, 'utf8'));
  return { model, textureDir };
}

module.exports = { CRLF, FACES, round, geoJson, stableUuid, toBedrockUv, toGeo, toBbmodel, loadTemplate, isRotated };
