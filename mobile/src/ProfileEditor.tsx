import { useEffect, useRef, useState } from 'react';
import { Text, View } from 'react-native';
import { useAuth } from './AuthContext';
import { ApiError, request, type Account, type Session } from './api';
import { Button, Field, Message } from './ui';
import { styles as s } from './theme';

export function ProfileEditor({ session }: { session: Session }) {
  const { updateAccount, expire } = useAuth();
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(session.conta.nome);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);
  const active = useRef(true);
  const saving = useRef(false);
  useEffect(() => { active.current = true; return () => { active.current = false; }; }, []);

  async function save() {
    if (saving.current) return;
    const nome = name.trim();
    if (!nome || nome.length > 200) { setError('Informe seu nome com até 200 caracteres.'); return; }
    saving.current = true; setBusy(true); setError('');
    try {
      const account = await request<Account>('/auth/me', { method: 'PUT', token: session.token, body: { nome } });
      if (!active.current) return;
      updateAccount(session.token, account); setEditing(false); setSaved(true);
    } catch (e) {
      if (!active.current) return;
      if (e instanceof ApiError && e.status === 401) await expire();
      else setError(e instanceof Error ? e.message : 'Não foi possível salvar seu nome.');
    } finally { saving.current = false; if (active.current) setBusy(false); }
  }
  return <View>{editing ? <>
    <Field label="Seu nome" value={name} setValue={setName} />
    <Message text={error} />
    <Button title="Salvar nome" onPress={save} busy={busy} />
    <Button title="Cancelar" secondary disabled={busy} onPress={() => { setEditing(false); setError(''); }} />
  </> : <>
    {saved && <Text accessibilityRole="alert" style={s.text}>Nome atualizado.</Text>}
    <Button title="Editar nome" secondary onPress={() => { setName(session.conta.nome); setError(''); setSaved(false); setEditing(true); }} />
  </>}</View>;
}
