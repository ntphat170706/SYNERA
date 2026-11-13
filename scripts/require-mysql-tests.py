"""Require a complete real MySQL suite. CI requires Docker; portable MySQL is explicit opt-in."""
import sys
from pathlib import Path
from xml.etree import ElementTree

names = ['LocalMySqlIntegrationTest'] if '--local' in sys.argv else ['MySqlIntegrationTest', 'config.DatabaseMigrationMySqlTest']
for name in names:
    report = Path(__file__).resolve().parents[1] / f'backend/target-verified/surefire-reports/TEST-vn.synera.{name}.xml'
    suite = ElementTree.parse(report).getroot()
    assert int(suite.attrib['tests']) > 0, 'No MySQL tests executed'
    assert all(int(suite.attrib.get(key, 0)) == 0 for key in ['skipped', 'errors', 'failures']), suite.attrib
    print(f"{name}: {suite.attrib['tests']} tests passed, none skipped")
