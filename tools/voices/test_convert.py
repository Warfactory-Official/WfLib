import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

import convert


class VoiceConverterTest(unittest.TestCase):
    def test_stalker_grenade_warning_and_outgoing_throw_are_separate(self):
        self.assertEqual('grenade', convert.stalker_event(Path('fight/grenade/grenade_1.ogg')))
        self.assertEqual('throw_grenade', convert.stalker_event(Path('fight/grenade/grenade_ready_1.ogg')))
        self.assertIsNone(convert.stalker_event(Path('help/wounded/kill_wounded_1.ogg')))
        self.assertIsNone(convert.stalker_event(Path('fight/enemy/enemy_1_pda.ogg')))

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
