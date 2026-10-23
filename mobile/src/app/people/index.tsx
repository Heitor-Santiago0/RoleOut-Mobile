import { Redirect } from 'expo-router';
import { useAuth } from '../../AuthContext';
import { PublicProfiles } from '../../PublicProfiles';
export default function PeopleRoute() {
  const { session } = useAuth();
  return session ? <PublicProfiles key={session.token} token={session.token} /> : <Redirect href="/auth" />;
}
