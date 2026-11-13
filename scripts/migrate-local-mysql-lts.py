"""Copy an existing local 8.0 database to an empty 8.4 database, preserving the source.

The web app must be stopped (run-local.py stop --keep-mysql). Both portable
servers must be running. No source tables or database files are deleted.
"""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import re
import subprocess
import socket

root = Path(__file__).resolve().parents[1]
try:
    connection = socket.create_connection(('127.0.0.1',8080),timeout=2)
except OSError:
    pass
else:
    connection.close()
    raise SystemExit('Stop the web app with run-local.py stop --keep-mysql before copying.')

source = root / '.tools/mysql'
target = root / '.tools/mysql84'
source_client = source / 'mysql-8.0.46-winx64/bin/mysql.exe'
target_client = target / 'mysql-8.4.13-winx64/bin/mysql.exe'
hidden = subprocess.CREATE_NO_WINDOW

def query(client, directory, sql):
    result = subprocess.run([str(client), f'--defaults-extra-file={directory/"client.ini"}', '--batch', '--skip-column-names', '--raw'],
                            input=sql.encode('utf-8'), capture_output=True, creationflags=hidden)
    if result.returncode:
        raise SystemExit('Database check failed. Inspect local MySQL logs; source was preserved.')
    return result.stdout.decode('utf-8').strip()

assert query(source_client, source, 'select version()').startswith('8.0.')
assert query(target_client, target, 'select version()').startswith('8.4.')
tables_sql = "select table_name from information_schema.tables where table_schema='synera_local' and table_type='BASE TABLE' order by table_name"
tables = query(source_client, source, tables_sql).splitlines()
if not tables:
    raise SystemExit('The source database has no application tables.')
if query(target_client, target, tables_sql):
    raise SystemExit('Destination is not empty. Refusing to replace existing data.')
if not all(re.fullmatch(r'[A-Za-z0-9_]+', table) for table in tables):
    raise SystemExit('Unexpected table identifier.')
counts_sql = ' UNION ALL '.join(f"SELECT '{table}',COUNT(*) FROM synera_local.`{table}`" for table in tables) + ' ORDER BY 1'
financial_sql = """
SELECT 'credit',COALESCE(SUM(balance),0) FROM synera_local.credit_limit
UNION ALL SELECT 'credit_ledger',COALESCE(SUM(debit-credit),0) FROM synera_local.credit_ledger
UNION ALL SELECT 'stock',COALESCE(SUM(qty_on_hand),0) FROM synera_local.stock_balance
UNION ALL SELECT 'reserved',COALESCE(SUM(qty_reserved),0) FROM synera_local.stock_balance
UNION ALL SELECT 'snapshots',COALESCE(SUM(amount),0) FROM synera_local.period_snapshot
ORDER BY 1
"""
source_counts = query(source_client, source, counts_sql)
source_financial = query(source_client, source, financial_sql)
dump = target / 'migration-from-8.0.sql'
with dump.open('xb') as output:
    result = subprocess.run([str(source/'mysql-8.0.46-winx64/bin/mysqldump.exe'),
        f'--defaults-extra-file={source/"client.ini"}', '--single-transaction', '--routines', '--triggers', '--events',
        '--set-gtid-purged=OFF', '--no-tablespaces', 'synera_local'], stdout=output, stderr=subprocess.PIPE, creationflags=hidden)
if result.returncode:
    raise SystemExit('Dump failed. Source preserved; no destination was imported.')
with dump.open('rb') as input_file:
    imported = subprocess.run([str(target_client), f'--defaults-extra-file={target/"client.ini"}', '--binary-mode', 'synera_local'],
                              stdin=input_file, stdout=subprocess.PIPE, stderr=subprocess.PIPE, creationflags=hidden)
if imported.returncode:
    raise SystemExit('Import failed. Source preserved and selection unchanged. Review the destination before retrying.')
assert query(target_client, target, tables_sql).splitlines() == tables, 'Table list differs; keep source selected'
assert query(target_client, target, counts_sql) == source_counts, 'Row counts differ; keep source selected'
assert query(target_client, target, financial_sql) == source_financial, 'Financial/stock totals differ; keep source selected'
report = {'from':'8.0.46', 'to':'8.4.13', 'tableCount':len(tables), 'allTableRowCountsMatch':True,
          'creditLedgerStockSnapshotTotalsMatch':True, 'sourcePreserved':True,
          'dumpSha256':hashlib.sha256(dump.read_bytes()).hexdigest(),
          'copiedAtUtc':datetime.now(timezone.utc).isoformat()}
(root/'artifacts/mysql-lts-migration.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
(root/'.tools/local-mysql-choice.json').write_text(json.dumps({'family':'8.4'}),encoding='utf-8')
print(f'Copied {len(tables)} tables to MySQL 8.4; row counts and financial/stock totals match. Original 8.0 data preserved.')
