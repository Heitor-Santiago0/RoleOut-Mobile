import { Redirect } from 'expo-router';
import { useAuth } from '../AuthContext';
import { CommunityFeed } from '../Community';
export default function Screen() { const { session } = useAuth(); return session ? <CommunityFeed key={session.token} token={session.token} /> : <Redirect href="/auth" />; }
