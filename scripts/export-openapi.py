"""Export the running local contract and a searchable endpoint index."""
from pathlib import Path
import json
import urllib.request

root = Path(__file__).resolve().parents[1]
with urllib.request.urlopen('http://127.0.0.1:8080/v3/api-docs', timeout=60) as response:
    schema = json.load(response)
(root / 'artifacts').mkdir(exist_ok=True)
(root / 'artifacts/backend-openapi.json').write_text(json.dumps(schema, ensure_ascii=False, indent=2), encoding='utf-8')
rows = ['# Endpoint được sinh từ OpenAPI', '',
        'Sinh bằng scripts/export-openapi.py từ website local đang chạy. DTO chi tiết tại artifacts/backend-openapi.json.', '',
        '| Method | Path | Operation |', '|---|---|---|']
count = 0
for path, methods in sorted(schema['paths'].items()):
    for method, operation in methods.items():
        if method not in ('get', 'post', 'put', 'patch', 'delete'):
            continue
        rows.append(f'| {method.upper()} | `{path}` | {operation.get("operationId", "")} |')
        count += 1
(root / 'docs/BACKEND-ENDPOINTS.md').write_text('\n'.join(rows) + '\n', encoding='utf-8')
print(f'Exported {len(schema["paths"])} paths, {count} operations')
