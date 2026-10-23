import { TravelObserver } from '../TravelObserver';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { AuthProvider } from '../AuthContext';
import { RoutingPlanProvider } from '../RoutingPlanContext';
import { styles as s } from '../theme';
import { View } from 'react-native';
import { BackArrow, BottomNavigation } from '../AppNavigation';
import { useCallback, useState } from 'react';
import { StartupSplash } from '../StartupSplash';
import { PushObserver } from '../PushObserver';
export default function Layout() {
  const [opening, setOpening] = useState(true);
  const finishOpening = useCallback(() => setOpening(false), []);
  return <SafeAreaProvider><View style={{ flex: 1 }}><SafeAreaView style={s.root} accessibilityElementsHidden={opening} importantForAccessibility={opening ? 'no-hide-descendants' : 'auto'}><StatusBar style="light" hidden={opening} /><AuthProvider><RoutingPlanProvider>
    <PushObserver /><TravelObserver />
    <BackArrow />
    <View style={{ flex: 1 }}><Stack screenOptions={{ headerShown: false, contentStyle: s.root }} /></View>
    <BottomNavigation />
  </RoutingPlanProvider></AuthProvider></SafeAreaView>
    {opening && <StartupSplash onComplete={finishOpening} />}
  </View></SafeAreaProvider>;
}
