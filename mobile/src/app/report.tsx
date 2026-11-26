import { Redirect, useLocalSearchParams } from 'expo-router';
import { useAuth } from '../AuthContext';
import { ReportScreen } from '../Community';
export default function Screen() { const { session } = useAuth(); const { tipo, alvo } = useLocalSearchParams<{ tipo: string; alvo: string }>(); return session ? <ReportScreen key={`${session.token}:${tipo}:${alvo}`} token={session.token} type={typeof tipo === 'string' ? tipo : ''} target={typeof alvo === 'string' ? alvo : ''} /> : <Redirect href="/auth" />; }
