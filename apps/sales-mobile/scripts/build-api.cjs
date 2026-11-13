function validateBuildApi(value, profile) {
  let url;
  try {
    url = new URL(value);
  } catch {
    throw new Error(
      'IPA requires EXPO_PUBLIC_API_URL: set a reachable API origin before building.',
    );
  }
  if (
    !['https:', 'http:'].includes(url.protocol) ||
    url.username ||
    url.password ||
    url.search ||
    url.hash ||
    url.pathname !== '/' ||
    ['localhost', '127.0.0.1', '[::1]', '0.0.0.0'].includes(url.hostname)
  )
    throw new Error(
      'API must be an origin reachable from iPhone, without credentials, path or loopback.',
    );
  const privateIPv4 =
    /^(10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+|172\.(1[6-9]|2\d|3[01])\.\d+\.\d+)$/.test(url.hostname);
  if (url.protocol !== 'https:' && !(['preview', 'unsigned'].includes(profile) && privateIPv4))
    throw new Error('HTTPS is required; only preview builds may use a private LAN HTTP API.');
  return url.origin;
}
module.exports = { validateBuildApi };
