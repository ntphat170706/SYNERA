// Canonical native bridge policy. Always match the full /api/v1 pathname.
const reads = [
  /^\/(notifications|tasks)$/,
  /^\/sales-replacements(?:\/\d+)?$/,
  /^\/auth\/me$/,
  /^\/catalog\/(skus|offerings)$/,
  /^\/stores(?:\/\d+(?:\/360)?)?$/,
  /^\/sales-orders(?:\/(?:\d+|summary))?$/,
  /^\/visits\/today$/,
  /^\/display-audits(?:\/criteria)?$/,
];
const writes = [
  /^\/notifications\/\d+\/read$/,
  /^\/sales-replacements$/,
  /^\/sales-replacements\/\d+\/customer-feedback$/,
  /^\/auth\/(login|otp\/verify|refresh|logout)$/,
  /^\/pricing\/quote$/,
  /^\/sales-orders\/sync$/,
  /^\/visits\/\d+\/check-(in|out)$/,
  /^\/(interactions|appointments|stock-surveys|tickets)$/,
  /^\/sales\/daily-report$/,
  /^\/attachments\/upload$/,
  /^\/display-audits$/,
];
function allowsMobileRequest(method, url) {
  const pathname = (url || '').split('?')[0];
  if (!pathname.startsWith('/api/v1/')) return false;
  const path = pathname.slice('/api/v1'.length);
  return (method === 'GET' ? reads : method === 'POST' ? writes : []).some((rule) =>
    rule.test(path),
  );
}
module.exports = { allowsMobileRequest };
