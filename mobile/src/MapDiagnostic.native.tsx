import { useState } from 'react';
import { Text, View } from 'react-native';
import { Redirect, router } from 'expo-router';
import MapView, { PROVIDER_GOOGLE } from 'react-native-maps';
import { Button } from './ui';
import { styles as s } from './theme';

type Mode = 'standard' | 'satellite' | 'lite';
export default function MapDiagnostic() {
  const [mode, setMode] = useState<Mode>('standard');
  const [ready, setReady] = useState(false);
  const [loaded, setLoaded] = useState(false);
  if (!__DEV__) return <Redirect href="/" />;
  function select(next: Mode) { setReady(false); setLoaded(false); setMode(next); }
  return <View style={{ flex: 1, padding: 16, gap: 10 }}>
    <Text style={s.label}>Teste do mapa · centro de Uberaba</Text>
    <Text style={s.small}>Mapa isolado, sem localização ou consultas ao catálogo.</Text>
    <View style={{ flexDirection: 'row', gap: 8 }}>
      <Button title="Ruas" secondary={mode !== 'standard'} onPress={() => select('standard')} />
      <Button title="Satélite" secondary={mode !== 'satellite'} onPress={() => select('satellite')} />
      <Button title="Simplificado" secondary={mode !== 'lite'} onPress={() => select('lite')} />
    </View>
    <MapView key={mode} provider={PROVIDER_GOOGLE} style={{ flex: 1 }}
      initialRegion={{ latitude: -19.750001, longitude: -47.930001, latitudeDelta: 0.012, longitudeDelta: 0.012 }}
      mapType={mode === 'satellite' ? 'satellite' : 'standard'} liteMode={mode === 'lite'}
      zoomControlEnabled toolbarEnabled={false}
      onMapReady={() => { setReady(true); console.info(`[MapDiagnostic] ${mode}: ready`); }}
      onMapLoaded={() => { setLoaded(true); console.info(`[MapDiagnostic] ${mode}: loaded`); }} />
    <Text style={s.small}>Componente: {ready ? 'pronto' : 'aguardando'} · Imagem: {loaded ? 'carregada' : 'aguardando'}</Text>
    <Button title="Voltar ao Explorar" secondary onPress={() => router.back()} />
  </View>;
}
