"""DAR detector hand clips -> assets/wflib/detector/hands/<tier>/<state>.json (wflib HandClip, prop `detector`).

Per tier: MineAssault tools/blender/stalker_hud_dump.py (flatpak Blender + io_scene_xray) dumps the hands
(wpn_hand_01.ogf + DAR barry_devices_hud_hands.omf) and the device (DAR *_hud.ogf/omf); each state in
models/detector/lowpoly/states.json is baked with tools/handclip/handclip.py (local, untracked), then the section's
hands_position / hands_orientation applied as X-Ray does: setHPB(x, y, z) (heading, pitch, bank) + translate.
usage: bake_hands.py [dump dir]   (default ~/models/detector_dump)
"""
import json
import subprocess
import sys
from pathlib import Path

import numpy as np

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / 'tools' / 'handclip'))
import handclip  # noqa: E402

HOME = Path.home()
DAR = HOME / 'games/gamma/GAMMA/mods/416- Devices of Anomaly Redone - BarryBogs/gamedata'
HANDS = HOME / 'games/gamma/_blender_work/unpacked_weapons_base/meshes/anomaly_weapons/hands/wpn_hand_01.ogf'
HAND_MOTIONS = DAR / 'meshes/anomaly_weapons/hud_hands_animation/barry_devices_hud_hands.omf'
DUMP = REPO.parent / 'MineAssault/tools/blender/stalker_hud_dump.py'
BLENDER = ['flatpak', 'run', f'--env=BLENDER_USER_CONFIG={HOME}/models/.blender_headless_cfg',
           f'--env=BLENDER_USER_EXTENSIONS={HOME}/models/.blender_headless_ext', '--command=blender', 'org.blender.Blender']
ASSETS = REPO / 'src/main/resources/assets/wflib'
STATES = json.load(open(ASSETS / 'models/detector/lowpoly/states.json'))
DEVICE = {t: (f'meshes/dynamics/devices/dev_detector_{i}/dev_diolator_detector_{i}_hud.ogf',
              f'meshes/dynamics/devices/dev_detector_{i}/barry_{t}_detector_hud.omf')
          for i, t in enumerate(('echo', 'bear', 'veles'), 1)}
S = np.diag([1.0, 1.0, -1.0])


def vec(s):
    return [float(x) for x in s.split(',')]


def hpb(h, p, b):
    """X-Ray Fmatrix::setHPB as a column-vector rotation (columns i, j, k), X-Ray axes."""
    sh, ch, sp, cp, sb, cb = np.sin(h), np.cos(h), np.sin(p), np.cos(p), np.sin(b), np.cos(b)
    i = [ch * cb - sp * sh * sb, -cp * sb, sp * ch * sb + sh * cb]
    j = [sp * sh * cb + ch * sb, cp * cb, sh * sb - sp * ch * cb]
    k = [-cp * sh, sp, cp * ch]
    return np.column_stack((i, j, k))


def place(clip, hud):
    """hands_position / hands_orientation onto every track, in view space (x right, y up, -z forward)."""
    rot = S @ hpb(*np.radians(vec(hud['hands_orientation']))) @ S
    offset = S @ np.array(vec(hud['hands_position']))
    for key in ('right', 'left', 'detector'):
        rows = np.array(clip[key])
        rows[:, :3] = rows[:, :3] @ rot.T + offset
        rows[:, 3:] = [handclip.quat(rot @ handclip._mat(q)) for q in rows[:, 3:]]
        clip[key] = np.round(rows, 4).tolist()
    return clip


def aligned(item, clip, n):
    """The device clip cut, or held at its last frame, to the hand clip's n frames."""
    frames = item['clips'][clip]['frames']
    frames = frames[:n] + [frames[-1]] * (n - len(frames))
    return {**item, 'clips': {clip: {**item['clips'][clip], 'frames': frames}}}


def dump(out):
    for tier, (ogf, omf) in DEVICE.items():
        states = STATES[tier]['states'].values()
        cfg = out / f'cfg_{tier}.json'
        cfg.write_text(json.dumps({'hands': str(HANDS), 'handMotions': [str(HAND_MOTIONS)],
                                   'onlyHands': sorted({s[0] for s in states}), 'gun': str(DAR / ogf),
                                   'gunMotions': [str(DAR / omf)], 'onlyGun': sorted({s[1] for s in states}),
                                   'out': str(out / f'{tier}.json')}))
        subprocess.run([*BLENDER, '-b', '--factory-startup', '--python-exit-code', '1', '--python', str(DUMP), '--',
                        str(cfg)], check=True)


def bake(out):
    for tier in DEVICE:
        d = json.load(open(out / f'{tier}.json'))
        dest = ASSETS / 'detector/hands' / tier
        dest.mkdir(parents=True, exist_ok=True)
        for state, row in STATES[tier]['states'].items():
            hands_clip, device_clip = row[0], row[1]
            if hands_clip not in d['hands']['clips'] or device_clip not in d['gun']['clips']:
                print('missing', tier, state, hands_clip in d['hands']['clips'], device_clip in d['gun']['clips'])
                continue
            hands = d['hands']['clips'][hands_clip]
            speed = hands['speed'] * (float(row[2]) if len(row) > 2 else 1.0)
            clip = handclip.bake(hands, aligned(d['gun'], device_clip, len(hands['frames'])), device_clip,
                                 {'detector': ('wpn_body', ['wpn_body'])}, None, speed=speed)
            json.dump(place(clip, STATES[tier]['hud']), open(dest / f'{state}.json', 'w'), separators=(',', ':'))
            print(tier, state, clip['frames'], 'frames', clip['duration'], 's')


if __name__ == '__main__':
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else HOME / 'models/detector_dump'
    target.mkdir(parents=True, exist_ok=True)
    if not (target / 'veles.json').exists():
        dump(target)
    bake(target)
