import { Redirect, useLocalSearchParams } from 'expo-router';
import { useAuth } from '../AuthContext';
import { TextsScreen } from '../Community';
export default function Screen() { const { session } = useAuth(); const { tipo, alvo } = useLocalSearchParams<{ tipo: string; alvo: string }>(); return session ? <TextsScreen key={`${session.token}:${tipo}:${alvo}`} token={session.token} type={tipo === 'AVALIACAO' ? 'AVALIACAO' : 'COMENTARIO'} target={typeof alvo === 'string' ? alvo : ''} /> : <Redirect href="/auth" />; }
