"""Check a running local backend without printing or saving authentication secrets."""
from pathlib import Path
from urllib.request import Request, urlopen
import json
import os
import uuid
import zipfile

root = Path(__file__).resolve().parents[1]
base = os.environ.get('BACKEND_URL', 'http://127.0.0.1:8080')

def request(path, body=None, token=None, key=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if key:
        headers['Idempotency-Key'] = key
    req = Request(base + path, data=json.dumps(body).encode() if body is not None else None, headers=headers)
    with urlopen(req, timeout=30) as response:
        return json.load(response)

health = request('/actuator/health')
assert health['status'] == 'UP', health
auth = request('/api/v1/auth/login', {'username': 'npp@synera.local', 'password': os.environ.get('BOOTSTRAP_PASSWORD', 'SyneraLocal2026!')})['data']
token = auth['accessToken']
catalog = request('/api/v1/catalog/skus', token=token)['data']
assert len(catalog) >= 1
key = str(uuid.uuid4())
body = {'nppId': 2, 'lines': [{'skuId': 1, 'uom': 'THUNG', 'qty': 1}]}
created = request('/api/v1/sell-in-orders', body, token, key)['data']
replay = request('/api/v1/sell-in-orders', body, token, key)['data']
assert created['id'] == replay['id']
request(f"/api/v1/sell-in-orders/{created['id']}/cancel", {'reason': 'Local HTTP smoke test'}, token, str(uuid.uuid4()))
request('/api/v1/auth/logout', {}, token)
spec = request('/v3/api-docs')
assert '/api/v1/sell-in-orders' in spec['paths']
assert '/api/v1/sell-in-orders/{id}/close' in spec['paths']
assert '/api/v1/sales-orders/sync' in spec['paths']
(root / 'artifacts/backend-openapi.json').write_text(json.dumps(spec, ensure_ascii=False, indent=2), encoding='utf-8')
jar = root / 'backend/target-verified/synera-backend-1.0.0-SNAPSHOT.jar'
with zipfile.ZipFile(jar) as archive:
    bytecode = archive.read('BOOT-INF/classes/vn/synera/SyneraApplication.class')
assert int.from_bytes(bytecode[6:8], 'big') == 69, 'JAR does not target Java 25'
summary = {'health': health['status'], 'catalogItems': len(catalog), 'idempotencyReplay': True, 'cancelledSmokeDraft': created['id'], 'openapiPaths': len(spec['paths']), 'javaClassVersion': 69}
(root / 'artifacts/backend-smoke.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
print(json.dumps(summary, ensure_ascii=False))
