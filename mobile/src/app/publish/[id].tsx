import { Redirect, useLocalSearchParams } from 'expo-router';
import { useAuth } from '../../AuthContext';
import { PublicationEditor } from '../../PublicationEditor';
export default function PublishRoute() {
  const { session } = useAuth();
  const { id } = useLocalSearchParams<{ id: string }>();
  return session ? <PublicationEditor key={`${session.token}:${id}`} token={session.token} routeId={Number(id)} /> : <Redirect href="/auth" />;
}
