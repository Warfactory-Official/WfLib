#!/usr/bin/env python3
"""Convert a STALKER (X-Ray) .ogf model to .glb for GemRender, one node + mesh per OGF child, in bind pose.

Usage: xray-ogf.py <in.ogf> <out.glb> <child>=<material>[:<options>] ... [--centre]
  child:    index of an OGF child, in file order (xray-ogf.py <in.ogf> lists them with their textures)
  material: the glTF material and node name
  options:  comma list of  tex=<ns:path.png>  base colour texture, a resource-pack PNG GemRender loads by name
                           slot        leave untextured; GemRender fills it per submit (extras.textureSlot)
                           mask        alphaMode MASK, cutoff 0.5      blend    alphaMode BLEND
                           double      double sided
  e.g. xray-ogf.py af_dummy_battery.ogf battery.glb 1=body:tex=wflib:models/artifact/battery_body.png,mask,double \
       0=lightning:slot,blend,double --centre

Reads OGF v4 skinned children (OGF_VERTICES type 1-4: 1-4 bone links, compressed) and plain ones (FVF
0x112: position, normal, uv). X-Ray is left-handed (+Z forward): z is negated and the winding flipped. Metres.
Third-party art: the meshes and textures are GSC's / mod authors'; keep them internal (see the CREDITS.md beside).
"""
import json
import struct
import sys

HEADER, TEXTURE, VERTICES, INDICES, CHILDREN = 0x1, 0x2, 0x3, 0x4, 0x9
# Skinned (compressed) vertex: bone ids, P N T B, weights, uv.
SKINNED = {1: (60, 1, 0), 2: (64, 2, 1), 3: (70, 3, 2), 4: (76, 4, 3)}
# OGF_VERTEXFORMAT_FVF_<n>L = n * 0x12071980: same layouts.
SKINNED.update({n * 0x12071980: SKINNED[n] for n in (1, 2, 3, 4)})
FVF_STATIC = 0x112


def chunks(data, start=0, end=None):
    end = len(data) if end is None else end
    at = start
    while at + 8 <= end:
        cid, size = struct.unpack_from('<II', data, at)
        yield cid & 0x7FFFFFFF, data[at + 8:at + 8 + size]
        at += 8 + size


def children(data):
    for cid, body in chunks(data):
        if cid == CHILDREN:
            return [dict(chunks(child)) for _, child in chunks(body)]
    return [dict(chunks(data))]


def mesh(child):
    vtx = child[VERTICES]
    kind, count = struct.unpack_from('<II', vtx)
    positions, normals, uvs = [], [], []
    at = 8
    if kind in SKINNED:
        size, links, weights = SKINNED[kind]
        # 1 link: P N T B uv, then its u32 bone; 2-4: u16 bones first, P N T B, weights, uv.
        bone_bytes = 0 if links == 1 else 2 * links
        uv_at = 48 if links == 1 else size - 8
        for _ in range(count):
            p = struct.unpack_from('<3f', vtx, at + bone_bytes)
            n = struct.unpack_from('<3f', vtx, at + bone_bytes + 12)
            uv = struct.unpack_from('<2f', vtx, at + uv_at)
            positions.append((p[0], p[1], -p[2]))
            normals.append((n[0], n[1], -n[2]))
            uvs.append(uv)
            at += size
    elif kind == FVF_STATIC:
        for _ in range(count):
            p = struct.unpack_from('<3f', vtx, at)
            n = struct.unpack_from('<3f', vtx, at + 12)
            uv = struct.unpack_from('<2f', vtx, at + 24)
            positions.append((p[0], p[1], -p[2]))
            normals.append((n[0], n[1], -n[2]))
            uvs.append(uv)
            at += 32
    else:
        raise SystemExit(f'vertex format {kind:#x} not handled')
    idx = child[INDICES]
    n = struct.unpack_from('<I', idx)[0]
    tris = list(struct.unpack_from(f'<{n}H', idx, 4))
    # X-Ray's front faces are clockwise (Direct3D); negating z mirrors them to counter-clockwise, glTF's front: the
    # index order stands as is (swapping it as well culled every outward face).
    return positions, normals, uvs, tris


def texture_name(child):
    names = child.get(TEXTURE, b'').split(b'\0')
    return names[0].decode('latin-1') if names else '', names[1].decode('latin-1') if len(names) > 1 else ''


def write_glb(path, parts):
    """parts: [(name, (positions, normals, uvs, tris), options)]"""
    blob = bytearray()
    views, accessors, meshes, nodes, materials, images, textures = [], [], [], [], [], [], []

    def view(data, target):
        while len(blob) % 4:
            blob.append(0)
        views.append({'buffer': 0, 'byteOffset': len(blob), 'byteLength': len(data), 'target': target})
        blob.extend(data)
        return len(views) - 1

    def accessor(values, comps, kind):
        flat = [c for v in values for c in (v if comps > 1 else (v,))]
        data = struct.pack(f'<{len(flat)}f', *flat)
        acc = {'bufferView': view(data, 34962), 'componentType': 5126, 'count': len(values), 'type': kind}
        if kind == 'VEC3':
            acc['min'] = [min(v[i] for v in values) for i in range(3)]
            acc['max'] = [max(v[i] for v in values) for i in range(3)]
        accessors.append(acc)
        return len(accessors) - 1

    for name, (pos, nor, uv, tris), opts in parts:
        material = {'name': name, 'pbrMetallicRoughness': {'metallicFactor': 0.0, 'roughnessFactor': 0.8}}
        if 'tex' in opts:
            # GemRender reads textures from the resource pack, by name (extras.resourceLocation), never embedded.
            location = opts['tex']
            images.append({'uri': location.rsplit('/', 1)[-1], 'mimeType': 'image/png',
                           'extras': {'resourceLocation': location}})
            textures.append({'source': len(images) - 1})
            material['pbrMetallicRoughness']['baseColorTexture'] = {'index': len(textures) - 1}
        if 'slot' in opts:
            material['extras'] = {'textureSlot': name}
        if 'mask' in opts:
            material['alphaMode'] = 'MASK'
            material['alphaCutoff'] = 0.5
        if 'blend' in opts:
            material['alphaMode'] = 'BLEND'
        if 'double' in opts:
            material['doubleSided'] = True
        materials.append(material)
        idx = struct.pack(f'<{len(tris)}I', *tris)
        accessors.append({'bufferView': view(idx, 34963), 'componentType': 5125, 'count': len(tris),
                          'type': 'SCALAR'})
        ia = len(accessors) - 1
        attrs = {'POSITION': accessor(pos, 3, 'VEC3'), 'NORMAL': accessor(nor, 3, 'VEC3'),
                 'TEXCOORD_0': accessor(uv, 2, 'VEC2')}
        meshes.append({'name': name, 'primitives': [{'attributes': attrs, 'indices': ia,
                                                     'material': len(materials) - 1}]})
        nodes.append({'name': name, 'mesh': len(meshes) - 1})
    gltf = {'asset': {'version': '2.0', 'generator': 'wflib xray-ogf.py'}, 'scene': 0,
            'scenes': [{'nodes': list(range(len(nodes)))}], 'nodes': nodes, 'meshes': meshes,
            'materials': materials, 'accessors': accessors, 'bufferViews': views,
            'buffers': [{'byteLength': len(blob)}]}
    if images:
        gltf['images'] = images
        gltf['textures'] = textures
    js = json.dumps(gltf, separators=(',', ':')).encode()
    js += b' ' * (-len(js) % 4)
    while len(blob) % 4:
        blob.append(0)
    with open(path, 'wb') as f:
        f.write(struct.pack('<III', 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(blob)))
        f.write(struct.pack('<II', len(js), 0x4E4F534A) + js)
        f.write(struct.pack('<II', len(blob), 0x004E4942) + bytes(blob))


def main():
    data = open(sys.argv[1], 'rb').read()
    kids = children(data)
    if len(sys.argv) == 2:
        for i, child in enumerate(kids):
            kind, count = struct.unpack_from('<II', child[VERTICES])
            print(i, texture_name(child), f'format {kind:#x}', f'{count} vertices')
        return
    parts = []
    centre = '--centre' in sys.argv
    specs = [a for a in sys.argv[3:] if a != '--centre']
    for spec in specs:
        index, rest = spec.split('=', 1)
        name, _, raw = rest.partition(':')
        opts = {}
        for o in filter(None, raw.split(',')):
            key, _, value = o.partition('=')
            opts[key] = value or True
        parts.append((name, mesh(kids[int(index)]), opts))
    if centre:
        # Bounding-box centre to the origin, over every part: it spins and floats about its middle.
        allp = [p for _, (pos, _, _, _), _ in parts for p in pos]
        c = [(min(p[i] for p in allp) + max(p[i] for p in allp)) / 2 for i in range(3)]
        parts = [(n, ([(p[0] - c[0], p[1] - c[1], p[2] - c[2]) for p in pos], nor, uv, t), o)
                 for n, (pos, nor, uv, t), o in parts]
    write_glb(sys.argv[2], parts)


if __name__ == '__main__':
    main()
