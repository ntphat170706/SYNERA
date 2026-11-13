"""Build and run the integrated website on loopback, using the persistent portable MySQL."""
from pathlib import Path
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.request
import ctypes

repo = Path(__file__).resolve().parents[1]
state = repo / '.tools/local-runtime.json'
logs = repo / 'artifacts'
logs.mkdir(exist_ok=True)
mode = sys.argv[1] if len(sys.argv) > 1 else 'start'
hidden = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0

def alive():
    try:
        with urllib.request.urlopen('http://127.0.0.1:8080/actuator/health', timeout=2) as response:
            return response.status == 200
    except Exception:
        return False

def process_alive(pid):
    if os.name == 'nt':
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.OpenProcess.argtypes = [ctypes.c_ulong, ctypes.c_int, ctypes.c_ulong]
        kernel.OpenProcess.restype = ctypes.c_void_p
        kernel.GetExitCodeProcess.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_ulong)]
        kernel.CloseHandle.argtypes = [ctypes.c_void_p]
        handle = kernel.OpenProcess(0x1000, False, int(pid))
        if not handle:
            return ctypes.get_last_error() not in (87, 1168)
        try:
            code = ctypes.c_ulong()
            if not kernel.GetExitCodeProcess(handle, ctypes.byref(code)):
                return True
            return code.value == 259
        finally:
            kernel.CloseHandle(handle)
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False

if mode == 'stop':
    if not state.exists(): raise SystemExit('No runtime owned by run-local.py was recorded.')
    info = json.loads(state.read_text(encoding='utf-8'))
    stop = repo / 'backend/.data/runtime/stop.request'
    stop.parent.mkdir(parents=True, exist_ok=True)
    stop.write_text('stop', encoding='utf-8')
    for _ in range(40):
        if not process_alive(info['pid']): break
        time.sleep(1)
    if process_alive(info['pid']): raise SystemExit('Backend is still running; inspect artifacts/local-backend.log. No forced termination was used.')
    if info.get('mysqlOwned') and '--keep-mysql' not in sys.argv:
        subprocess.run([sys.executable, 'scripts/local-mysql.py', 'stop', '--lts' if info.get('mysqlFamily') == '8.4' else '--legacy'], cwd=repo, check=True, creationflags=hidden)
    state.unlink()
    print('Stopped the owned local runtime. Database and uploaded files were preserved.')
    raise SystemExit(0)
if mode != 'start': raise SystemExit('Usage: python scripts/run-local.py [start|stop] [--no-build]')
if alive(): raise SystemExit('Port 8080 already serves a backend. Stop it before starting another runtime.')
jdks = sorted((repo / '.tools/backend').glob('jdk-25*/bin/java.exe'))
if not jdks: raise SystemExit('Run python scripts/bootstrap-backend.py first (Java 25 and Maven).')
java = str(jdks[-1])
node = shutil.which('node')
if not node:
    candidate = Path(os.environ.get('USERPROFILE', '')) / '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe'
    if candidate.exists(): node = str(candidate)
if not node: raise SystemExit('Install Node.js 22+ or put node on PATH.')
env = os.environ.copy()
env['PATH'] = str(Path(node).parent) + os.pathsep + env.get('PATH', '')
if '--no-build' not in sys.argv:
    subprocess.run([node, 'scripts/build.mjs'], cwd=repo, env=env, check=True)
    subprocess.run(['cmd', '/c', str(repo/'scripts/backend.cmd'), '-DskipTests', 'package'], cwd=repo, env=env, check=True, creationflags=hidden)
selection_file = repo / '.tools/local-mysql-choice.json'
family = json.loads(selection_file.read_text(encoding='utf-8'))['family'] if selection_file.exists() else ('8.0' if (repo/'.tools/mysql/data/mysql').exists() else '8.4')
if family not in ('8.0', '8.4'): raise SystemExit('Invalid local MySQL family selection.')
version = '8.4.13' if family == '8.4' else '8.0.46'
port = '3308' if family == '8.4' else '3307'
mysql = repo / ('.tools/mysql84' if family == '8.4' else '.tools/mysql')
server = mysql / f'mysql-{version}-winx64'
if not (server/'bin/mysqld.exe').exists(): raise SystemExit('Run python scripts/bootstrap-mysql.py' + (' --legacy' if family == '8.0' else '') + ' first.')
client = server/'bin/mysqladmin.exe'
def mysql_ready():
    if not (mysql/'client.ini').exists(): return False
    return subprocess.run([str(client),f'--defaults-extra-file={mysql/"client.ini"}','ping','--silent'],capture_output=True,creationflags=hidden).returncode == 0
owned = not mysql_ready()
if owned:
    with (logs/'local-mysql.log').open('ab') as log:
        subprocess.Popen([sys.executable,'scripts/local-mysql.py','start','--lts' if family=='8.4' else '--legacy'],cwd=repo,stdout=log,stderr=subprocess.STDOUT,creationflags=hidden)
    for _ in range(90):
        if mysql_ready(): break
        time.sleep(1)
    else: raise SystemExit('MySQL did not become ready. See artifacts/local-mysql.log.')
keys=json.loads((mysql/'local-secrets.json').read_text(encoding='utf-8'))
runtime=repo/'backend/.data/runtime'
runtime.mkdir(parents=True,exist_ok=True)
(runtime/'stop.request').unlink(missing_ok=True)
jar=runtime/'synera.jar'
shutil.copyfile(repo/'backend/target-verified/synera-backend-1.0.0-SNAPSHOT.jar',jar)
migration_env=env.copy()
migration_env.update(DB_HOST='127.0.0.1',DB_PORT=port,DB_NAME='synera_local',DB_USERNAME='synera',DB_APP_HOST='localhost',MIGRATION_USERNAME='root',MYSQL_ROOT_PASSWORD=keys['root'])
with (logs/'local-migration.log').open('wb') as log:
    result=subprocess.run([java,'-jar',str(jar),'--migrate-only'],cwd=repo/'backend',env=migration_env,stdout=log,stderr=subprocess.STDOUT,creationflags=hidden)
if result.returncode: raise SystemExit('Migration failed. See artifacts/local-migration.log. Data was preserved.')
web_env=env.copy()
web_env.update(LOCAL_MYSQL_PASSWORD=keys['app'],LOCAL_MYSQL_PORT=port,SPRING_FLYWAY_ENABLED='false')
with (logs/'local-backend.log').open('wb') as log:
    process=subprocess.Popen([java,'-jar',str(jar),'--spring.profiles.active=local,local-mysql','--server.address=127.0.0.1','--debug=false','--logging.level.root=INFO','--logging.level.org.springframework=INFO'],cwd=repo/'backend',env=web_env,stdout=log,stderr=subprocess.STDOUT,creationflags=hidden)
state.write_text(json.dumps({'pid':process.pid,'mysqlOwned':owned,'mysqlFamily':family}),encoding='utf-8')
for _ in range(120):
    if alive(): break
    if process.poll() is not None: raise SystemExit('Backend stopped. See artifacts/local-backend.log.')
    time.sleep(1)
else: raise SystemExit('Backend is not ready. See artifacts/local-backend.log.')
print('SYNERA is ready: http://127.0.0.1:8080')
print('Local accounts: admin@synera.local / npp@synera.local / sales@synera.local')
print('Default local password: SyneraLocal2026! (only provisioned for new local users).')
print('Stop gracefully: python scripts/run-local.py stop')
