import { Redirect, useLocalSearchParams } from 'expo-router';
import { useAuth } from '../../AuthContext';
import { PublicPublicationScreen } from '../../Publications';
export default function PublicationRoute() {
  const { session } = useAuth();
  const { id } = useLocalSearchParams<{ id: string }>();
  return session ? <PublicPublicationScreen key={`${session.token}:${id}`} token={session.token} id={typeof id === 'string' ? id : ''} /> : <Redirect href="/auth" />;
}
