const { getDefaultConfig } = require('expo/metro-config');
const http = require('node:http');
const fs = require('node:fs');
const nodePath = require('node:path');
const config = getDefaultConfig(__dirname);
const { allowsMobileRequest } = require('./scripts/mobile-policy.cjs');
// Local development bridge for Expo Go and browser preview; backend still enforces role/scope.
config.server.enhanceMiddleware = (middleware) => (req, res, next) => {
  if (req.url?.split('?')[0] === '/phone') {
    const file = nodePath.join(__dirname, '.expo', 'iphone.html');
    if (fs.existsSync(file)) {
      res.writeHead(200, {
        'Content-Type': 'text/html; charset=utf-8',
        'Cache-Control': 'no-store',
      });
      return res.end(fs.readFileSync(file));
    }
    res.writeHead(503, { 'Content-Type': 'text/plain; charset=utf-8' });
    return res.end('Chạy npm run iphone để chuẩn bị QR iPhone.');
  }
  if (!req.url?.startsWith('/api/v1/')) return middleware(req, res, next);
  if (!allowsMobileRequest(req.method, req.url)) {
    res.writeHead(405, { 'Content-Type': 'application/json' });
    return res.end(
      JSON.stringify({
        success: false,
        error: {
          code: 'MOBILE_OPERATION_UNAVAILABLE',
          message: 'Thao tác này chưa được mở trong app Sales.',
        },
      }),
    );
  }
  const headers = { ...req.headers, host: '127.0.0.1:8080' };
  delete headers.origin;
  delete headers.cookie;
  delete headers.referer;
  const proxy = http.request(
    { hostname: '127.0.0.1', port: 8080, path: req.url, method: req.method, headers },
    (upstream) => {
      const responseHeaders = { ...upstream.headers };
      delete responseHeaders['set-cookie'];
      res.writeHead(upstream.statusCode || 502, responseHeaders);
      upstream.pipe(res);
    },
  );
  proxy.on('error', () => {
    if (!res.headersSent) res.writeHead(502, { 'Content-Type': 'application/json' });
    res.end(
      JSON.stringify({
        success: false,
        error: { code: 'BACKEND_UNAVAILABLE', message: 'Chưa kết nối được API local ở cổng 8080.' },
      }),
    );
  });
  req.on('aborted', () => proxy.destroy());
  req.pipe(proxy);
};
module.exports = config;
