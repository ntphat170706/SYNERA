"""Run an isolated loopback-only MySQL in .tools; never install a system service."""
from pathlib import Path
import json
import os
import secrets
import subprocess
import sys

repo=Path(__file__).resolve().parents[1]
lts='--legacy' not in sys.argv
version='8.4.13' if lts else '8.0.46'
port=3308 if lts else 3307
root=repo/('.tools/mysql84' if lts else '.tools/mysql')
server=root/f'mysql-{version}-winx64'
state=root/'local-secrets.json'
data=root/'data'
root.mkdir(parents=True,exist_ok=True)
if not (server/'bin/mysqld.exe').exists():raise SystemExit('Run python scripts/bootstrap-mysql.py first')
if not state.exists():state.write_text(json.dumps({'root':secrets.token_hex(32),'app':secrets.token_hex(32),'test':secrets.token_hex(32)}),encoding='utf-8')
keys=json.loads(state.read_text(encoding='utf-8'))
config=root/'my.ini'
config.write_text(f'''[mysqld]
basedir={server.as_posix()}
datadir={data.as_posix()}
bind-address=127.0.0.1
port={port}
mysqlx=OFF
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
default-time-zone=+00:00
innodb-buffer-pool-size=128M
log-error={root.as_posix()}/server.log
''',encoding='utf-8')
client=root/'client.ini'
client.write_text(f'[client]\nuser=root\npassword={keys["root"]}\nhost=127.0.0.1\nport={port}\nprotocol=TCP\n',encoding='utf-8')
mode=sys.argv[1] if len(sys.argv)>1 else 'start'
if mode=='start':
    if not (data/'mysql').exists():subprocess.run([str(server/'bin/mysqld.exe'),f'--defaults-file={config}','--initialize-insecure'],check=True)
    init=root/'initialize.sql'
    init.write_text(f"ALTER USER 'root'@'localhost' IDENTIFIED BY '{keys['root']}';\nCREATE DATABASE IF NOT EXISTS synera_local;\nCREATE USER IF NOT EXISTS 'synera'@'localhost' IDENTIFIED BY '{keys['app']}';\nGRANT ALL ON synera_local.* TO 'synera'@'localhost';\nCREATE USER IF NOT EXISTS 'synera_test'@'localhost' IDENTIFIED BY '{keys['test']}';\n",encoding='utf-8')
    print(f'Starting MySQL {version} on 127.0.0.1:{port}; data stays in {root.name}/data',flush=True)
    subprocess.run([str(server/'bin/mysqld.exe'),f'--defaults-file={config}',f'--init-file={init.as_posix()}','--console'],check=True)
elif mode=='prepare-test':
    name='synera_test_'+secrets.token_hex(6)
    query=f"CREATE DATABASE {name}; GRANT ALL ON {name}.* TO 'synera_test'@'localhost';"
    subprocess.run([str(server/'bin/mysql.exe'),f'--defaults-extra-file={client}'],input=query,text=True,check=True,capture_output=True)
    (root/'test.properties').write_text(f'url=jdbc:mysql://127.0.0.1:{port}/{name}?sslMode=REQUIRED&connectionTimeZone=UTC\nusername=synera_test\npassword={keys["test"]}\nmigrationUsername=root\nmigrationPassword={keys["root"]}\n',encoding='utf-8')
    print('Created isolated test database '+name,flush=True)
elif mode=='stop':
    subprocess.run([str(server/'bin/mysqladmin.exe'),f'--defaults-extra-file={client}','shutdown'],check=True,capture_output=True)
else:raise SystemExit('Use start, prepare-test or stop')
