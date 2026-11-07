import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

import convert


def tiny_pak(files):
    """Smallest valid v11 pak: raw entries, a full directory index, no path-hash index."""
    import struct
    body = b''
    offsets = {}
    for name, data in files.items():
        offsets[name] = len(body)
        # FPakEntry header: offset, size, uncompressed, compression, sha1, flags, block size
        body += struct.pack('<qqqI', 0, len(data), len(data), 0) + bytes(20) + struct.pack('<BI', 0, 0) + data
    def fstr(text):
        raw = text.encode() + b'\0'
        return struct.pack('<i', len(raw)) + raw
    encoded = b''
    entry_at = {}
    for name, data in files.items():
        entry_at[name] = len(encoded)
        # bits: 32-bit offset (31) and 32-bit uncompressed size (30), size 29 unused for raw
        encoded += struct.pack('<I', (1 << 31) | (1 << 30) | (1 << 29)) + struct.pack('<II', offsets[name], len(data))
    dirs = {}
    for name in files:
        directory, leaf = name.rsplit('/', 1)
        dirs.setdefault(directory + '/', []).append((leaf, entry_at[name]))
    fdi = struct.pack('<I', len(dirs))
    for directory, leaves in dirs.items():
        fdi += fstr(directory) + struct.pack('<I', len(leaves))
        for leaf, at in leaves:
            fdi += fstr(leaf) + struct.pack('<i', at)
    fdi_at = len(body)
    body += fdi
    index = fstr('/') + struct.pack('<IQ', len(files), 0) + struct.pack('<I', 0) + struct.pack('<I', 1) \
        + struct.pack('<qq', fdi_at, len(fdi)) + bytes(20) + struct.pack('<i', len(encoded)) + encoded + struct.pack('<I', 0)
    index_at = len(body)
    footer = bytes(17) + struct.pack('<IIQQ', 0x5A6F12E1, 11, index_at, len(index)) + bytes(20) + bytes(160)
    return body + index + footer


class VoiceConverterTest(unittest.TestCase):
    def test_stalker_grenade_warning_and_outgoing_throw_are_separate(self):
        self.assertEqual('grenade', convert.stalker_event(Path('fight/grenade/grenade_1.ogg')))
        self.assertEqual('throw_grenade', convert.stalker_event(Path('fight/grenade/grenade_ready_1.ogg')))
        self.assertIsNone(convert.stalker_event(Path('help/wounded/kill_wounded_1.ogg')))
        self.assertIsNone(convert.stalker_event(Path('fight/enemy/enemy_1_pda.ogg')))

    def test_trepang2_events_follow_the_folder_names(self):
        for path, event in [('Contact/ContactSoldiers-004.ogg', 'contact'), ('Damage/PainGunshot-001.ogg', 'wounded'),
                            ('Damage/PainGunshotRare-001.ogg', 'wounded'), ('Combat/Reloading-001.ogg', 'reload'),
                            ('Combat/Reloading-Replies-001.ogg', None), ('Throwing/ThrowingGrenade-002.ogg', 'throw_grenade'),
                            ('Danger/DangerGrenade-003.ogg', 'grenade'), ('Spawn/SearchTheAreaAggressive-001.ogg', 'search'),
                            ('Target/TargetLostWhereIsHeReplies-001.ogg', 'lost_contact'), ('Hostage/HostageSpotted-001.ogg', None),
                            ('Damage/DeathGunshot-001.ogg', 'death'), ('Damage/Death-001.ogg', 'death'),
                            ('Throwing/ThrowingFlare-001.ogg', None)]:
            self.assertEqual(event, convert.trepang_event(Path(path)), path)

    def test_trepang2_packs_keep_their_voices_and_skip_nearly_silent_ones(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for voice in ['horizon_blackops', 'horizon_grunts', 'syndicate_mercs']:
                path = root / voice / 'Damage/PainGunshot-001.ogg'
                path.parent.mkdir(parents=True)
                path.touch()
            groups, _ = convert.discover(root, 'trepang2', 'horizon')
            self.assertEqual({('horizon_blackops', 'wounded')}, set(groups))

    def test_pak_reader_lists_and_reads_an_uncompressed_v11_pak(self):
        import pak
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'tiny.pak'
            path.write_bytes(tiny_pak({'../../../Game/Content/a/b.uexp': b'hello world'}))
            archive = pak.Pak(path)
            self.assertEqual(b'hello world', archive.read('../../../Game/Content/a/b.uexp'))

    def test_factions_keep_their_speakers(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for speaker in ['bandit_1', 'bandit_2', 'military_1']:
                path = root / speaker / 'fight/enemy/enemy_1.ogg'
                path.parent.mkdir(parents=True)
                path.touch()
            groups, _ = convert.discover(root, 'stalker', 'bandit')
            self.assertEqual({('bandit_1', 'contact'), ('bandit_2', 'contact')}, set(groups))

    @unittest.skipUnless(convert.shutil.which('ffmpeg'), 'ffmpeg not installed')
    def test_custom_pack_real_transcode_is_mono_and_preserves_existing_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            clip = root / 'source/speaker_one/revive/test.wav'
            clip.parent.mkdir(parents=True)
            subprocess.run(['ffmpeg', '-v', 'error', '-f', 'lavfi', '-i', 'sine=frequency=440:duration=0.2',
                            '-ac', '2', str(clip)], check=True, capture_output=True)
            groups, unmapped = convert.discover(root / 'source', 'folders')
            output = root / 'custom.zip'
            convert.convert(root / 'source', output, 'custom', groups, unmapped, 6, False, 'Test audio')
            with zipfile.ZipFile(output) as archive:
                manifest = json.loads(archive.read('voices.json'))
                self.assertIn('revive', manifest['speakers']['speaker_one'])
                audio = next(name for name in archive.namelist() if name.endswith('.ogg'))
                ogg = root / 'test.ogg'
                ogg.write_bytes(archive.read(audio))
                info = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'stream=channels,codec_name',
                                       '-of', 'json', str(ogg)], capture_output=True, text=True, check=True)
                self.assertEqual({'codec_name': 'vorbis', 'channels': 1}, json.loads(info.stdout)['streams'][0])
            before = output.read_bytes()
            with self.assertRaises(FileExistsError):
                convert.convert(root / 'source', output, 'custom', groups, unmapped, 6, False, 'Test audio')
            self.assertEqual(before, output.read_bytes())

    def test_rejects_invalid_ids(self):
        for value in ['../bad', 'UPPERCASE', '', 'bad-name']:
            with self.assertRaises(ValueError):
                convert.identifier(value)


if __name__ == '__main__':
    unittest.main()
