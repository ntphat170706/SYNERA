import os from 'node:os';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import QRCode from 'qrcode';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
process.chdir(root);
if (process.argv.includes('--login')) {
  const child = spawn(process.execPath, ['node_modules/expo/bin/cli', 'login', '--browser'], {
    stdio: 'inherit',
    cwd: root,
  });
  child.on('exit', (code) => (process.exitCode = code ?? 1));
} else {
  const addresses = Object.entries(os.networkInterfaces())
    .flatMap(([name, rows]) =>
      (rows || [])
        .filter(
          (row) =>
            row.family === 'IPv4' &&
            !row.internal &&
            /^(192\.168\.|10\.|172\.(1[6-9]|2\d|3[01])\.)/.test(row.address),
        )
        .map((row) => ({ name, address: row.address })),
    )
    .sort(
      (a, b) =>
        Number(/wi-?fi|wireless|ethernet/i.test(b.name)) -
        Number(/wi-?fi|wireless|ethernet/i.test(a.name)),
    );
  const host = process.env.SYNERA_PHONE_HOST || addresses[0]?.address;
  if (!host) throw Error('Chua tim thay IP LAN. Ket noi Wi-Fi hoac dat SYNERA_PHONE_HOST.');
  const web = `http://${host}:5173`,
    native = `exp://${host}:5173`;
  fs.mkdirSync('.expo', { recursive: true });
  const [webQR, nativeQR] = await Promise.all([
    QRCode.toString(web, { type: 'svg', margin: 1, width: 220 }),
    QRCode.toString(native, { type: 'svg', margin: 1, width: 220 }),
  ]);
  const html = `<!doctype html><html lang="vi"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Mở SYNERA trên iPhone</title><style>body{margin:0;background:#f4f6f8;color:#1f2937;font:16px/1.6 system-ui,sans-serif}main{max-width:780px;margin:48px auto;padding:24px}h1{font-size:30px}section{background:white;border-radius:16px;padding:24px;margin:20px 0}a{color:#1769c2}svg{max-width:200px;width:100%}.row{display:flex;gap:28px;align-items:center}.row div{flex:1}@media(max-width:540px){main{margin:12px auto;padding:16px}.row{flex-direction:column;align-items:start}}code{overflow-wrap:anywhere}</style><main><img alt="SYNERA" width="160" src="data:image/png;base64,${fs.readFileSync('assets/synera-logo.png').toString('base64')}"><h1>Mở SYNERA trên iPhone</h1><p>iPhone và máy tính kết nối cùng Wi-Fi. Cho phép quyền Mạng cục bộ khi iPhone hỏi.</p><section><h2>Xem ngay bằng Safari</h2><div class="row">${webQR}<div><p>Quét QR bằng Camera iPhone, hoặc mở:</p><a href="${web}">${web}</a><p>Đây là bản preview web của giao diện mobile, dùng API thật. Đăng nhập bằng tài khoản Sales đã được cấp.</p></div></div></section><section><h2>Chạy app native bằng Expo Go</h2><div class="row">${nativeQR}<div><ol><li>Cài <a href="https://expo.dev/go">Expo Go</a> trên iPhone.</li><li>Trên máy tính, mở file <strong>Dang-nhap-Expo.cmd</strong> trong thư mục app, rồi đăng nhập ở cửa sổ trình duyệt.</li><li>Trong Expo Go, đăng nhập cùng tài khoản Expo đó.</li><li>Quét QR này bằng Camera iPhone để mở app.</li></ol><a href="${native}">${native}</a><p>Tài khoản Expo dùng để mở app phát triển; tài khoản Sales dùng để đăng nhập SYNERA bên trong app.</p></div></div></section><p>Giữ máy tính và server đang chạy trong khi thử. Nếu iPhone không truy cập được, kiểm tra Wi-Fi/VPN/tường lửa; không cần mở cổng ra Internet.</p></main>`;
  fs.writeFileSync('.expo/iphone.html', html);
  fs.writeFileSync('.expo/phone-connection.json', JSON.stringify({ host, web, native }, null, 2));
  console.log(`Phone setup: ${web}/phone\nExpo Go: ${native}\nWeb: ${web}`);
  if (!process.argv.includes('--prepare-only')) {
    const child = spawn(
      process.execPath,
      ['node_modules/expo/bin/cli', 'start', '--go', '--web', '--host', 'lan', '--port', '5173'],
      {
        cwd: root,
        stdio: 'inherit',
        env: { ...process.env, REACT_NATIVE_PACKAGER_HOSTNAME: host },
      },
    );
    child.on('exit', (code) => (process.exitCode = code ?? 1));
  }
}
