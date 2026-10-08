import { Text, View, useWindowDimensions } from 'react-native';
import Svg, { Path, Circle } from 'react-native-svg';
import { colors } from './theme';

export function Brand() {
  const { width } = useWindowDimensions();
  const fontSize = Math.min(48, Math.max(30, (width - 64) / 7.2));
  return <View accessible accessibilityLabel="RolêOut. Descubra rolês que combinam com você." style={{ alignItems: 'center', gap: 22 }}>
    <View style={{ flexDirection: 'row', alignItems: 'center' }}>
      <Text style={{ fontSize, fontWeight: '900', color: colors.text, letterSpacing: -1.5 }}>ROLÊ</Text>
      <Svg width={fontSize * 0.85} height={fontSize * 1.35} viewBox="0 0 48 64">
        <Path d="M24 1C11 1 2 10 2 23c0 16 22 39 22 39s22-23 22-39C46 10 37 1 24 1Z" fill="#D51C29" />
        <Circle cx="24" cy="23" r="11" fill={colors.background} />
      </Svg>
      <Text style={{ fontSize, fontWeight: '900', color: colors.text, letterSpacing: -1.5 }}>UT</Text>
    </View>
    <Text style={{ color: colors.muted, fontSize: 16, lineHeight: 24, textAlign: 'center', maxWidth: 270 }}>Descubra rolês que combinam com você</Text>
  </View>;
}
