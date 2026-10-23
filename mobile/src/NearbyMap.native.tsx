import { Disclosure } from './Disclosure';
import { RoleIllustration } from './RoleIllustration';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Modal, Platform, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect } from 'expo-router';
import * as Location from 'expo-location';
import MapView, { Marker, PROVIDER_GOOGLE } from 'react-native-maps';
import { request } from './api';
import { Button, Message } from './ui';
import { styles as s } from './theme';
import type { NearbyMapProps, Point } from './NearbyMap.types';
import { nearbyMapStyle } from './nearbyMapStyle';
import { nearbyLocations } from './nearbyLocations';
import { BackArrow, BottomNavigation } from './AppNavigation';

const NEIGHBORHOOD_DELTA = 0.004;

export function NearbyMap({ demo, token, onSelect, onCity, onNearby, onContext }: NearbyMapProps) {
  const [resolvedCity, setResolvedCity] = useState<{ key: string; name: string } | null>(null);
  const [enabled, setEnabled] = useState(0);
  const [checkingPermission, setCheckingPermission] = useState(true);
  const permissionRequested = useRef(false);
  const [position, setPosition] = useState<Location.LocationObject | null>(null);
  const [points, setPoints] = useState<Point[]>([]); const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState('');
  const [mapAttempt, setMapAttempt] = useState(0);
  const [mapLoaded, setMapLoaded] = useState(false);
  const [mapDelayed, setMapDelayed] = useState(false);
  const [selectedLocation, setSelectedLocation] = useState<string | null>(null);
  const locations = nearbyLocations(points);
  const selected = locations.find(location => location.key === selectedLocation);
  const hasPosition = position !== null;
  useEffect(() => {
    if (!hasPosition || mapLoaded) return;
    const timer = setTimeout(() => setMapDelayed(true), 15000);
    return () => clearTimeout(timer);
  }, [hasPosition, mapLoaded, mapAttempt]);

  useFocusEffect(useCallback(() => {
    if (demo) return;
    let active = true; let subscription: Location.LocationSubscription | undefined;
    (async () => {
      let permission = await Location.getForegroundPermissionsAsync();
      if (!active) return;
      setCheckingPermission(false);
      if (!enabled && permission.granted) { setEnabled(1); return; }
      if (!enabled) return;
      if (!permission.granted && permissionRequested.current) {
        permissionRequested.current = false;
        permission = await Location.requestForegroundPermissionsAsync();
      }
      if (!active) return;
      if (!permission.granted) { setPosition(null); setPoints([]); setLoaded(false); setError('Localização não autorizada. Você pode continuar usando a busca por cidade.'); return; }
      if (!await Location.hasServicesEnabledAsync()) { if (active) setError('Ative a localização do aparelho e tente novamente.'); return; }
      subscription = await Location.watchPositionAsync({ accuracy: Location.Accuracy.Balanced, distanceInterval: 20, timeInterval: 10000 }, value => {
        if (active) setPosition(value);
      }, () => { if (active) setError('Não foi possível acompanhar a localização. Tente novamente.'); });
      if (!active) subscription.remove();
    })().catch(() => { if (active) { setCheckingPermission(false); setError('Não foi possível acessar sua localização. Tente novamente.'); } });
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
  useEffect(() => { onNearby?.(loaded && !error ? points : null); }, [loaded, error, points, onNearby]);
  // Geocode a coarse position rather than every GPS tick.
  const cityLatitude = position ? Math.round(position.coords.latitude * 100) / 100 : null;
  const cityLongitude = position ? Math.round(position.coords.longitude * 100) / 100 : null;
  useEffect(() => {
    if (cityLatitude === null || cityLongitude === null || !onCity) return;
    let active = true;
    Location.reverseGeocodeAsync({ latitude: cityLatitude, longitude: cityLongitude }).then(addresses => {
      const city = addresses[0]?.city || addresses[0]?.subregion;
      if (active && city) { onCity(city); setResolvedCity({ key: `${cityLatitude}:${cityLongitude}`, name: city }); }
    }).catch(() => { /* Manual city search stays available. */ });
    return () => { active = false; };
  }, [cityLatitude, cityLongitude, onCity]);
  useFocusEffect(useCallback(() => {
    const valid = position && !error && resolvedCity?.key === cityLatitude + ':' + cityLongitude;
    onContext?.(valid ? { latitude: position.coords.latitude, longitude: position.coords.longitude, timestamp: position.timestamp, cidade: resolvedCity.name } : null);
    return () => onContext?.(null);
  }, [position, error, resolvedCity, cityLatitude, cityLongitude, onContext]));
  const map = useRef<MapView | null>(null);
  return <View style={{ gap: 12 }}><Text style={s.label}>Perto de você</Text>
    {demo ? <Text style={s.subtitle}>Os exemplos fictícios não possuem localização real.</Text> : <>
      {checkingPermission && <Text style={s.small}>Verificando localização…</Text>}
      {!enabled && !checkingPermission && <><Text style={s.subtitle}>Ative para ver sua posição e rolês publicados em até 5 km. Sua posição será usada na consulta de proximidade.</Text><Button title="Ativar minha localização" onPress={() => { permissionRequested.current = true; setError(''); setEnabled(1); }} /></>}
      {enabled > 0 && !position && !error && <Text style={s.subtitle}>Aguardando a localização do aparelho…</Text>}
      <Message text={error} />{!!error && <Button title="Tentar localização novamente" secondary onPress={() => { permissionRequested.current = true; setError(''); setEnabled(v => v + 1); }} />}
      {position && <><View style={{ height: 280, width: '100%', borderRadius: 20, overflow: 'hidden' }}><MapView key={mapAttempt} ref={map} provider={Platform.OS === 'android' ? PROVIDER_GOOGLE : undefined} style={{ width: '100%', height: '100%' }} mapType="standard" customMapStyle={nearbyMapStyle} moveOnMarkerPress={false} userInterfaceStyle="light" zoomControlEnabled onMapLoaded={() => { setMapLoaded(true); setMapDelayed(false); }} initialRegion={{ latitude: position.coords.latitude, longitude: position.coords.longitude, latitudeDelta: NEIGHBORHOOD_DELTA, longitudeDelta: NEIGHBORHOOD_DELTA }}>
        <Marker coordinate={{ latitude: position.coords.latitude, longitude: position.coords.longitude }} title="Você está aqui" pinColor="#397FF5" />{locations.map(location => <Marker key={location.key} coordinate={{ latitude: location.latitude, longitude: location.longitude }} title={location.points.length > 1 ? `${location.points.length} rolês neste ponto` : location.points[0].nome} onPress={() => setSelectedLocation(location.key)} />)}
      </MapView></View><View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}><View style={{ flex: 1, minWidth: 120 }}><Button title="Minha posição" secondary onPress={() => map.current?.animateToRegion({ latitude: position.coords.latitude, longitude: position.coords.longitude, latitudeDelta: NEIGHBORHOOD_DELTA, longitudeDelta: NEIGHBORHOOD_DELTA }, 350)} /></View>
        {points.length > 0 && <View style={{ flex: 1, minWidth: 120 }}><Button title="Ver rolês" secondary onPress={() => {
          map.current?.fitToCoordinates([
            { latitude: position.coords.latitude, longitude: position.coords.longitude },
            ...points.map(point => ({ latitude: point.latitude, longitude: point.longitude })),
          ], { edgePadding: { top: 32, right: 24, bottom: 24, left: 24 }, animated: true });
        }} /></View>}</View>
        <Disclosure title="Como usar o mapa"><Text style={s.small}>Azul: você. Vermelho: rolês próximos. Toque em um marcador vermelho para conhecer as opções naquele ponto. Busca em até 5 km.</Text></Disclosure>
        {!mapLoaded && <Text style={s.small}>{mapDelayed ? 'O mapa está demorando. Você pode explorar os cartões abaixo.' : 'Carregando mapa…'}</Text>}
        {mapDelayed && <Button title="Recarregar mapa" secondary onPress={() => { setMapLoaded(false); setMapDelayed(false); setMapAttempt(value => value + 1); }} />}
        {loaded && points.length === 0 && <Text style={s.small}>Nenhum rolê cadastrado em até 5 km.</Text>}
      </>}
    </>}
    <Modal visible={!!selected} animationType="slide" onRequestClose={() => setSelectedLocation(null)}>
      <SafeAreaView style={s.root}>
        <BackArrow onPress={() => setSelectedLocation(null)} />
        <View style={{ padding: 20, gap: 12 }}>
          <Text accessibilityRole="header" style={s.title}>{selected && selected.points.length > 1 ? 'Rolês neste ponto' : 'Conheça este rolê'}</Text>
        </View>
        <ScrollView style={{ flex: 1 }} contentContainerStyle={{ padding: 20, paddingTop: 0, gap: 16 }}>
          {selected?.points.map(point => <View key={point.id} style={[s.card, { flex: 0 }]}><RoleIllustration name={point.nome} category={point.categoria} /><View style={s.cardBody}>
            <Text style={s.cardTitle}>{point.nome}</Text>
            {!!point.categoria && <Text style={s.text}>{point.categoria}</Text>}
            <Text style={s.small}>{point.distanciaKm.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} km de você · em linha reta</Text>
            <Button title={`Ver detalhes de ${point.nome}`} onPress={() => { setSelectedLocation(null); onSelect(point.id); }} />
          </View></View>)}
        </ScrollView>
        <BottomNavigation beforeNavigate={() => setSelectedLocation(null)} />
      </SafeAreaView>
    </Modal>
  </View>;
}
