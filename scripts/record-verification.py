"""Record the completed local verification without exposing credentials or tokens."""
from pathlib import Path
from xml.etree import ElementTree
from datetime import datetime, timezone
import hashlib
import json
import re

root = Path(__file__).resolve().parents[1]
suites = []
for report in sorted((root / 'backend/target-verified/surefire-reports').glob('TEST-*.xml')):
    suite = ElementTree.parse(report).getroot()
    row = {key: int(suite.attrib.get(key, 0)) for key in ('tests', 'failures', 'errors', 'skipped')}
    row['name'] = suite.attrib['name']
    assert row['failures'] == row['errors'] == 0, row['name']
    assert row['skipped'] == 0 or row['name'] == 'vn.synera.MySqlIntegrationTest', 'Unexpected skipped suite: ' + row['name']
    suites.append(row)
mysql = next(row for row in suites if row['name'].endswith('.LocalMySqlIntegrationTest'))
assert mysql['tests'] > 0 and mysql['skipped'] == 0
browser = json.loads((root / 'artifacts/e2e-live-result.json').read_text(encoding='utf-8'))
design = json.loads((root / 'artifacts/design-results.json').read_text(encoding='utf-8'))
assert browser['passed'] >= 18 and not browser['errors']
assert design['passed'] >= 12 and not design['errors']
assert browser['base'] == 'http://127.0.0.1:8080', 'Verify the integrated JAR before recording'
jar = root / 'backend/target-verified/synera-backend-1.0.0-SNAPSHOT.jar'
assert (root / 'artifacts/e2e-live-result.json').stat().st_mtime >= jar.stat().st_mtime
def log(name):
    data = (root / 'artifacts' / name).read_bytes()
    return data.decode('utf-16' if data.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig')
unit = re.search(r'\bTests\s+(\d+)\s+passed\b', log('frontend-unit.log'))
assert unit and int(unit[1]) > 0
assert 'All matched files use Prettier code style!' in log('frontend-format-check.log')
assert 'Offline shell: precached' in log('frontend-final-build.log')
assert 'Architecture checks passed:' in log('frontend-architecture.log')
mysql_versions = []
for name in ('mysql80-result.json', 'mysql84-result.json'):
    path = root / 'artifacts' / name
    if path.exists():
        proof = json.loads(path.read_text(encoding='utf-8'))
        assert proof['tests'] == mysql['tests'] and all(proof[key] == 0 for key in ('failures','errors','skipped'))
        mysql_versions.append(proof['version'])
assert '8.4.13' in mysql_versions, 'Verify the supported production MySQL version'
result = {
    'recordedAtUtc': datetime.now(timezone.utc).isoformat(),
    'java': 25,
    'jarSha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
    'backend': {
        'passed': sum(row['tests'] - row['skipped'] for row in suites),
        'skipped': sum(row['skipped'] for row in suites),
        'mysqlPassed': mysql['tests'],
        'mysqlVersionsVerified': mysql_versions,
        'skipReason': 'Docker daemon unavailable; the corresponding complete portable MySQL suite passed',
        'suites': suites,
    },
    'frontend': {'unitPassed': int(unit[1]), 'architecture': 'passed', 'prettier': 'passed', 'productionBuild': 'passed'},
    'browser': browser,
    'design': design,
    'runtime': json.loads((root / 'artifacts/runtime-smoke.json').read_text(encoding='utf-8')),
    'deployment': json.loads((root / 'artifacts/deploy-validation.json').read_text(encoding='utf-8')),
    'notVerified': ['Docker image/container runtime', 'GitHub Actions execution', 'VPS/HTTPS domain', 'real SMTP/AI/S3 providers', 'ClamAV signatures in a running container', 'backup restore drill', 'production load/pentest'],
    'remainingCodeScope': 'docs/BACKEND-BUILD-SUPPLEMENT.md',
    'localDatabaseMigration': json.loads((root/'artifacts/mysql-lts-migration.json').read_text(encoding='utf-8')) if (root/'artifacts/mysql-lts-migration.json').exists() else None,
}
(root / 'artifacts/verification.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f"Recorded: {result['backend']['passed']} backend passed, {mysql['tests']} real MySQL, {browser['passed']} browser, {design['passed']} design")
