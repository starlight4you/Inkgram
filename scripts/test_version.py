#!/usr/bin/env python3
"""Regression checks for release metadata and upgrade ordering (no Android SDK needed)."""
from pathlib import Path
import tempfile
import unittest
import version


class VersionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.write('1.3.1', 1824)
        (self.root / 'CHANGELOG.md').write_text(
            '# Inkgram\n\n## [Unreleased]\n\n- New fix.\n\n## [1.3.1] - 2026-11-14\n\n- Previous fix.\n')

    def write(self, name, build):
        (self.root / 'version.properties').write_text(
            f'version.name={name}\nversion.app={build}\nversion.upstream=0.29.1.1823\n')

    def test_upgrade_across_all_variant_codes(self):
        self.assertGreater(1824 * 1000, 1823 * 1000 + 999)
        self.assertLessEqual(version.MAX_BUILD * 1000 + 999, 2_100_000_000)

    def test_bump_and_changelog(self):
        self.assertEqual(version.bump(self.root, 'patch'), ('1.3.2', 1825))
        self.assertEqual(version.validate(self.root, 'v1.3.2'), ('1.3.2', 1825))
        content = (self.root / 'CHANGELOG.md').read_text()
        self.assertLess(content.index('[1.3.2]'), content.index('New fix.'))
        self.assertIn('version.upstream=0.29.1.1823', (self.root / 'version.properties').read_text())

    def test_minor_and_major_reset(self):
        self.assertEqual(version.bump(self.root, 'minor'), ('1.4.0', 1825))
        self.assertEqual(version.bump(self.root, 'major'), ('2.0.0', 1826))

    def test_reject_invalid_or_old_version_without_writing(self):
        before = (self.root / 'version.properties').read_text()
        for target in ('1.3.1', '1.2.9', '01.4.0', '1.4', 'bad'):
            with self.assertRaises(ValueError):
                version.bump(self.root, target)
            self.assertEqual((self.root / 'version.properties').read_text(), before)

    def test_tag_and_changelog_required(self):
        with self.assertRaises(ValueError):
            version.validate(self.root, 'v1.3.0')
        (self.root / 'CHANGELOG.md').write_text('# No release entry\n')
        with self.assertRaises(ValueError):
            version.validate(self.root)

    def test_build_bounds_and_duplicates(self):
        for build in (1823, version.MAX_BUILD + 1):
            self.write('1.3.1', build)
            with self.assertRaises(ValueError):
                version.read_version(self.root)
        self.write('1.3.1', 1824)
        with (self.root / 'version.properties').open('a') as handle:
            handle.write('version.app=1825\n')
        with self.assertRaises(ValueError):
            version.read_version(self.root)


if __name__ == '__main__':
    unittest.main()
