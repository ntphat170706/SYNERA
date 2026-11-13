const { validateBuildApi } = require('./scripts/build-api.cjs');

module.exports = ({ config }) => {
  const profile = process.env.EAS_BUILD_PROFILE;
  const api = process.env.EXPO_PUBLIC_API_URL;
  if (profile) validateBuildApi(api, profile);
  return {
    ...config,
    ios: {
      ...config.ios,
      bundleIdentifier: 'vn.synera.sales',
      buildNumber: '4',
      infoPlist: {
        ...config.ios?.infoPlist,
        ITSAppUsesNonExemptEncryption: false,
        NSLocalNetworkUsageDescription:
          'SYNERA kết nối máy chủ trong cùng Wi-Fi để tải và đồng bộ dữ liệu công việc.',
        ...(['preview', 'unsigned'].includes(profile) && api?.startsWith('http:')
          ? { NSAppTransportSecurity: { NSAllowsArbitraryLoads: true } }
          : {}),
      },
    },
  };
};
