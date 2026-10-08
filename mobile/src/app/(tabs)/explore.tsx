import { router } from 'expo-router';
import { useAuth } from '../../AuthContext';
import { ExploreScreen } from '../../ExploreScreen';
export default function ExploreRoute() {
  const { demo, session } = useAuth();
  return <ExploreScreen demo={demo} token={session?.token} onSelect={role => router.push(`/roles/${role.id}`)} />;
}
