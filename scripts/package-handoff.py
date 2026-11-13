"""Package the checked-out source plus verified web/backend/iOS outputs, without local secrets/data."""
from pathlib import Path
from datetime import datetime
from zoneinfo import ZoneInfo
import argparse,hashlib,json,subprocess,zipfile

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--output',type=Path,default=root/'output/releases')
parser.add_argument('--name',default='SYNERA-Full-'+datetime.now(ZoneInfo('Asia/Ho_Chi_Minh')).strftime('%Y%m%d-%H%M%S')+'.zip')
args=parser.parse_args()
dest=args.output.resolve()/args.name
if Path(args.name).name!=args.name or not args.name.endswith('.zip'):raise SystemExit('Use a simple .zip filename.')
if dest.exists():raise SystemExit('Refusing to overwrite an existing release package.')
def sha(data):return hashlib.sha256(data).hexdigest()
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
names=subprocess.check_output(['git','ls-files','-z'],cwd=root).decode('utf8').split('\0')
forbidden={'.git','.tools','.expo','node_modules','.data','__pycache__'}
extensions={'.p12','.p8','.key','.pem','.mobileprovision','.jks','.keystore','.sqlite','.sqlite3','.db','.log'}
files={}
for name in names:
 if not name:continue
 p=root/name
 if any(x in forbidden for x in Path(name).parts) or p.suffix.lower() in extensions:raise SystemExit('Forbidden file in Git index: '+name)
 if p.name.startswith('.env') and p.name!='.env.example':raise SystemExit('Real environment file is tracked: '+name)
 files[name]=p
jar=root/'backend/target-verified/synera-backend-1.0.0-SNAPSHOT.jar'
if not jar.exists():raise SystemExit('Build the integrated backend JAR first.')
with zipfile.ZipFile(jar) as app:
 if app.read('BOOT-INF/classes/static/index.html')!=(root/'dist/index.html').read_bytes():raise SystemExit('JAR and dist are different builds.')
files[jar.relative_to(root).as_posix()]=jar
for p in (root/'dist').rglob('*'):
 if p.is_file():files[p.relative_to(root).as_posix()]=p
ipa=root/'output/ios/SYNERA-Sales-v4-Internet-unsigned.ipa'
if ipa.exists():
 proof=ipa.with_suffix('.verification.json')
 metadata=json.loads(proof.read_text(encoding='utf8'))
 if sha(ipa.read_bytes())!=metadata['sha256']:raise SystemExit('IPA hash does not match verification metadata.')
 for p in [ipa,proof,ipa.with_suffix('.ipa.sha256')]:files[p.relative_to(root).as_posix()]=p
manifest={'createdAt':datetime.now(ZoneInfo('Asia/Ho_Chi_Minh')).isoformat(),'repository':'https://github.com/ntphat170706/SYNERA','sourceCommit':commit,'includesDatabase':False,'files':[]}
dest.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(dest,'x',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for name,p in sorted(files.items()):
  data=p.read_bytes();z.writestr('SYNERA/'+name,data)
  manifest['files'].append({'path':name,'bytes':len(data),'sha256':sha(data)})
 z.writestr('SYNERA/BUILD-MANIFEST.json',json.dumps(manifest,ensure_ascii=False,indent=2).encode('utf8'))
with zipfile.ZipFile(dest) as z:
 if z.testzip() is not None:raise SystemExit('Archive CRC verification failed.')
 for entry in manifest['files']:
  if sha(z.read('SYNERA/'+entry['path']))!=entry['sha256']:raise SystemExit('Archive hash mismatch: '+entry['path'])
digest=sha(dest.read_bytes())
dest.with_suffix('.zip.sha256').write_text(digest+'  '+dest.name+'\n',encoding='ascii')
report={'zip':str(dest),'bytes':dest.stat().st_size,'sha256':digest,'files':len(files),'sourceCommit':commit,'ipaIncluded':ipa.exists()}
(root/'artifacts').mkdir(exist_ok=True)
(root/'artifacts/package-verification.json').write_text(json.dumps(report,indent=2),encoding='utf8')
print(json.dumps(report))
