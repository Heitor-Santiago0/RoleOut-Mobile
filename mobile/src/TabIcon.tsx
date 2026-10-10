import Svg, { Circle, Path } from 'react-native-svg';
import type { ColorValue } from 'react-native';
import { colors } from './theme';

export function TabIcon({ kind, color }: { kind: 'create' | 'explore' | 'account'; color: ColorValue }) {
  return <Svg width={32} height={32} viewBox="0 0 48 48" accessible={false}>
    {kind === 'create' && <Path d="M24 9v30M9 24h30" stroke={color} strokeWidth={4} strokeLinecap="round" />}
    {kind === 'explore' && <>
      <Path d="M24 2C14 2 7 9 7 19c0 12 17 28 17 28s17-16 17-28C41 9 34 2 24 2Z" fill={color} />
      <Circle cx={24} cy={19} r={7} fill={colors.card} />
    </>}
    {kind === 'account' && <>
      <Circle cx={24} cy={15} r={9} fill={color} />
      <Path d="M7 42v-4c0-8 7-13 17-13s17 5 17 13v4Z" fill={color} />
    </>}
  </Svg>;
}
