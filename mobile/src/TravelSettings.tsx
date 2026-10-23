import { useCallback, useRef, useState } from 'react';
import { AppState, Linking, Text, View } from 'react-native';
import { useFocusEffect } from 'expo-router';
import * as Location from 'expo-location';
import { serializeTravel, stopTravel, travelManager, travelSession, travelTaskName } from './travelControl';
import { startTravel } from './startTravel';
import { Button, Message } from './ui';
import { styles as s } from './theme';

export function TravelSettings({ token, onChanged }: { token: string; onChanged?: () => void }) {
  const generation=useRef(0);
  const [enabled, setEnabled] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState('');
  useFocusEffect(useCallback(() => {
    let active = true; generation.current++;
    const refresh = async () => { try { const value = await travelSession() === token && !!travelManager() && await Location.hasStartedLocationUpdatesAsync(travelTaskName); if (active) setEnabled(value); } catch { if (active) setEnabled(false); } };
    void refresh();
    const listener = AppState.addEventListener('change', state => { if (state === 'active') void refresh(); });
    return () => { active = false; generation.current++; listener.remove(); };
  }, [token]));
  async function toggle() {
    const version=generation.current;
    setBusy(true); setError('');
    try {
      if (enabled) { await stopTravel(); setEnabled(false); return; }
      await serializeTravel(async () => {
        const started=await startTravel(token,()=>generation.current===version);
        if(generation.current===version)setEnabled(started);
      });
    } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível atualizar os avisos.'); }
    finally { onChanged?.(); setBusy(false); }
  }
  return <View style={{ gap: 10 }}>
    <Text style={s.text}>Receba sugestões em outras cidades quando o app estiver em segundo plano.</Text>
    <Text style={s.small}>Ao ativar, você autoriza o uso da localização mesmo fora do app. O Android mostrará um aviso permanente. Usamos a posição para consultar opções próximas, sem salvar coordenadas ou histórico de viagens. A frequência é a mesma das sugestões automáticas.</Text>
    <Text style={s.small}>Na próxima tela do Android, escolha Permitir o tempo todo. Você pode desligar aqui a qualquer momento. Precisa de internet e do servidor disponível; pode consumir bateria. Forçar o fechamento ou reiniciar o celular pode interromper os avisos.</Text>
    <Text style={s.label}>{enabled ? 'Ativado neste celular e nesta sessão' : 'Desativado neste celular'}</Text>
    <Message text={error} />
    <Button title={enabled ? 'Desativar avisos em viagens' : 'Autorizar avisos em viagens'} busy={busy} onPress={() => void toggle()} />
    <Button title="Permissões no Android" secondary onPress={() => { void Linking.openSettings().catch(() => setError('Abra as configurações do Android para revisar as permissões.')); }} />
  </View>;
}
