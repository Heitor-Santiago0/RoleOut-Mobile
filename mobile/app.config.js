module.exports = ({ config }) => {
  if (process.env.ROLEOUT_STANDALONE === '1' || process.env.EAS_BUILD_PROFILE === 'preview') {
    let url;
    try { url = new URL(process.env.EXPO_PUBLIC_API_URL ?? ''); } catch { /* handled below */ }
    const host = url?.hostname.toLowerCase() ?? '';
    const local = !host.includes('.') || host === 'localhost' || host.endsWith('.localhost')
      || host.endsWith('.local') || host.endsWith('.internal')
      || /^\d+\.\d+\.\d+\.\d+$/.test(host) || host.includes(':');
    if (!url || url.protocol !== 'https:' || local || url.username || url.password || url.search || url.hash) {
      throw new Error('O APK independente exige EXPO_PUBLIC_API_URL com domínio HTTPS remoto, sem credenciais, parâmetros ou endereço local. Configure a API no ambiente preview do EAS.');
    }
  }
  const mapsKey = process.env.GOOGLE_MAPS_ANDROID_API_KEY?.trim();
  if (process.env.EAS_BUILD_PLATFORM === 'android' && !mapsKey) {
    throw new Error('Configure GOOGLE_MAPS_ANDROID_API_KEY antes de gerar o aplicativo Android.');
  }
  return {
    ...config,
    android: {
      ...config.android,
      ...(process.env.GOOGLE_SERVICES_JSON ? { googleServicesFile: process.env.GOOGLE_SERVICES_JSON } : {}),
    },
    plugins: [
      ...(config.plugins ?? []),
      ['expo-notifications', { defaultChannel: 'horarios', color: '#3478F6' }],
      ...(mapsKey ? [['react-native-maps', { androidGoogleMapsApiKey: mapsKey }]] : []),
    ],
  };
};
