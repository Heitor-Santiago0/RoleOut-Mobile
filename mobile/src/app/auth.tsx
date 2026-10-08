import { Redirect, router } from 'expo-router';
import { useAuth } from '../AuthContext';
import { AuthScreen } from '../AuthScreen';
export default function AuthRoute() {
  const auth = useAuth();
  if (auth.session || auth.demo) return <Redirect href="/explore" />;
  return <AuthScreen message={auth.message} onLogin={session => { auth.login(session); router.replace('/explore'); }} onDemo={() => { auth.startDemo(); router.replace('/explore'); }} />;
}
