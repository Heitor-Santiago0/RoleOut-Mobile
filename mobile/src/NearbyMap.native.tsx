import { useCallback, useEffect, useRef, useState } from 'react';
import { Platform, Text, View } from 'react-native';
import { useFocusEffect } from 'expo-router';
import * as Location from 'expo-location';
import MapView, { Marker, PROVIDER_GOOGLE } from 'react-native-maps';
import { request } from './api';
import { Button, Message } from './ui';
import { styles as s } from './theme';
import type { NearbyMapProps, Point } from './NearbyMap.types';

export function NearbyMap({ demo, token, onSelect }: NearbyMapProps) {
  const [enabled, setEnabled] = useState(0);
  const [position, setPosition] = useState<Location.LocationObject | null>(null);
  const [points, setPoints] = useState<Point[]>([]); const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState('');
  const [mapAttempt, setMapAttempt] = useState(0);
  const [mapLoaded, setMapLoaded] = useState(false);
  const [mapDelayed, setMapDelayed] = useState(false);
  const hasPosition = position !== null;
  useEffect(() => {
    if (!hasPosition || mapLoaded) return;
    const timer = setTimeout(() => setMapDelayed(true), 15000);
    return () => clearTimeout(timer);
  }, [hasPosition, mapLoaded, mapAttempt]);

  useFocusEffect(useCallback(() => {
    if (!enabled || demo) return;
    let active = true; let subscription: Location.LocationSubscription | undefined;
    (async () => {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (!active) return;
      if (!permission.granted) { setPosition(null); setPoints([]); setLoaded(false); setError('Localização não autorizada. Você pode continuar usando a busca por cidade.'); return; }
      if (!await Location.hasServicesEnabledAsync()) { if (active) setError('Ative a localização do aparelho e tente novamente.'); return; }
      subscription = await Location.watchPositionAsync({ accuracy: Location.Accuracy.Balanced, distanceInterval: 20, timeInterval: 10000 }, value => {
        if (active) setPosition(value);
      }, () => { if (active) setError('Não foi possível acompanhar a localização. Tente novamente.'); });
      if (!active) subscription.remove();
    })().catch(() => { if (active) setError('Não foi possível acessar sua localização. Tente novamente.'); });
    return () => { active = false; subscription?.remove(); };
  }, [enabled, demo]));
  useFocusEffect(useCallback(() => {
    if (!position || !enabled || demo) return;
    let active = true;
    const query = new URLSearchParams({ latitude: String(position.coords.latitude), longitude: String(position.coords.longitude), raioKm: '5' });
    request<{ itens: Point[] }>(`/roles/proximos?${query}`, { token }).then(data => {
      if (active) { setPoints(data.itens); setLoaded(true); setError(''); }
    }).catch(e => { if (active) { setPoints([]); setLoaded(false); setError(e instanceof Error ? e.message : 'Não foi possível buscar pontos próximos.'); } });
    return () => { active = false; };
  }, [position, enabled, demo, token]));
  const map = useRef<MapView | null>(null);
  return <View style={{ gap: 12 }}><Text style={s.label}>Rolês perto de você</Text>
    {demo ? <Text style={s.subtitle}>Os exemplos fictícios não possuem localização real.</Text> : <>
      {!enabled && <><Text style={s.subtitle}>Ative para ver sua posição e rolês publicados em até 5 km. Sua posição será usada na consulta de proximidade.</Text><Button title="Ativar minha localização" onPress={() => { setError(''); setEnabled(1); }} /></>}
      {enabled > 0 && !position && !error && <Text style={s.subtitle}>Aguardando a localização do aparelho…</Text>}
      <Message text={error} />{!!error && <Button title="Tentar localização novamente" secondary onPress={() => { setError(''); setEnabled(v => v + 1); }} />}
      {position && <><View style={{ height: 260, width: '100%' }}><MapView key={mapAttempt} ref={map} provider={Platform.OS === 'android' ? PROVIDER_GOOGLE : undefined} style={{ width: '100%', height: '100%' }} mapType="standard" userInterfaceStyle="light" onMapLoaded={() => { setMapLoaded(true); setMapDelayed(false); }} initialRegion={{ latitude: position.coords.latitude, longitude: position.coords.longitude, latitudeDelta: 0.09, longitudeDelta: 0.09 }}>
        <Marker coordinate={{ latitude: position.coords.latitude, longitude: position.coords.longitude }} title="Você está aqui" pinColor="#397FF5" />{points.map(point => <Marker key={point.id} coordinate={{ latitude: point.latitude, longitude: point.longitude }} title={point.nome} description={`${point.distanciaKm.toFixed(1)} km • Toque para ver detalhes`} onCalloutPress={() => onSelect(point.id)} />)}
      </MapView></View><Button title="Centralizar em mim" secondary onPress={() => map.current?.animateToRegion({ latitude: position.coords.latitude, longitude: position.coords.longitude, latitudeDelta: 0.09, longitudeDelta: 0.09 }, 350)} />
        {!mapLoaded && <Text style={s.small}>{mapDelayed ? 'O mapa não concluiu o carregamento. A causa ainda não foi identificada. Você pode consultar os rolês próximos na lista abaixo.' : 'Carregando mapa…'}</Text>}
        <Button title="Recarregar mapa" secondary onPress={() => { setMapLoaded(false); setMapDelayed(false); setMapAttempt(value => value + 1); }} />
        {loaded && <Text style={s.small}>{points.length === 0 ? 'Nenhum rolê publicado com coordenadas encontrado em até 5 km.' : `${points.length} rolê(s) próximo(s). Toque em um ponto para ver detalhes.${points.length === 100 ? ' Mostrando os 100 mais próximos.' : ''}`}</Text>}
        {loaded && points.map(point => <Button key={point.id} title={`${point.nome} • ${point.distanciaKm.toFixed(1)} km`} secondary onPress={() => onSelect(point.id)} />)}
      </>}
    </>}
  </View>;
}
