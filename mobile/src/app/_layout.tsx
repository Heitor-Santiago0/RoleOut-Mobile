import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { AuthProvider } from '../AuthContext';
import { styles as s } from '../theme';
export default function Layout() {
  return <SafeAreaProvider><SafeAreaView style={s.root}><StatusBar style="light" /><AuthProvider><Stack screenOptions={{ headerShown: false, contentStyle: s.root }} /></AuthProvider></SafeAreaView></SafeAreaProvider>;
}
