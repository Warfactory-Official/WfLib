#!/usr/bin/env python3
"""Copy STALKER Anomaly particle groups (and what they need) into WFLib's resources, for XrParticles.

Usage: xray-assets.py <gamedata dir> [<gamedata dir> ...] -- <group> [<group> ...] [--sounds <sound> ...]
  gamedata dirs: GAMMA's mods in MO2 order, then the base game's files extracted by xray-db.py
  group: a particle group path as the zone configs name it, e.g. anomaly2/electra2_blast
  sound: a sound as the zone configs name it, e.g. anomaly/electra_blast1 -> assets/wflib/sounds/xray/..., made
         mono if it is not (Minecraft only attenuates mono sounds with distance)

For each group: the .pg, every enabled effect's .pe (children followed), and every texture those name, converted
from DDS to PNG (sheets over 2048 px halved). Earlier gamedata dirs win, as a mod order. Lookups ignore case (mods
ship ElectricBlast1.dds for unrealengine\\electricblast1). Out: src/main/resources/assets/wflib/xray/particles/ and
assets/wflib/textures/xray/, all lower case.

Third-party art: these are a GAMMA mod's textures (themselves from other games). See the CREDITS.md written beside
them before shipping anything.
"""
import os
import re
import subprocess
import sys

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'wflib')
MAX_TEXTURE = 2048


def index(roots):
    """lower-case relative path -> real file, first root wins."""
    files = {}
    for root in roots:
        for dirpath, _, names in os.walk(root):
            for name in names:
                rel = os.path.relpath(os.path.join(dirpath, name), root).replace(os.sep, '/').lower()
                files.setdefault(rel, os.path.join(dirpath, name))
    return files


def read(files, rel):
    path = files.get(rel.lower())
    if path is None:
        raise SystemExit(f'missing: {rel}')
    return open(path, encoding='latin-1').read().replace('\r', '')


def write(rel, text):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(text)


def main():
    split = sys.argv.index('--')
    roots, groups = sys.argv[1:split], sys.argv[split + 1:]
    sounds = []
    if '--sounds' in groups:
        at = groups.index('--sounds')
        groups, sounds = groups[:at], groups[at + 1:]
    files = index(roots)
    effects, textures = set(), set()
    for group in groups:
        rel = f'particles/{group}.pg'.lower()
        text = read(files, rel)
        write(f'xray/{rel}', text)
        for section in re.split(r'\n(?=\[)', text):
            name = re.search(r'effect_name\s*=\s*(\S+)', section)
            flags = re.search(r'\bflags\s*=\s*(\d+)', section)
            if name and flags and int(flags.group(1)) & 4:
                effects.add(name.group(1))
            for child in re.findall(r'on_\w+_child[ \t]*=[ \t]*(\S+)', section):
                effects.add(child)
    pending = list(effects)
    while pending:
        effect = pending.pop()
        rel = 'particles/' + effect.replace('\\', '/').lower() + '.pe'
        text = read(files, rel)
        shader = re.search(r'shader\s*=\s*(\S+)', text)
        if shader and 'distort' in shader.group(1):
            continue  # distortion particles: not drawn (no blur on these anomalies)
        # 'a, b': the sprite, then a distortion map (not drawn).
        texture = re.search(r'texture\s*=\s*([^,\s]+)', text)
        texture = texture and texture.group(1).replace('\\', '/').lower()
        if texture and f'textures/{texture}.dds' not in files:
            # Only in the base game's packed .db archives: the effect is left out (XrParticles skips it).
            print(f'skipped {effect}: missing texture {texture}')
            continue
        write(f'xray/{rel}', text)
        if texture:
            textures.add(texture)
    for texture in sorted(textures):
        src = files.get(f'textures/{texture}.dds')
        if src is None:
            raise SystemExit(f'missing texture: {texture}')
        image = Image.open(src).convert('RGBA')
        if max(image.size) > MAX_TEXTURE:
            image = image.resize((image.size[0] // 2, image.size[1] // 2), Image.LANCZOS)
        dst = os.path.join(OUT, 'textures', 'xray', texture + '.png')
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        image.save(dst, optimize=True)
        print(f'{texture}: {image.size[0]}x{image.size[1]}')
    for sound in sounds:
        rel = sound.replace('\\', '/').lower()
        src = files.get(f'sounds/{rel}.ogg')
        if src is None:
            raise SystemExit(f'missing sound: {sound}')
        dst = os.path.join(OUT, 'sounds', 'xray', rel + '.ogg')
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', src, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5', dst],
                       check=True)
        print(f'sound {rel}')
    if sounds and not os.path.exists(os.path.join(OUT, 'sounds/xray/CREDITS.md')):
        write('sounds/xray/CREDITS.md', """# X-Ray sounds

Converted by devtools/xray-assets.py from STALKER GAMMA's audio mods (Dark Signal Blowout and Anomalies Audio by
Shrike, Dynamic Anomalies Overhaul, and others) and STALKER Anomaly's own archives. Licence unrecorded: for this
internal build only; clear them before anything ships.
""")
    if not os.path.exists(os.path.join(OUT, 'textures/xray/CREDITS.md')):
        write('textures/xray/CREDITS.md', """# X-Ray particle textures

Converted by devtools/xray-assets.py from STALKER GAMMA's particle mods ("Particles Cinematic VFX", Dynamic Anomalies
Overhaul and others), whose textures in turn come from other games (Unreal Engine samples, Crysis 3, Far Cry 4,
Fallout 4), and from STALKER Anomaly's own archives (pfx/, glow/: devtools/xray-db.py). Licence unrecorded: for this
internal build only; clear them (or replace them) before anything ships.
""")


if __name__ == '__main__':
    main()
