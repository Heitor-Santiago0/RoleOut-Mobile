import { Redirect, useLocalSearchParams } from 'expo-router';
import { useAuth } from '../../AuthContext';
import { PublicProfileScreen } from '../../PublicProfiles';
export default function PersonRoute() {
  const { session } = useAuth();
  const { id } = useLocalSearchParams<{ id: string }>();
  return session ? <PublicProfileScreen key={`${session.token}:${id}`} token={session.token} id={typeof id === 'string' ? id : ''} /> : <Redirect href="/auth" />;
}
