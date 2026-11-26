import { Redirect } from 'expo-router';
import { useAuth } from '../AuthContext';
import { ModerationScreen } from '../Community';
export default function Screen() { const { session } = useAuth(); return session ? <ModerationScreen key={session.token} token={session.token} /> : <Redirect href="/auth" />; }
