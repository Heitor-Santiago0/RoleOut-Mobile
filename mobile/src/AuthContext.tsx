import { createContext, useContext, useEffect, useState, useCallback, type ReactNode } from 'react';
import { ActivityIndicator, ScrollView, Text, View } from 'react-native';
import { apiUrl, ApiError, request, type Account, type Session } from './api';
import { clearToken, readToken } from './session';
import { colors, styles as s } from './theme';
import { Button } from './ui';
import { OfflineItinerariesScreen } from './OfflineItinerariesScreen';
type AuthState = { session: Session | null; demo: boolean; message: string; setMessage: (message: string) => void; login: (session: Session) => void; updateAccount: (token: string, account: Account) => void; startDemo: () => void; exit: () => Promise<void>; expire: () => Promise<void> };
const Context = createContext<AuthState | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null); const [demo, setDemo] = useState(false);
  const [message, setMessage] = useState(''); const [loading, setLoading] = useState(true);
  const [restoreError, setRestoreError] = useState('');
  const [restoreAttempt, setRestoreAttempt] = useState(0);
  const [showOffline, setShowOffline] = useState(false);
  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const token = await readToken();
        if (token && apiUrl) {
          const account = await request<Account>('/auth/me', { token });
          if (active) setSession({ token, conta: account, expiraEm: '' });
        }
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) {
          await clearToken().catch(() => {});
          if (active) { setShowOffline(false); setMessage('Sua sessão expirou. Entre novamente.'); }
        } else if (active) {
          // A network failure must not discard a previously saved login.
          setRestoreError(e instanceof Error ? e.message : 'Não foi possível restaurar sua sessão.');
        }
      } finally { if (active) setLoading(false); }
    })();
    return () => { active = false; };
  }, [restoreAttempt]);
  async function exit() {
    if (session) {
      try { await request('/auth/logout', { method: 'POST', token: session.token }); }
      catch (e) { if (!(e instanceof ApiError && e.status === 401)) throw e; }
    }
    await clearToken(); setSession(null); setDemo(false); setMessage('');
  }
  const expire = useCallback(async () => {
    await clearToken().catch(() => {}); setSession(null); setDemo(false); setMessage('Sua sessão expirou. Entre novamente.');
  }, []);
  const updateAccount = useCallback((token: string, account: Account) => {
    setSession(current => current?.token === token && current.conta.id === account.id ? { ...current, conta: account } : current);
  }, []);
  if (showOffline) return <OfflineItinerariesScreen onBack={() => setShowOffline(false)} />;
  if (loading) return <View style={[s.root, s.content, { justifyContent: 'center', alignItems: 'center' }]}>
    <ActivityIndicator color={colors.blue} />
    <Text style={s.text}>Conectando ao RolêOut…</Text>
    <Text style={s.subtitle}>Estamos verificando seu acesso.</Text>
    <Button title="Consultar roteiros offline" secondary onPress={() => setShowOffline(true)} />
  </View>;
  if (restoreError) return <ScrollView style={s.root} contentContainerStyle={[s.content, { justifyContent: 'center' }]}>
    <Text accessibilityRole="header" style={s.title}>Não conseguimos abrir sua conta</Text>
    <Text accessibilityRole="alert" style={s.subtitle}>{restoreError}</Text>
    <Text style={s.text}>Confira a conexão e tente novamente. Você não precisa digitar sua senha de novo se sua sessão ainda estiver válida.</Text>
    <Button title="Tentar novamente" onPress={() => {
      setLoading(true); setRestoreError(''); setRestoreAttempt(value => value + 1);
    }} />
    <Button title="Consultar roteiros offline" secondary onPress={() => setShowOffline(true)} />
    <Button title="Ir para o login" secondary onPress={() => {
      setRestoreError(''); setMessage('Entre com sua conta para continuar.');
    }} />
  </ScrollView>;
  return <Context.Provider value={{ session, demo, message, setMessage, exit, expire, updateAccount,
    login: value => { setSession(value); setDemo(false); setMessage(''); },
    startDemo: () => { if (__DEV__) { setSession(null); setDemo(true); setMessage(''); } },
  }}>{children}</Context.Provider>;
}
export function useAuth() {
  const value = useContext(Context);
  if (!value) throw new Error('Missing AuthProvider.');
  return value;
}
