"""Download verified portable build tools only into the repository's ignored .tools directory."""
from pathlib import Path
import urllib.request,json,zipfile,hashlib
root=Path('.tools/backend');root.mkdir(parents=True,exist_ok=True)
def fetch(url,target):
    if not target.exists():
        print('Downloading '+target.name,flush=True)
        urllib.request.urlretrieve(url,target)
def verify(target,expected,algorithm):
    h=hashlib.new(algorithm)
    with target.open('rb') as f:
        for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
    if h.hexdigest().lower()!=expected.strip().split()[0].lower():raise RuntimeError('Checksum mismatch: '+str(target))
def unpack(archive):
    with zipfile.ZipFile(archive) as z:
        for name in z.namelist():
            if not (root/name).resolve().is_relative_to(root.resolve()):raise RuntimeError('Unsafe archive path')
        z.extractall(root)
maven=root/'apache-maven-3.9.9-bin.zip'
base='https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip'
fetch(base,maven)
verify(maven,urllib.request.urlopen(base+'.sha512').read().decode(),'sha512')
if not (root/'apache-maven-3.9.9').exists():unpack(maven)
request=urllib.request.Request('https://api.github.com/repos/adoptium/temurin25-binaries/releases/latest',headers={'User-Agent':'SYNERA-build-bootstrap','Accept':'application/vnd.github+json'})
data=json.load(urllib.request.urlopen(request))
package=next(a for a in data['assets'] if a['name'].startswith('OpenJDK25U-jdk_x64_windows_hotspot_') and a['name'].endswith('.zip'))
jdk=root/package['name'];fetch(package['browser_download_url'],jdk)
checksum=next(a for a in data['assets'] if a['name']==package['name']+'.sha256.txt')
verify(jdk,urllib.request.urlopen(checksum['browser_download_url']).read().decode(),'sha256')
if not list(root.glob('jdk-25*/bin/java.exe')):unpack(jdk)
print('Ready: Maven 3.9.9 and Temurin 25 ('+data['tag_name']+')',flush=True)
