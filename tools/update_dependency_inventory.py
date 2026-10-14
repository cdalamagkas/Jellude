#!/usr/bin/env python3
"""Summarize resolved runtime POM licenses, including inherited parent licenses.

This is a metadata inventory, not a substitute for reviewing binary notices.
SPDX-License-Identifier: GPL-3.0-only
"""
import argparse
import os
from pathlib import Path
import xml.etree.ElementTree as ET

NS = {'m': 'http://maven.apache.org/POM/4.0.0'}


def licenses_for(cache, group, name, version, seen=None):
    seen = set() if seen is None else seen
    coordinate = (group, name, version)
    if coordinate in seen:
        raise ValueError(f'POM parent cycle: {coordinate}')
    seen.add(coordinate)
    poms = sorted((cache / group / name / version).glob('*/*.pom'))
    if not poms:
        raise ValueError(f'Cached POM missing: {group}:{name}:{version}')
    pom = ET.parse(poms[0]).getroot()
    declared = [node.findtext('m:name', namespaces=NS) for node in pom.findall('m:licenses/m:license', NS)]
    if declared:
        return declared
    parent = pom.find('m:parent', NS)
    if parent is None:
        raise ValueError(f'License declaration missing: {group}:{name}:{version}')
    return licenses_for(cache, *(parent.findtext('m:' + key, namespaces=NS) for key in ('groupId', 'artifactId', 'version')), seen=seen)


def spdx(name):
    if 'apache' in name.lower() and ('2.0' in name or name == 'Apache-2.0'):
        return 'Apache-2.0'
    if name in ('The MIT License', 'MIT License', 'MIT'):
        return 'MIT'
    raise ValueError(f'Unreviewed license declaration: {name!r}; review before updating the inventory')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, default=Path('build/reports/runtime-dependencies.tsv'))
    parser.add_argument('--gradle-user-home', type=Path, default=Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')))
    parser.add_argument('--output', type=Path, default=Path('docs/DEPENDENCIES.md'))
    args = parser.parse_args()
    cache = args.gradle_user_home / 'caches/modules-2/files-2.1'
    rows = []
    for line in sorted(set(args.input.read_text(encoding='utf-8').splitlines())):
        group, name, version = line.split('\t')
        declared = licenses_for(cache, group, name, version)
        rows.append(f'| `{group}:{name}:{version}` | {", ".join(sorted(set(map(spdx, declared))))} |')
    text = '''# Runtime dependency inventory

Resolved from `:app:releaseRuntimeClasspath` for the source-only public-launch preparation (app version 0.1.3). Coordinates are unique; BOMs, the local core module, build plugins, and test/debug-only dependencies are excluded. License declarations come from the resolved artifacts' published POMs, following parent POMs where necessary (including Guava).

This records declared licenses, not a complete file-by-file audit. Preserve upstream notices and review embedded/native components for the exact packaged APK before binary distribution. See [third-party notices](../THIRD_PARTY_NOTICES.md).

Regenerate from the repository root with JDK 21 and Python 3:

```sh
./gradlew -I tools/runtime-dependencies.init.gradle :app:runtimeDependencyInventory
python tools/update_dependency_inventory.py
```

Use `.\\gradlew.bat` in Windows PowerShell. The script honors `GRADLE_USER_HOME` or accepts `--gradle-user-home PATH`. It stops on missing or unreviewed license declarations; do not replace them with guesses.

| Runtime coordinate | POM-declared license (SPDX) |
|---|---|
'''
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(text + '\n'.join(rows) + f'\n\nTotal: {len(rows)} external runtime coordinates.\n', encoding='utf-8', newline='\n')
    print(f'Wrote {len(rows)} runtime coordinates to {args.output}')


if __name__ == '__main__':
    main()
