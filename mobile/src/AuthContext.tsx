import { createContext, useContext, useEffect, useState, useCallback, type ReactNode } from 'react';
import { ActivityIndicator, View } from 'react-native';
import { apiUrl, ApiError, request, type Account, type Session } from './api';
import { clearToken, readToken } from './session';
import { colors, styles as s } from './theme';
type AuthState = { session: Session | null; demo: boolean; message: string; setMessage: (message: string) => void; login: (session: Session) => void; startDemo: () => void; exit: () => Promise<void>; expire: () => Promise<void> };
const Context = createContext<AuthState | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null); const [demo, setDemo] = useState(false);
  const [message, setMessage] = useState(''); const [loading, setLoading] = useState(true);
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
        if (e instanceof ApiError && e.status === 401) await clearToken().catch(() => {});
        if (active) setMessage(e instanceof Error ? e.message : 'Não foi possível restaurar sua sessão.');
      } finally { if (active) setLoading(false); }
    })();
    return () => { active = false; };
  }, []);
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
  if (loading) return <View style={s.root}><ActivityIndicator color={colors.blue} /></View>;
  return <Context.Provider value={{ session, demo, message, setMessage, exit, expire,
    login: value => { setSession(value); setDemo(false); setMessage(''); },
    startDemo: () => { if (__DEV__) { setSession(null); setDemo(true); setMessage(''); } },
  }}>{children}</Context.Provider>;
}
export function useAuth() {
  const value = useContext(Context);
  if (!value) throw new Error('Missing AuthProvider.');
  return value;
}
