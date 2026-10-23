import { useCallback, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { useFocusEffect } from 'expo-router';
import { ApiError, request } from './api';
import { useAuth } from './AuthContext';

// Public content is read again on resume; failed reads never leave a stale public profile visible.
export function useSocialResource<T>(path: string, token: string, resume = true) {
  const { expire } = useAuth();
  const [data, setData] = useState<T | null>(null), [error, setError] = useState('');
  const [retry, setRetry] = useState(0), [loading, setLoading] = useState(true);
  const generation = useRef(0);
  useFocusEffect(useCallback(() => {
    let focused = true;
    async function load() {
      const version = ++generation.current;
      setData(null); setError(''); setLoading(true);
      try {
        const result = await request<T>(path, { token });
        if (focused && generation.current === version) setData(result);
      } catch (e) {
        if (!focused || generation.current !== version) return;
        if (e instanceof ApiError && e.status === 401) void expire();
        else setError(e instanceof Error ? e.message : 'Não foi possível carregar.');
      } finally { if (focused && generation.current === version) setLoading(false); }
    }
    void load();
    const listener = resume ? AppState.addEventListener('change', state => { if (state === 'active') void load(); }) : null;
    return () => { focused = false; generation.current++; listener?.remove(); setData(null); };
  // retry explicitly reloads the server state.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [path, token, resume, retry, expire]));
  return { data, error, loading, reload: () => setRetry(value => value + 1), generation };
}
