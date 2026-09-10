#!/usr/bin/env python3
"""Author the fixed 1.20.1 sanctuary NBT. Runtime generation is NOT random.

Run: python tools/generate_avatar_sanctum.py [--preview]
Standard library for NBT; optional Pillow for architectural previews.
World coordinates are used during authoring, translated by ORIGIN for export.
"""
import argparse
import gzip
import json
import math
from pathlib import Path
import struct
from collections import Counter, deque

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/data/ssc-primalstinct/structures/avatar_sanctum.nbt'
ORIGIN = (-72, 0, -24)
SIZE = (145, 152, 145)
SPAWN = (0, 96, 0)
PORTAL = (0, 96, -5)
PLATFORM = (0, 108, 52)
AVATAR = (8, 125, 53)  # Small animated tentacles above the fixed stone roots.
SEED = 20260911
blocks = {}
palette = []
palette_lookup = {}


def state(name, **props):
    if ':' not in name:
        name = 'minecraft:' + name
    key = (name, tuple(sorted((k, str(v)) for k, v in props.items())))
    if key not in palette_lookup:
        palette_lookup[key] = len(palette)
        palette.append(key)
    return palette_lookup[key]


def put(x, y, z, material):
    blocks[int(x), int(y), int(z)] = material


def noise(x, y, z):
    n = (x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ SEED) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return (n & 65535) / 65535


STONE = state('stone')
TUFF = state('tuff')
ANDESITE = state('andesite')
POLISHED = state('polished_andesite')
BRICK = state('stone_bricks')
CRACKED = state('cracked_stone_bricks')
MOSSY = state('mossy_stone_bricks')
DEEP = state('deepslate_tiles')
DEEP_CRACKED = state('cracked_deepslate_tiles')
MOSS = state('moss_block')
CARPET = state('moss_carpet')
LIGHT = state('sea_lantern')
FERN = state('fern')
LEAVES = state('azalea_leaves', persistent='true', distance='1', waterlogged='false')
SLAB = state('stone_brick_slab', type='bottom', waterlogged='false')
STAIRS = state('stone_brick_stairs', facing='south', half='bottom', shape='straight', waterlogged='false')
VINE = {face: state('vine', **{k: str(k == face).lower() for k in ['north','south','west','east','up']})
        for face in ['north','south','west','east']}


def masonry(x, y, z, floor=False):
    """Large coherent damp patches, horizontal cast courses, occasional scars."""
    wet = math.sin(x * .19 + z * .11) + math.cos(z * .23 - y * .08)
    n = noise(x, y, z)
    if wet > 1.05 and y > 70:
        return MOSSY if n < .65 else TUFF
    if floor:
        return CRACKED if n < .14 else (ANDESITE if n < .27 else BRICK)
    if y % 17 in (0, 1):
        return CRACKED if n < .18 else BRICK
    if n < .08:
        return CRACKED
    if n < .23:
        return TUFF
    if n < .4:
        return ANDESITE
    return STONE


# x, z, radius, summit, rectangular through-hole axis (None for no hole)
PILLARS = [
    (0, 0, 10, 95, None),
    (-31, 20, 7, 102, 'z'), (31, 16, 6, 92, 'x'),
    (-49, 47, 9, 111, 'x'), (48, 45, 8, 99, 'z'),
    (-34, 79, 7, 100, 'z'), (34, 87, 10, 110, 'x'),
    (-4, 99, 8, 103, 'x'),
    (-57, 91, 4, 94, None), (61, 76, 5, 105, 'z'),
    (-57, 9, 4, 91, None), (54, -7, 5, 101, 'x'),
]


def pillar(cx, cz, radius, summit, hole=None, central=False):
    for x in range(cx-radius-1, cx+radius+2):
        for z in range(cz-radius-1, cz+radius+2):
            dx, dz = x-cx, z-cz
            d = math.hypot(dx, dz)
            if d > radius + .15:
                continue
            for y in range(0, summit+1):
                # Cylinders remain massive solids; east half of central column is torn away.
                if central and y > 32:
                    cut = 3.0 + (107-y)*.055 + math.sin(z*.42)*1.7 + math.sin(y*.31)*.8
                    if dx > cut:
                        continue
                if hole and summit-23 <= y <= summit-15:
                    if (hole == 'x' and abs(dz) <= 2) or (hole == 'z' and abs(dx) <= 2):
                        continue
                # Eroded rim has sparse chips, but never eats the interior floor.
                if d > radius-1 and y >= summit-1 and noise(x,y,z) < .17:
                    continue
                put(x,y,z,masonry(x,y,z,y == summit))
            if (x,summit,z) in blocks and d > radius-3:
                # Raised intermittent rim/cast coping: visibly built, rather than natural stacks.
                if noise(x,0,z) < .30:
                    put(x,summit+1,z,SLAB)


def central_ruin():
    pillar(0,52,19,107,central=True)
    # Raised circular ritual paving, approaching from the intact western/front half.
    for x in range(-18,19):
        for z in range(34,71):
            d = math.hypot(x,z-52)
            if d <= 18 and (x,107,z) in blocks:
                if 14 <= d <= 17 or 4.8 <= d <= 5.8:
                    put(x,107,z,POLISHED if noise(x,0,z) > .13 else CRACKED)
                elif d < 4.2:
                    put(x,107,z,DEEP if noise(x,0,z) > .20 else DEEP_CRACKED)
    # Monumental rectangular piers form the surviving half of an annular ruin.
    # Angles on the east/right are absent, opening the view into the model cavity.
    angles = [80,110,140,170,200,230,260]
    for index, degrees in enumerate(angles):
        a = math.radians(degrees)
        cx, cz = round(15.5*math.cos(a)), round(52+15.5*math.sin(a))
        height = [7,12,10,14,12,9,5][index]
        for x in range(cx-1,cx+2):
            for z in range(cz-1,cz+2):
                for y in range(108,108+height):
                    if y > 106+height and noise(x,y,z) < .35:
                        continue
                    put(x,y,z,masonry(x,y,z))
        # Massive square footings.
        for x in range(cx-2,cx+3):
            for z in range(cz-2,cz+3):
                if (x,107,z) in blocks:
                    put(x,108,z,CRACKED if noise(x,0,z) < .3 else POLISHED)
    # Surviving curved lintel, broken into broad brutalist slabs, never a complete ring.
    for x in range(-19,5):
        for z in range(34,71):
            d = math.hypot(x,z-52)
            angle = math.degrees(math.atan2(z-52,x)) % 360
            if 14 <= d <= 17.6 and 112 <= angle <= 224:
                for y in range(119,122):
                    if angle < 119 or angle > 219:
                        if noise(x,y,z) < .35: continue
                    put(x,y,z,masonry(x,y,z))
    # Collapsed masonry below the torn edge. It doesn't bridge or fill the model cavity.
    for cx,cy,cz,r in [(12,43,44,3),(16,35,58,4),(8,57,67,2),(18,22,51,3)]:
        for x in range(cx-r,cx+r+1):
            for z in range(cz-r,cz+r+1):
                for y in range(cy-r,cy+r+1):
                    if abs(x-cx)+abs(y-cy)+abs(z-cz) <= r+1:
                        put(x,y,z,masonry(x,y,z))


def main_bridge():
    # Continuous 3-block-wide stair spine; broken edges/soffits convey collapse.
    for z in range(8,36):
        y = 95 + min(12,max(0,(z-10)//2))
        next_y = 95 + min(12,max(0,(z+1-10)//2))
        for x in range(-3,4):
            if abs(x) > 1 and (noise(x,0,z) < .38 or (18 <= z <= 24 and x > 1)):
                continue
            put(x,y,z,STAIRS if next_y > y else masonry(x,y,z,True))
            for dy in range(1,3+(z%7 == 0)):
                if abs(x) <= 1 or noise(x,dy,z) > .3:
                    put(x,y-dy,z,masonry(x,y-dy,z))
        # Broken parapet fragments confined to edges.
        if z in (9,10,15,26,27,33):
            put(-3,y+1,z,CRACKED)
        if z in (11,12,29,30):
            put(3,y+1,z,BRICK)
        if z in (10,18,26,34):
            put(-1,y,z,LIGHT)


def broken_bridges():
    for cx,cz,r,top,_ in PILLARS[1:8]:
        dx,dz = cx,cz-52
        length = math.hypot(dx,dz)
        ux,uz = dx/length,dz/length
        # Only two stubs. A guaranteed broad void gap separates each pair.
        for step in list(range(16,23)) + list(range(round(length-r-4),round(length-r+2))):
            if step < 16: continue
            y = round(107+(top-107)*step/length)
            for width in range(-2,3):
                x,z = round(ux*step-uz*width), round(52+uz*step+ux*width)
                if abs(width) == 2 and noise(x,y,z) < .5: continue
                # Do not rebuild the missing central half; its stubs hang below the breach.
                if step < 23 and x > 5: y0 = y-5
                else: y0 = y
                put(x,y0,z,masonry(x,y0,z,True))
                put(x,y0-1,z,CRACKED)


def vegetation():
    # Coherent moss islands with restrained floor coverage; heavy hanging vines.
    columns = {}
    for x,y,z in blocks:
        if y > columns.get((x,z),-1): columns[x,z] = y
    for (x,z),y in list(columns.items()):
        if y < 85 or abs(x) <= 2 and -8 <= z <= 57:
            continue
        wet = math.sin(x*.31+z*.07)+math.cos(z*.27-x*.13)
        if wet > 1.25 and blocks[x,y,z] not in (SLAB,LIGHT):
            if noise(x,y,z) < .65:
                put(x,y,z,MOSS if noise(x,0,z) < .28 else MOSSY)
            if noise(x,y+1,z) < .35:
                put(x,y+1,z,CARPET)
            if noise(x,0,z) < .10 and blocks[x,y,z] == MOSS:
                put(x,y+1,z,FERN)
    # Sparse clinging shrubs on ruins; leaves persistent and never obscure the route.
    for cx,cy,cz in [(-8,108,42),(-14,108,57),(-4,108,66),(-48,112,43),(35,111,83),(-30,103,23)]:
        for dx in range(-2,3):
            for dz in range(-2,3):
                if abs(dx)+abs(dz) <= 3 and (cx+dx,cy-1,cz+dz) in blocks:
                    put(cx+dx,cy,cz+dz,LEAVES)
    supports = list(blocks.items())
    for (x,y,z),mat in supports:
        if y < 80 or mat in (CARPET,FERN,LEAVES,SLAB,LIGHT) or noise(x,y,z) > .022:
            continue
        if abs(x) <= 3 and z < 58: continue
        for dx,dz,face in [(1,0,'west'),(-1,0,'east'),(0,1,'north'),(0,-1,'south')]:
            if (x+dx,y,z+dz) in blocks: continue
            for drop in range(5+int(noise(x,0,z)*19)):
                key = (x+dx,y-drop,z+dz)
                if key in blocks: break
                put(*key,VINE[face])


TENTACLE_TIPS = [(6,117,44),(8,119,56),(12,114,64)]


def stone_tentacles():
    """Three tapering, inward-curled fossil limbs, topped by reserved model sockets."""
    basalt=state('polished_basalt',axis='y')
    blackstone=state('polished_blackstone')
    prismarine=state('dark_prismarine')
    curves=[[(12,36,43),(26,65,34),(21,116,35),TENTACLE_TIPS[0]],
            [(18,27,56),(33,70,55),(26,126,59),TENTACLE_TIPS[1]],
            [(12,40,65),(28,64,78),(30,110,73),TENTACLE_TIPS[2]]]
    for index,points in enumerate(curves):
        for step in range(161):
            t=step/160
            w=[(1-t)**3,3*t*(1-t)**2,3*t*t*(1-t),t**3]
            cx,cy,cz=[sum(w[k]*points[k][axis] for k in range(4)) for axis in range(3)]
            r=4.3*(1-t)+1.35
            for x in range(math.floor(cx-r),math.ceil(cx+r)+1):
                for y in range(math.floor(cy-r),math.ceil(cy+r)+1):
                    for z in range(math.floor(cz-r),math.ceil(cz+r)+1):
                        if (x-cx)**2+(y-cy)**2+(z-cz)**2 <= r*r:
                            n=noise(x,y,z)
                            mat=blackstone if n < .16 else DEEP
                            # Coherent mineral bands give the stone limbs their own silhouette/material.
                            if (y+index*3)%11 in (0,1): mat=basalt
                            if x < cx and n < .12: mat=prismarine
                            put(x,y,z,mat)


def gameplay():
    # Clear overlaps from ruined bridgeheads/column footings along the intact route.
    for z in range(8,49):
        y = 95 + min(12,max(0,(z-10)//2))
        for x in (-1,0,1):
            for h in range(1,5): blocks.pop((x,y+h,z),None)
    # Keep spawn and approach clean. Return gate is behind the player, facing south.
    for x in range(-2,3):
        for z in range(-2,9):
            put(x,95,z,masonry(x,95,z,True))
            for y in range(96,100): blocks.pop((x,y,z),None)
    frame = state('ssc-primalstinct:primal_portal_frame')
    portal = state('ssc-primalstinct:primal_portal')
    for x in range(-2,3):
        for z in range(-7,-2):
            put(x,95,z,POLISHED)
            if abs(x) == 2 or z in (-7,-3):
                put(x,96,z,frame)
            else: put(x,96,z,portal)
    # Small three-sided viewing landing at the conversion platform.
    for x in range(-2,3):
        for z in range(49,55):
            put(x,107,z,DEEP)
            for y in range(108,112): blocks.pop((x,y,z),None)
    put(*PLATFORM,state('ssc-primalstinct:primal_conversion_platform'))
    for x,z,y in [(-4,-1,95),(4,-1,95),(-2,40,107),(-2,47,107),(-3,52,107),(2,55,107)]:
        put(x,y,z,LIGHT)


def walkable_surface(x,z):
    non_solid = {CARPET,FERN,LEAVES,*VINE.values(),state('ssc-primalstinct:primal_portal')}
    for y in range(125,89,-1):
        if blocks.get((x,y,z)) is not None and blocks[x,y,z] not in non_solid:
            if all((x,y+h,z) not in blocks or blocks[x,y+h,z] in non_solid for h in (1,2)):
                return y
    return None


def validate():
    assert blocks.get((0,95,0)) is not None
    assert all((0,y,0) not in blocks for y in (96,97,98))
    assert blocks[PLATFORM] == state('ssc-primalstinct:primal_conversion_platform')
    # Validate complete three-wide approach, not just a visual line on the plan.
    for z in range(0,49):
        for x in (-1,0,1):
            here,there = walkable_surface(x,z),walkable_surface(x,z+1)
            assert here is not None and there is not None and abs(there-here) <= 1, (x,z,here,there)
    seen,queue = {(0,0)},deque([(0,0)])
    while queue:
        x,z = queue.popleft()
        y = walkable_surface(x,z)
        for a,b in [(x+1,z),(x-1,z),(x,z+1),(x,z-1)]:
            h = walkable_surface(a,b)
            if (a,b) not in seen and h is not None and abs(h-y) <= 1:
                seen.add((a,b)); queue.append((a,b))
    assert (0,52) in seen, 'Conversion platform unreachable'
    for cx,cz,*_ in PILLARS[1:]:
        assert (cx,cz) not in seen, ('Broken bridge unexpectedly connected',cx,cz)
    # Space for animated small tips above the stone limbs; never add a fixed entity.
    assert all((x,y,z) not in blocks for x in range(3,17) for y in range(125,145) for z in range(40,68))
    for pos,p in blocks.items():
        assert all(0 <= pos[i]-ORIGIN[i] < SIZE[i] for i in range(3)), pos
        assert 0 <= p < len(palette)
    return len(seen)


# Minimal typed NBT writer (Java structure format, big endian).
def utf(s):
    b = s.encode('utf-8'); return struct.pack('>H',len(b))+b


def payload(t,value):
    if t == 3: return struct.pack('>i',value)
    if t == 8: return utf(value)
    if t == 9:
        subtype,items = value
        return bytes([subtype])+struct.pack('>i',len(items))+b''.join(payload(subtype,v) for v in items)
    if t == 10:
        return b''.join(bytes([typ])+utf(k)+payload(typ,v) for k,(typ,v) in value.items())+b'\0'
    raise ValueError(t)


def export():
    entries=[]
    for (x,y,z),p in sorted(blocks.items(),key=lambda e:(e[0][1],e[0][2],e[0][0])):
        entries.append({'pos':(9,(3,[x-ORIGIN[0],y-ORIGIN[1],z-ORIGIN[2]])),'state':(3,p)})
    states=[]
    for name,props in palette:
        entry={'Name':(8,name)}
        if props: entry['Properties']=(10,{k:(8,v) for k,v in props})
        states.append(entry)
    doc={'DataVersion':(3,3465),'size':(9,(3,list(SIZE))),
         'palette':(9,(10,states)),'blocks':(9,(10,entries)),'entities':(9,(10,[]))}
    OUT.parent.mkdir(parents=True,exist_ok=True)
    OUT.write_bytes(gzip.compress(b'\x0a\x00\x00'+payload(10,doc),mtime=0))
    # Authoring metadata is documentation, not a second runtime world generator.
    meta={'template':'ssc-primalstinct:avatar_sanctum','version':3,'origin':ORIGIN,'size':SIZE,
          'spawn':SPAWN,'spawn_yaw':0,'return_portal':PORTAL,'conversion_platform':PLATFORM,
          'avatar_model_anchor':AVATAR,'stone_tentacle_tips':TENTACLE_TIPS,
          'animation_clearance':{'min':[3,125,40],'max':[16,144,67]},
          'central_column':[0,52,19,107], 'pillars':PILLARS,
          'blocks':len(blocks),'palette_size':len(palette),'seed':SEED,'entities':0,
          'materials':dict(Counter(palette[v][0] for v in blocks.values()))}
    path=ROOT/'docs/avatar_sanctum/layout.json'; path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


def color(p):
    name=palette[p][0]
    if 'sea_lantern' in name: return (172,246,228)
    if 'portal' in name: return (115,155,188)
    if 'conversion' in name: return (94,206,198)
    if 'moss' in name: return (81,103,66)
    if any(n in name for n in ('vine','leaves','fern')): return (55,87,58)
    if 'deepslate' in name: return (58,65,66)
    if 'blackstone' in name: return (49,53,57)
    if 'basalt' in name: return (83,88,85)
    if 'prismarine' in name: return (49,89,84)
    if 'tuff' in name: return (100,108,101)
    if 'cracked' in name: return (115,117,108)
    if 'polished' in name: return (158,162,151)
    return (137,142,133)


def preview():
    from PIL import Image,ImageDraw
    out=ROOT/'docs/avatar_sanctum'
    im=Image.new('RGB',(1150,1250),(16,24,28)); d=ImageDraw.Draw(im)
    scale=7
    highest={}
    for (x,y,z),p in blocks.items():
        if y > highest.get((x,z),(-1,0))[0]: highest[x,z]=(y,p)
    for (x,z),(y,p) in highest.items():
        px,py=70+(x-ORIGIN[0])*scale,110+(z-ORIGIN[2])*scale
        c=tuple(int(v*(.5+.5*y/125)) for v in color(p))
        d.rectangle((px,py,px+scale-1,py+scale-1),fill=c)
    d.text((40,24),'THE PRIMAL RIFT / FIXED SANCTUARY',fill=(217,226,211),stroke_width=0)
    d.text((40,48),'TOP PLAN - SOUTH IS DOWN | NBT GEOMETRY PREVIEW, NOT A GAME SCREENSHOT',fill=(135,165,167))
    for label,(x,y,z) in [('ARRIVAL',SPAWN),('RETURN',PORTAL),('CONVERSION',PLATFORM),('MODEL VOID',AVATAR)]:
        px,py=70+(x-ORIGIN[0])*scale,110+(z-ORIGIN[2])*scale
        d.ellipse((px-3,py-3,px+3,py+3),fill=(248,221,136))
        d.text((px+10,py),label,fill=(248,221,136))
    im.save(out/'plan.png')
    # Exposed voxel faces, depth-sorted orthographic architectural view.
    im=Image.new('RGB',(1600,1350),(17,23,29)); d=ImageDraw.Draw(im)
    def project(v):
        x,y,z=v; return (690+(x+z-48)*6.0,1160+(x-z+52)*2.45-y*6.0)
    faces=[]
    definitions=[((0,1,0),[(0,1,0),(1,1,0),(1,1,1),(0,1,1)],1.05),
                 ((1,0,0),[(1,0,0),(1,0,1),(1,1,1),(1,1,0)],.67),
                 ((0,0,-1),[(0,0,0),(1,0,0),(1,1,0),(0,1,0)],.83)]
    for (x,y,z),p in blocks.items():
        if palette[p][0] == 'minecraft:vine': continue
        for (dx,dy,dz),corners,shade in definitions:
            if (x+dx,y+dy,z+dz) in blocks: continue
            fog=.23+.77*min(1,y/100)
            c=tuple(min(255,int(v*shade*fog)) for v in color(p))
            poly=[project((x+a,y+b,z+c0)) for a,b,c0 in corners]
            faces.append((x+.8167*y-z+dx*.5+dy*.40835-dz*.5,poly,c))
    for _,poly,c in sorted(faces,key=lambda f:f[0]): d.polygon(poly,fill=c)
    d.text((40,28),'THE PRIMAL RIFT / RUINED RING AND ABYSSAL COLUMNS',fill=(216,226,213))
    d.text((40,52),'NBT VOXEL STUDY - STONE ROOTS / RESERVED SPACE FOR ANIMATED TIPS',fill=(137,166,167))
    im.save(out/'overview.png')


def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--preview',action='store_true'); args=parser.parse_args()
    for values in PILLARS: pillar(*values)
    central_ruin(); broken_bridges(); main_bridge(); stone_tentacles(); vegetation(); gameplay()
    reachable=validate(); export()
    if args.preview: preview()
    print(json.dumps({'file':str(OUT),'blocks':len(blocks),'palette':len(palette),
                      'bytes':OUT.stat().st_size,'reachable_surface_cells':reachable,'size':SIZE}))


if __name__ == '__main__': main()
