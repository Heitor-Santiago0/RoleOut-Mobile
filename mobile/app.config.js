module.exports = ({ config }) => {
  const mapsKey = process.env.GOOGLE_MAPS_ANDROID_API_KEY?.trim();
  if (process.env.EAS_BUILD_PLATFORM === 'android' && !mapsKey) {
    throw new Error('Configure GOOGLE_MAPS_ANDROID_API_KEY antes de gerar o aplicativo Android.');
  }
  return {
    ...config,
    plugins: [
      ...(config.plugins ?? []),
      ...(mapsKey ? [['react-native-maps', { androidGoogleMapsApiKey: mapsKey }]] : []),
    ],
  };
};
