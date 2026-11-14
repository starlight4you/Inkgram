#!/usr/bin/env python3
"""Inkgram version inspection, validation and local release preparation."""
import argparse
from datetime import date
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
SEMVER = re.compile(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\Z')
MIN_BUILD = 1824  # Must upgrade over the previously published build 1823.
MAX_BUILD = 2_099_999  # versionCode = build * 1000 + SDK/ABI (0..999).


def read_version(root):
    values = {}
    for line in (root / 'version.properties').read_text().splitlines():
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        key, value = line.split('=', 1)
        if key.strip() in values:
            raise ValueError(f'Duplicate property: {key.strip()}')
        values[key.strip()] = value.strip()
    name = values['version.name']
    if not SEMVER.fullmatch(name):
        raise ValueError('version.name must be MAJOR.MINOR.PATCH (no leading zeroes)')
    build = int(values['version.app'])
    if not MIN_BUILD <= build <= MAX_BUILD:
        raise ValueError(f'version.app must be {MIN_BUILD}..{MAX_BUILD}')
    return name, build


def validate(root, tag=None):
    name, build = read_version(root)
    if tag is not None and tag != f'v{name}':
        raise ValueError(f'Tag {tag} does not match v{name}')
    changelog = (root / 'CHANGELOG.md').read_text()
    if not re.search(rf'^## \[{re.escape(name)}\] - \d{{4}}-\d{{2}}-\d{{2}}$', changelog, re.M):
        raise ValueError(f'Missing dated CHANGELOG.md entry for {name}')
    return name, build


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()


def bump(root, target):
    old, build = validate(root)
    parts = [int(n) for n in old.split('.')]
    if target in ('major', 'minor', 'patch'):
        index = ('major', 'minor', 'patch').index(target)
        parts[index] += 1
        parts[index + 1:] = [0] * (2 - index)
        name = '.'.join(map(str, parts))
    else:
        name = target
    if not SEMVER.fullmatch(name) or tuple(map(int, name.split('.'))) <= tuple(map(int, old.split('.'))):
        raise ValueError(f'New version must be greater than {old}')
    if build == MAX_BUILD:
        raise ValueError('Android build number exhausted')
    version_file = root / 'version.properties'
    content = version_file.read_text()
    content = re.sub(r'^version.name=.*$', f'version.name={name}', content, flags=re.M)
    content = re.sub(r'^version.app=.*$', f'version.app={build + 1}', content, flags=re.M)
    changelog_file = root / 'CHANGELOG.md'
    changelog = changelog_file.read_text()
    if '## [Unreleased]\n' not in changelog:
        raise ValueError('Missing Unreleased heading')
    # Move accumulated Unreleased notes into the new version.
    changelog = changelog.replace('## [Unreleased]\n', f'## [Unreleased]\n\n## [{name}] - {date.today().isoformat()}\n', 1)
    version_file.write_text(content)
    changelog_file.write_text(changelog)
    return name, build + 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    sub.add_parser('show')
    check = sub.add_parser('check')
    check.add_argument('--base', help='Compare versions with a previous Git revision')
    check.add_argument('--tag', help='Require the exact release tag, e.g. v1.3.1')
    release = sub.add_parser('release-check')
    release.add_argument('--tag', required=True)
    change = sub.add_parser('bump')
    change.add_argument('target', help='major, minor, patch, or an explicit MAJOR.MINOR.PATCH')
    args = parser.parse_args()
    try:
        if args.command == 'bump':
            if git('status', '--porcelain', '--ignore-submodules=all'):
                raise ValueError('Commit or stash current changes before bumping')
            result = bump(ROOT, args.target)
        else:
            result = validate(ROOT, getattr(args, 'tag', None))
            if args.command == 'check' and args.base:
                previous = dict(line.split('=', 1) for line in git('show', f'{args.base}:version.properties').splitlines()
                                if '=' in line and not line.lstrip().startswith('#'))
                old_build = int(previous['version.app'])
                old_name = previous.get('version.name')
                if result[1] < old_build:
                    raise ValueError('Android build number cannot decrease')
                if old_name and SEMVER.fullmatch(old_name):
                    old_tuple = tuple(map(int, old_name.split('.')))
                    new_tuple = tuple(map(int, result[0].split('.')))
                    if new_tuple < old_tuple or (result[1] != old_build and new_tuple == old_tuple):
                        raise ValueError('A new build requires a greater public version')
                if result[0] != old_name and result[1] <= old_build:
                    raise ValueError('A new public version requires a greater build number')
            if args.command == 'release-check':
                if git('status', '--porcelain', '--ignore-submodules=all'):
                    raise ValueError('Release requires a clean worktree')
                if git('rev-parse', f'{args.tag}^{{commit}}') != git('rev-parse', 'HEAD'):
                    raise ValueError('Release tag must point to HEAD')
        print(f'Inkgram {result[0]} | build {result[1]} | tag v{result[0]}')
    except (ValueError, KeyError, subprocess.CalledProcessError) as error:
        parser.exit(1, f'Error: {error}\n')


if __name__ == '__main__':
    main()
