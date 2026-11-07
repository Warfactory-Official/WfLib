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


def discover(root, preset, faction=None):
    """Returns speaker/event -> files, plus every deliberately unmapped clip for review."""
    groups = defaultdict(list)
    unmapped = []
    prefix = FACTIONS[faction] if faction else None
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
    parser.add_argument('source', type=Path, help='STALKER characters_voice/human, or custom <speaker>/<event>/<clip> folders')
    parser.add_argument('--preset', choices=['stalker', 'folders'], default='folders')
    parser.add_argument('--id', help='Required for custom folders')
    parser.add_argument('--factions', nargs='+', choices=sorted(FACTIONS), default=['bandit', 'monolith', 'military'])
    parser.add_argument('--output', type=Path, required=True, help='Destination directory, e.g. minecraft/wflib/voices/packs')
    parser.add_argument('--max-per-event', type=int, default=6, help='Per speaker/event (0 includes all). Default: 6')
    parser.add_argument('--normalize', action='store_true', help='Normalize to -18 LUFS / -2 dB true peak')
    parser.add_argument('--inspect', action='store_true', help='Print mapping counts without encoding or writing packs')
    parser.add_argument('--notice', default='Audio supplied by the pack author; source rights are unchanged.')
    args = parser.parse_args()
    if not args.source.is_dir():
        parser.error('Source must be a directory containing extracted audio')
    if not 0 <= args.max_per_event <= 128:
        parser.error('--max-per-event must be 0–128')
    if args.preset == 'folders' and not args.id:
        parser.error('--id is required for custom folders')
    if not args.inspect and (not shutil.which('ffmpeg') or not shutil.which('ffprobe')):
        parser.error('Install ffmpeg (including ffprobe) first')
    for faction in dict.fromkeys(args.factions) if args.preset == 'stalker' else [None]:
        pack_id = f'stalker_{faction}' if faction else identifier(args.id)
        groups, unmapped = discover(args.source, args.preset, faction)
        if args.inspect:
            print(json.dumps({'pack': pack_id, 'mapped': {f'{s}/{e}': len(v) for (s, e), v in groups.items()},
                              'unmapped_count': len(unmapped)}, indent=2))
        else:
            count, speakers, report = convert(args.source, args.output / f'{pack_id}.zip', pack_id, groups,
                                               unmapped, args.max_per_event, args.normalize, args.notice)
            print(f'{pack_id}: {count} clips, {speakers} speakers; report {report}')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        raise SystemExit(f'Voice conversion failed: {error}')
