"""Format Java sources with a checksum-verified, pinned google-java-format jar."""
from pathlib import Path
import hashlib
import subprocess
import urllib.request
import sys

root = Path(__file__).resolve().parents[1]
jar = root / '.tools/backend/google-java-format-1.37.0-all-deps.jar'
url = 'https://repo.maven.apache.org/maven2/com/google/googlejavaformat/google-java-format/1.37.0/' + jar.name
if not jar.exists():
    urllib.request.urlretrieve(url, jar)
# This pinned artifact was also verified against Maven Central's published checksum.
expected = '834b2a0c38cb774953322a84b5ca3f2f40dd3156650b3cd44d3b744345962f7a'
assert hashlib.sha256(jar.read_bytes()).hexdigest() == expected, 'Formatter checksum mismatch'
java = next((root / '.tools/backend').glob('jdk-25*/bin/java.exe'), None)
command = str(java) if java else 'java'
sources = sorted((root / 'backend/src').rglob('*.java'))
mode = ['--dry-run', '--set-exit-if-changed'] if '--check' in sys.argv else ['--replace']
for offset in range(0, len(sources), 30):
    subprocess.run([command, '-jar', str(jar), *mode, *map(str, sources[offset:offset+30])], check=True)
print(f'Formatted/checked {len(sources)} Java files')
