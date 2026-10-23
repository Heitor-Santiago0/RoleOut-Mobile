import { Redirect } from 'expo-router';
import { useAuth } from '../AuthContext';
import { SocialProfileEditor } from '../SocialProfileEditor';
export default function SocialProfileRoute() {
  const { session } = useAuth();
  return session ? <SocialProfileEditor key={session.token} token={session.token} /> : <Redirect href="/auth" />;
}
