"""Fetch the official MySQL 8 Windows archive into the ignored workspace, without installing a service."""
from pathlib import Path
import hashlib
import urllib.request
import zipfile
import sys

lts = '--legacy' not in sys.argv
version = '8.4.13' if lts else '8.0.46'
root=Path(__file__).resolve().parents[1]/('.tools/mysql84' if lts else '.tools/mysql')
root.mkdir(parents=True,exist_ok=True)
name=f'mysql-{version}-winx64.zip'
url='https://cdn.mysql.com/Downloads/MySQL-'+('8.4' if lts else '8.0')+'/'+name
checksum=urllib.request.urlopen(url+'.md5',timeout=30).read().decode().split()[0]
archive=root/name
if not archive.exists():
    print('Downloading official MySQL 8 archive...',flush=True)
    urllib.request.urlretrieve(url,archive)
digest=hashlib.md5()
with archive.open('rb') as stream:
    for block in iter(lambda:stream.read(1024*1024),b''):digest.update(block)
assert digest.hexdigest()==checksum,'MySQL archive does not match the official download checksum'
server=root/name.removesuffix('.zip')/'bin/mysqld.exe'
if not server.exists():
    with zipfile.ZipFile(archive) as files:
        for member in files.namelist():
            assert (root/member).resolve().is_relative_to(root.resolve()),'Unsafe archive path'
        files.extractall(root)
print('Ready: '+str(server),flush=True)
