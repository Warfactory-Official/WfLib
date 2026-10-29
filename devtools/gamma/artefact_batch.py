"""Convert the artefact batch: textures (+ bloom for glow ones) and glb models, parts on slots by STALKER shader.

Usage (from the repo root): python3 -I devtools/gamma/artefact_batch.py <base> src/main/resources/assets/wflib [id...]
  <base>: Anomaly's base archives extracted (devtools/xray-db.py <base> 'meshes/dynamics/artefacts/*' ... and
  'textures/artifact/*', 'textures/pfx/*'); GAMMA itself at $GAMMA (default ~/Games/GAMMA/gamma). [id...]: only these.
  Texture 'pfx/x' -> textures/artifact/x.png. Kinds follow each OGF child's shader (list them with
  devtools/xray-ogf.py <ogf>): models\artefact(2)/xanomaly -> body, selflightl -> glowsolid, lightplanes -> glow,
  xdistortcolor(l) -> veil (then cut out by hand where it hides nothing: see docs/anomalies-and-artefacts.md),
  xdistort -> left out. Hand fixes after conversion (Ring scale, MASK shells) are not redone by this script.
"""
import os, sys, subprocess, json
from PIL import Image, ImageFilter
BASE, R = sys.argv[1], sys.argv[2]
HERE = os.path.dirname(os.path.abspath(__file__))
GAMMA = os.path.expanduser(os.environ.get('GAMMA', '~/Games/GAMMA/gamma'))
ONLY = set(sys.argv[3:])
def find(rel):
    out = subprocess.run([sys.executable, '-I', os.path.join(HERE, 'gamma_find.py'), GAMMA, BASE, rel],
                         capture_output=True, text=True).stdout.strip().split('\t')
    return out[2] if len(out) > 2 and out[1] != 'None' else None
BASE_MESH = {'artefact_itcher', 'artefact_ring'}  # converted from base; mod copies differ
# id: (mesh, [(child, slot, texture, kind)]) kind: body (lit, opaque), veil (lit, blended), glow (emissive, blended)
BATCH = {
 'crystal': ('af_cristall', [(1, 'glow', 'artifact_lavahell1', 'glowsolid')]),
 'itcher': ('artefact_itcher', [(0, 'body', 'artefact_itcher', 'body')]),
 'sponge': ('artefact_sponge', [(0, 'body', 'artefact_sponge', 'body')]),
 'fireball': ('af_fireball', [(0, 'body', 'artifact_lavahell1', 'veil'), (1, 'glow', 'artifact_loner_lava', 'glowsolid'),
                              (2, 'glow2', 'artifact_loner_lavahell', 'glowsolid')]),
 'sparkler': ('af_electra_sparkler', [(0, 'body', 'artifact_sopli', 'veil')]),
 'glass_beads': ('af_dummy_glassbeads', [(0, 'glow', 'artifact_lava', 'glowsolid')]),
 'moonlight': ('af_electra_moonlight', [(0, 'body', 'artifact_hell1', 'body'), (1, 'glow', 'artifact_glow', 'glow'),
                                        (2, 'glow2', 'artifact_lighting', 'glow')]),
 'flash': ('af_electra_flash', [(0, 'glow', 'artifact_glow', 'glow'), (1, 'body', 'artifact_fire', 'veil'),
                                (2, 'glow2', 'artifact_fire_light', 'glow')]),
 'ice': ('af_ice', [(0, 'glow', 'artifact_hell1', 'glowsolid'), (1, 'glow2', 'artifact_sopli2', 'glowsolid')]),
 'ring': ('artefact_ring', [(0, 'body', 'artefact_ring', 'body')]),
 'dummy': ('af_dummy_dummy', [(0, 'glow', 'artifact_anim_lightning', 'glow'), (1, 'glow2', 'artifact_sopli2', 'glowsolid'),
                              (2, 'body', 'artifact_sopli', 'veil')]),
 'stone_flower': ('af_cristall_flower', [(0, 'body', 'artifact_myasorubka2', 'body'), (1, 'glow', 'artifact_hell1', 'glowsolid')]),
 'stone_blood': ('af_blood', [(0, 'glow', 'artifact_blood', 'glowsolid')]),
 'lobster_eyes': ('artefact_lobster_eyes', [(0, 'glow', 'artefact_lobster_eyes', 'glowsolid'),
                                            (1, 'body', 'artefact_lobster_eyes', 'body')]),
 'jellyfish': ('af_medusa', [(0, 'body', 'artifact_anim_dist', 'veil'), (1, 'glow', 'artifact_myasorubka3', 'glowsolid')]),
 'meat_chunk': ('af_mincer_meat', [(0, 'glow', 'artifact_mincer_meat', 'glowsolid')]),
 'pin': ('artefact_pin', [(0, 'body', 'artefact_pin', 'body'), (1, 'body', 'artefact_pin', 'body')]),
 'bubble': ('af_baloon', [(1, 'glow', 'artifact_mp_green', 'glowsolid')]),
 'black_spray': ('artefact_black_spray', [(0, 'body', 'artefact_black_spray', 'body'),
                                          (1, 'glow', 'artefact_black_spray', 'glowsolid')]),
 'bracelet': ('artefact_bracelet', [(0, 'glow', 'artefact_bracelet', 'glowsolid')]),
 'eye': ('af_eye', [(0, 'glow', 'artifact_eye', 'glowsolid')]),
 'flame': ('af_fire', [(0, 'body', 'artifact_lavahell1', 'veil'), (1, 'glow', 'pfx/pfx_anomaly_1', 'glow'),
                       (2, 'glow2', 'artifact_lava', 'glowsolid')]),
 'kolobok': ('af_fuzz_kolobok', [(0, 'body', 'artifact_sopli', 'veil'), (1, 'glow', 'artifact_mp_green', 'glowsolid')]),
 'firefly': ('af_glass', [(0, 'glow', 'artifact_fire_light', 'glow'), (1, 'body', 'artifact_myasorubka2', 'body'),
                          (2, 'glow2', 'pfx/pfx_anomaly_4', 'glow')]),
 'wrenched': ('af_vyvert', [(1, 'body', 'artifact_myasorubka3', 'body')]),
 'pellicle': ('artefact_plenka_coc', [(0, 'body', 'pfx/pfx_anomaly_19', 'veil')]),
 'oasis_heart': ('af_cristall_plant', [(0, 'body', 'artifact_hell1', 'veil'), (1, 'body2', 'af_cristall_jup', 'body'),
                                       (2, 'glow', 'pfx/pfx_anomaly_4', 'glow')]),
}
if ONLY:
    BATCH = {k: v for k, v in BATCH.items() if k in ONLY}
os.makedirs(f'{R}/textures/artifact', exist_ok=True)
done = {}
for key, (mesh, parts) in BATCH.items():
    for _, slot, tex, kind in parts:
        name = tex.split('/')[-1]
        dst = f'{R}/textures/artifact/{name}.png'
        if tex not in done:
            src = find(f'textures/{tex}.dds' if '/' in tex else f'textures/artifact/{tex}.dds')
            im = Image.open(src).convert('RGBA')
            if max(im.size) > 512:
                k = 512 / max(im.size); im = im.resize((max(1, int(im.size[0]*k)), max(1, int(im.size[1]*k))), Image.LANCZOS)
            if kind == 'body':
                im.putalpha(255)  # STALKER's body alpha is gloss, not cut-out
            im.save(dst, optimize=True); done[tex] = im.size
        if kind.startswith('glow') and not os.path.exists(dst.replace('.png', '_bloom.png')):
            im = Image.open(dst)
            Image.open(dst).filter(ImageFilter.GaussianBlur(max(2, im.size[0] // 40))).save(dst.replace('.png', '_bloom.png'))
    src = (f'{BASE}/meshes/dynamics/artefacts/{mesh}.ogf' if mesh in BASE_MESH
           else find(f'meshes/dynamics/artefacts/{mesh}.ogf'))
    specs = []
    for child, slot, tex, kind in parts:
        opts = {'body': 'slot', 'veil': 'slot,blend,double', 'glow': 'slot,blend,double', 'glowsolid': 'slot,double'}[kind]
        # Solid glows get their own slot names: the renderer skips them for bloom (Artifact.bloomSlot).
        name = 'glowsolid' + slot[4:] if kind == 'glowsolid' else slot
        specs.append(f'{child}={name}:{opts}')
    subprocess.run([sys.executable, 'devtools/xray-ogf.py', src, f'{R}/models/artifact/{key}.glb', *specs, '--centre'],
                   check=True)
    print(key, mesh, src.split('/gamedata/')[0].split('/')[-1] if '/mods/' in src else 'base')

