#!/usr/bin/env python3
"""Convert local voice recordings into external Warforge NPC packs. Requires ffmpeg/ffprobe."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from collections import defaultdict
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

EVENTS = frozenset('contact lost_contact search attack covering flank retreat grenade throw_grenade reload wounded help treat revive revived enemy_down idle death'.split())
FACTIONS = {'bandit': 'bandit', 'monolith': 'monolith', 'military': 'military',
            'duty': 'dolg', 'freedom': 'freedom', 'loner': 'stalker', 'mercenary': 'killer'}
# X-Ray's m_stalker.ltx distinguishes grenade_ready (our throw) from grenade (incoming).
# Never map all of help/wounded: it also contains execution dialogue (kill_wounded).
STALKER = [
    (r'fight/enemy/enemy_[^/]+', 'contact'),
    (r'fight/searching_enemy/enemy_lost_[^/]+', 'lost_contact'),
    (r'fight/searching_enemy/search_[^/]+', 'search'),
    (r'fight/attack/attack_[^/]+', 'attack'),
    (r'fight/fire/fire_[^/]+', 'covering'),
    (r'fight/detour/detour_[^/]+', 'flank'),
    (r'fight/grenade/grenade_ready_[^/]+', 'throw_grenade'),
    (r'fight/grenade/grenade_[0-9][^/]*', 'grenade'),
    (r'fight/hit/hit_[^/]+', 'wounded'),
    (r'fight/cover_fire/cover_fire_[^/]+', 'help'),
    (r'help/combat/[^/]+', 'help'),
    (r'fight/enemy_down/enemy_down_[^/]+', 'enemy_down'),
    (r'states/idle/idle_[^/]+', 'idle'),
    (r'fight/death/death_[^/]+', 'death'),
]
# Trepang2 NPC VO: Content/Sounds/VO/NPCs/<faction>/<voice>/<category>/<event>-NNN. The folder names say what each line is
# for; nothing is guessed from listening. Unlisted events (hostage chatter, spawn banter, ...) are reported as unmapped.
TREPANG_FACTIONS = {'horizon': 'Horizon', 'syndicate': 'Syndicate', 'cultists': 'Cultists'}
TREPANG_SKIP_VOICES = {'grunts'}  # death/pain only: a speaker that would be nearly silent
TREPANG = {
    'contact': ['Contact/ContactSoldiers', 'Contact/Contact106', 'Contact/SpottedFlashlight*', 'Target/TargetFoundClose',
                'Target/TargetFoundFar'],
    'lost_contact': ['Target/TargetLost*', 'Target/TargetRunningAway'],
    'search': ['Spawn/SearchTheArea*', 'SusMode/AlertedNoVisualYet', 'SusMode/SusSound', 'SusMode/SusVision'],
    'attack': ['Combat/Suppressing', 'Combat/Cursing'],
    'covering': ['Combat/SuppressingStill', 'Combat/MovingCoverMeReplies'],
    'flank': ['Combat/MovingToFlank', 'Combat/MovingToFlankA', 'Combat/MovingToFlankB'],
    'retreat': ['Combat/Fallback', 'Danger/Suppressed', 'Danger/TakeCover', 'Danger/TakeCoverUrgent'],
    'grenade': ['Danger/DangerGrenade', 'Danger/DangerGrenadesRare', 'Danger/DangerGrenadesSuperRare'],
    'throw_grenade': ['Throwing/ThrowingGrenade', 'Throwing/ThrowingMolotov'],
    'reload': ['Combat/Reloading'],
    'wounded': ['Damage/PainGunshot', 'Damage/PainGunshotRare', 'Damage/PainGeneric', 'Damage/PainGenericWords',
                'Damage/PainMelee'],
    'revived': ['Damage/RecoveredYouOkay'],
    'enemy_down': ['TargetKilled/TargetKilledSoldier'],
    'idle': ['SusMode/SusRelief'],
    'death': ['Damage/DeathGunshot', 'Damage/DeathMelee', 'Damage/DeathFire', 'Damage/Death'],
}
TREPANG_EXACT = {name: event for event, names in TREPANG.items() for name in names if not name.endswith('*')}
TREPANG_PREFIX = [(name[:-1], event) for event, names in TREPANG.items() for name in names if name.endswith('*')]
TREPANG_ROOT = 'CPPFPS/Content/Sounds/VO/NPCs/'
NOTICE = 'Audio supplied by the pack author; source rights are unchanged.'
AUDIO = {'.ogg', '.wav', '.flac', '.mp3', '.m4a', '.opus'}


def identifier(value):
    if not re.fullmatch(r'[a-z0-9_]{1,64}', value):
        raise ValueError(f'Invalid identifier: {value!r}; use lowercase letters, digits and underscores')
    return value


def stalker_event(relative):
    name = relative.with_suffix('').as_posix().lower()
    if name.endswith('_pda'):
        return None
    return next((event for pattern, event in STALKER if re.fullmatch(pattern, name)), None)


def trepang_event(relative):
    """Category/event of one extracted clip, e.g. Damage/PainGunshot-003.ogg -> wounded."""
    parts = relative.with_suffix('').parts
    stem = re.sub(r'-?\d+$', '', parts[-1])
    key = '/'.join((*parts[:-1], stem)) if stem else '/'.join(parts[:-1])
    if key in TREPANG_EXACT:
        return TREPANG_EXACT[key]
    if len(parts) > 1 and (alt := '/'.join(parts[:-1])) in {'Combat/Deaths': 0, 'Combat/Pain': 0}:  # Grunts layout
        return 'death' if alt.endswith('Deaths') else 'wounded'
    return next((event for prefix, event in TREPANG_PREFIX if key.startswith(prefix)), None)


def extract_trepang2(source, destination):
    """Pulls the NPC dialogue clips out of Trepang2's pak into <voice>/<category>/<clip>.ogg under destination.

    A SoundWave's .uexp holds the Ogg Vorbis stream verbatim, followed by the 4-byte package tag. Returns the clip count.
    """
    import pak
    path = source if source.is_file() else next(iter(sorted(source.rglob('*.pak'))), None)
    if path is None:
        raise ValueError(f'No .pak found under {source}')
    archive = pak.Pak(path)
    count = 0
    for name in sorted(archive.entries):
        marker = name.find(TREPANG_ROOT)
        if marker < 0 or not name.endswith('.uexp'):
            continue
        parts = name[marker + len(TREPANG_ROOT):].split('/')
        if len(parts) < 4 or parts[0].lower() not in TREPANG_FACTIONS:
            continue
        data = archive.read(name)
        start = data.find(b'OggS')
        if start < 0 or not re.search(r'(^|-)\d+\.uexp$', parts[-1]):
            continue  # the unnumbered siblings are dialogue-line wrappers, not audio
        voice = f'{parts[0]}_{parts[1]}'.lower()
        out = destination / voice / Path(*parts[2:]).with_suffix('.ogg')
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(data[start:-4])
        count += 1
    return count


def discover(root, preset, faction=None):
    """Returns speaker/event -> files, plus every deliberately unmapped clip for review."""
    groups = defaultdict(list)
    unmapped = []
    prefix = (faction if preset == 'trepang2' else FACTIONS[faction]) if faction else None
    for source in sorted(root.rglob('*')):
        if not source.is_file() or source.suffix.lower() not in AUDIO:
            continue
        relative = source.relative_to(root)
        if len(relative.parts) < 3:
            unmapped.append(relative.as_posix())
            continue
        speaker = relative.parts[0]
        if prefix and not re.fullmatch(re.escape(prefix) + r'_[a-z0-9_]+', speaker):
            continue
        if preset == 'trepang2':
            if speaker.split('_', 1)[1] in TREPANG_SKIP_VOICES:
                continue
            event = trepang_event(Path(*relative.parts[1:]))
        else:
            event = stalker_event(Path(*relative.parts[1:])) if preset == 'stalker' else relative.parts[1]
        if event not in EVENTS:
            unmapped.append(relative.as_posix())
            continue
        identifier(speaker)
        if source.is_symlink() or not source.resolve().is_relative_to(root.resolve()):
            raise ValueError(f'Audio outside source root: {relative}')
        groups[speaker, event].append(source)
    return groups, unmapped


def duration(path):
    result = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration',
                             '-of', 'default=noprint_wrappers=1:nokey=1', str(path)],
                            check=True, capture_output=True, text=True, timeout=30)
    seconds = float(result.stdout.strip())
    if not math.isfinite(seconds) or not 0.05 <= seconds <= 30:
        raise ValueError(f'Clip must be 0.05–30 seconds, got {seconds}: {path}')
    return seconds


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def convert(root, destination, pack_id, groups, unmapped, limit, normalize, notice):
    identifier(pack_id)
    if not groups:
        raise ValueError(f'No mapped audio for {pack_id}')
    if destination.exists():
        raise FileExistsError(f'Refusing to overwrite {destination}; move it or choose a new output directory')
    destination.parent.mkdir(parents=True, exist_ok=True)
    report = {'pack': pack_id, 'source': str(root.resolve()), 'selected': [], 'unmapped': unmapped,
              'missing_events': {}, 'normalization': normalize}
    with tempfile.TemporaryDirectory(prefix='wf-voice-') as temp:
        stage = Path(temp)
        speakers = {}
        sounds = {}
        jobs = []
        for (speaker, event), sources in sorted(groups.items()):
            selected = sources[:limit] if limit else sources
            if len(selected) > 128:
                raise ValueError(f'{speaker}/{event} exceeds 128 variants; set --max-per-event')
            for source in selected:
                key = f'voice/{speaker}/{event}/{hashlib.sha256(source.relative_to(root).as_posix().encode()).hexdigest()[:16]}'
                jobs.append((speaker, event, source, key))

        def transcode(job):
            speaker, event, source, key = job
            duration(source)  # Fail before encoding an accidentally supplied full song.
            target = stage / f'assets/{pack_id}/sounds/{key}.ogg'
            target.parent.mkdir(parents=True, exist_ok=True)
            command = ['ffmpeg', '-nostdin', '-v', 'error', '-i', str(source), '-map', '0:a:0',
                       '-vn', '-ac', '1', '-ar', '44100', '-map_metadata', '-1']
            if normalize:
                command += ['-af', 'loudnorm=I=-18:TP=-2:LRA=11']
            subprocess.run(command + ['-c:a', 'libvorbis', '-q:a', '4', '-threads', '1', str(target)],
                           check=True, capture_output=True, timeout=60)
            seconds = duration(target)
            return job, math.ceil(seconds * 20)

        with ThreadPoolExecutor(max_workers=4) as pool:
            for (speaker, event, source, key), ticks in pool.map(transcode, jobs):
                sound = f'{pack_id}:{key}'
                speakers.setdefault(speaker, {}).setdefault(event, []).append({'sound': sound, 'ticks': ticks})
                sounds[key] = {'sounds': [sound]}
                report['selected'].append({'source': source.relative_to(root).as_posix(), 'speaker': speaker,
                                           'event': event, 'sound': sound, 'ticks': ticks})
        for speaker, events in speakers.items():
            report['missing_events'][speaker] = sorted(EVENTS - events.keys())
        write_json(stage / 'voices.json', {'format': 1, 'id': pack_id, 'speakers': speakers})
        write_json(stage / f'assets/{pack_id}/sounds.json', sounds)
        write_json(stage / 'pack.mcmeta', {'pack': {'pack_format': 34, 'description': f'Warforge voices: {pack_id}'}})
        (stage / 'NOTICE.txt').write_text(notice + '\n', encoding='utf-8')
        files = sorted(p for p in stage.rglob('*') if p.is_file())
        if sum(p.stat().st_size for p in files) > 64 * 1024 * 1024:
            raise ValueError('Pack exceeds expanded 64 MiB limit; lower --max-per-event or split speakers')
        with tempfile.NamedTemporaryFile(prefix='voice-', suffix='.zip', dir=destination.parent, delete=False) as handle:
            archive = Path(handle.name)
        try:
            with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED) as zip_out:
                for path in files:
                    info = zipfile.ZipInfo(path.relative_to(stage).as_posix(), date_time=(1980, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    zip_out.writestr(info, path.read_bytes())
            if archive.stat().st_size > 64 * 1024 * 1024:
                raise ValueError('Pack exceeds compressed 64 MiB limit')
            # Exclusive creation keeps an existing pack safe even if it appeared during conversion.
            os.link(archive, destination)
        finally:
            archive.unlink(missing_ok=True)
    report_path = destination.with_suffix('.report.json')
    write_json(report_path, report)
    return len(jobs), len(speakers), report_path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path, help='STALKER characters_voice/human; Trepang2 Content/Paks (or the .pak); or custom <speaker>/<event>/<clip> folders')
    parser.add_argument('--preset', choices=['stalker', 'trepang2', 'folders'], default='folders')
    parser.add_argument('--id', help='Required for custom folders')
    parser.add_argument('--factions', nargs='+', choices=sorted(FACTIONS) + sorted(TREPANG_FACTIONS), default=None,
                        help='stalker: bandit monolith military ... (default bandit monolith military); trepang2: horizon syndicate cultists (default all)')
    parser.add_argument('--output', type=Path, required=True, help='Destination directory, e.g. minecraft/wflib/voices/packs')
    parser.add_argument('--max-per-event', type=int, default=6, help='Per speaker/event (0 includes all). Default: 6')
    parser.add_argument('--normalize', action='store_true', help='Normalize to -18 LUFS / -2 dB true peak')
    parser.add_argument('--inspect', action='store_true', help='Print mapping counts without encoding or writing packs')
    parser.add_argument('--notice', default=NOTICE)
    args = parser.parse_args()
    trepang = args.preset == 'trepang2'
    if not (args.source.is_file() if trepang and args.source.suffix == '.pak' else args.source.is_dir()):
        parser.error('Source must be a directory containing extracted audio (Trepang2: its Paks folder or the .pak)')
    if not 0 <= args.max_per_event <= 128:
        parser.error('--max-per-event must be 0–128')
    if args.preset == 'folders' and not args.id:
        parser.error('--id is required for custom folders')
    if not args.inspect and (not shutil.which('ffmpeg') or not shutil.which('ffprobe')):
        parser.error('Install ffmpeg (including ffprobe) first')
    if args.preset == 'folders':
        factions = [None]
    else:
        valid = TREPANG_FACTIONS if trepang else FACTIONS
        factions = list(dict.fromkeys(args.factions or (sorted(valid) if trepang else ['bandit', 'monolith', 'military'])))
        for faction in factions:
            if faction not in valid:
                parser.error(f'--factions {faction} is not a {args.preset} faction; choose from {" ".join(sorted(valid))}')
    with tempfile.TemporaryDirectory(prefix='wf-trepang-') as extracted:
        root = args.source
        if trepang:
            root = Path(extracted)
            print(f'Extracted {extract_trepang2(args.source, root)} Trepang2 clips')
        for faction in factions:
            pack_id = f'{args.preset}_{faction}' if faction else identifier(args.id)
            groups, unmapped = discover(root, args.preset, faction)
            if args.inspect:
                print(json.dumps({'pack': pack_id, 'mapped': {f'{s}/{e}': len(v) for (s, e), v in groups.items()},
                                  'unmapped_count': len(unmapped)}, indent=2))
            else:
                notice = args.notice if args.notice != NOTICE else (
                    'Extracted locally from Trepang2 NPC dialogue; source rights are unchanged. Do not redistribute.'
                    if trepang else NOTICE)
                count, speakers, report = convert(root, args.output / f'{pack_id}.zip', pack_id, groups,
                                                   unmapped, args.max_per_event, args.normalize, notice)
                print(f'{pack_id}: {count} clips, {speakers} speakers; report {report}')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        raise SystemExit(f'Voice conversion failed: {error}')
