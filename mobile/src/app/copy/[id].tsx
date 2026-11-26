import { Redirect, useLocalSearchParams } from 'expo-router';
import { useAuth } from '../../AuthContext';
import { CopyItinerary } from '../../CopyItineraryScreen';
export default function Screen() { const { session } = useAuth(); const { id } = useLocalSearchParams<{ id: string }>(); return session ? <CopyItinerary key={`${session.token}:${id}`} token={session.token} id={typeof id === 'string' ? id : ''} /> : <Redirect href="/auth" />; }
