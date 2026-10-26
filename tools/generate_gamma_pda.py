#!/usr/bin/env python3
"""Reproducible rugged housing. Screen quad/UVs and hand grips are copied unchanged from the classic model."""
from pathlib import Path
import json, struct
root = Path(__file__).resolve().parents[1]
source = root / 'src/main/resources/assets/wflib/models/pda/pda.glb'
raw = source.read_bytes()
n = struct.unpack_from('<I', raw, 12)[0]
doc = json.loads(raw[20:20+n])
binary = bytearray(raw[28+n:])
materials = {}
for name, colour in {'rubber':(.075,.08,.085,1), 'gunmetal':(.19,.205,.215,1), 'steel':(.34,.36,.37,1),
                     'button':(.12,.135,.145,1), 'wear':(.39,.40,.40,1), 'green_led':(.17,.55,.25,1), 'red_led':(.44,.10,.09,1)}.items():
    materials[name] = len(doc['materials'])
    doc['materials'].append({'name':name,'pbrMetallicRoughness':{'baseColorFactor':colour,'metallicFactor':0,'roughnessFactor':1}})

def accessor(values, kind, count, component=5126):
    while len(binary)%4: binary.append(0)
    start = len(binary)
    fmt = 'f' if component==5126 else 'H'
    binary.extend(struct.pack('<'+fmt*len(values), *values))
    v=len(doc['bufferViews']);doc['bufferViews'].append({'buffer':0,'byteOffset':start,'byteLength':len(binary)-start})
    i=len(doc['accessors']);a={'bufferView':v,'componentType':component,'count':count,'type':kind}
    if kind=='VEC3': a.update(min=[min(values[k::3]) for k in range(3)],max=[max(values[k::3]) for k in range(3)])
    doc['accessors'].append(a);return i

def box(name, cx,cy,cz, sx,sy,sz, material):
    lo=[cx-sx/2,cy-sy/2,cz-sz/2];hi=[cx+sx/2,cy+sy/2,cz+sz/2]
    p=[];normal=[];uv=[];indices=[]
    for axis,sign,u,v in [(2,1,0,1),(2,-1,1,0),(0,1,1,2),(0,-1,2,1),(1,1,2,0),(1,-1,0,2)]:
        start=len(p)//3
        for a,b in [(0,0),(1,0),(1,1),(0,1)]:
            q=[0,0,0];q[axis]=hi[axis] if sign>0 else lo[axis];q[u]=hi[u] if a else lo[u];q[v]=hi[v] if b else lo[v]
            p+=q; nn=[0,0,0];nn[axis]=sign;normal+=nn;uv += [a,b]
        indices += [start,start+1,start+2,start,start+2,start+3]
    attrs={'POSITION':accessor(p,'VEC3',24),'NORMAL':accessor(normal,'VEC3',24),'TEXCOORD_0':accessor(uv,'VEC2',24)}
    mi=len(doc['meshes']);doc['meshes'].append({'name':name,'primitives':[{'attributes':attrs,'indices':accessor(indices,'SCALAR',36,5123),'material':materials[material]}]})
    ni=len(doc['nodes']);doc['nodes'].append({'name':name,'mesh':mi});doc['nodes'][0]['children'].append(ni)

box('rear_shell',.004,0,-.006,.284,.18,.025,'rubber')
box('upper_bezel',.004,.080,.008,.284,.020,.015,'gunmetal')
box('lower_bezel',.004,-.080,.008,.284,.020,.015,'gunmetal')
box('left_bezel',-.125,0,.008,.026,.14,.015,'gunmetal')
box('right_controls',.129,0,.009,.034,.14,.017,'gunmetal')
# Recess lip sits strictly outside screen edges (+/- .112, +/-.070).
for x in [-.113,.113]: box('screen_recess',x,0,.012,.002,.142,.003,'rubber')
for y in [-.071,.071]: box('screen_recess',0,y,.012,.226,.002,.003,'rubber')
for x in [-.130,.138]:
 for y in [-.080,.080]:
    box('screw_seat',x,y,.016,.007,.007,.002,'rubber')
    box('screw_head',x,y,.017,.004,.004,.002,'steel')
    box('screw_slot',x,y,.0182,.003,.0007,.0004,'rubber')
for i,y in enumerate([.047,.016,-.015,-.046]):
 box('hardware_button_seat',.129,y,.018,.022,.022,.003,'rubber')
 box('hardware_button',.129,y,.020,.017,.017,.004,'button')
 # Raised tactile strokes, restrained neutral markings.
 box('button_mark',.129,y,.0225,.007,.001,.0006,'steel')
 if i%2==0: box('button_mark',.129,y,.0225,.001,.007,.0006,'steel')
for i in range(5): box('indicator',.1165,.059-i*.004,.019,.002,.002,.001,'green_led' if i<3 else 'red_led')
for i in range(18):
 y=-.064+i*.0075
 box('grip_rib',-.135,y,.014,.005,.0015,.003,'rubber')
# Sparse edge wear, outside content. No texture dependency.
for i in range(15):
 x=-.104+i*.014
 box('edge_wear',x,.087-(i%3)*.001,.016,.003+(i%4)*.001,.0006,.0006,'wear')
for i in range(9): box('vent',-.033+i*.009,-.081,.016,.004,.001,.001,'rubber')
doc['asset']['generator']='WFLib tools/generate_gamma_pda.py'
doc['buffers'][0]['byteLength']=len(binary)
js=json.dumps(doc,separators=(',',':')).encode();js+=b' '*((-len(js))%4);binary+=b'\0'*((-len(binary))%4)
result=struct.pack('<III',0x46546c67,2,12+8+len(js)+8+len(binary))+struct.pack('<II',len(js),0x4e4f534a)+js+struct.pack('<II',len(binary),0x004e4942)+binary
(source.parent/'pda_gamma.glb').write_bytes(result)
print(f'Generated {len(result)} bytes, {len(doc["meshes"])} meshes; original screen and grip nodes retained.')
