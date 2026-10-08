import { type TextInputProps, ActivityIndicator, Pressable, Text, TextInput, View } from 'react-native';
import { styles as s } from './theme';
export function Button({ title, onPress, busy = false, secondary = false, disabled = false }: { title: string; onPress: () => void; busy?: boolean; secondary?: boolean; disabled?: boolean }) {
  return <Pressable accessibilityRole="button" accessibilityLabel={title} accessibilityState={{ disabled: busy || disabled }} disabled={busy || disabled} onPress={onPress} style={[s.button, secondary && s.secondary, (busy || disabled) && { opacity: 0.6 }]}>{busy ? <ActivityIndicator color="white" /> : <Text style={s.buttonText}>{title}</Text>}</Pressable>;
}
export function Field({ label, value, setValue, password = false, email = false, placeholder, keyboardType }: { label: string; value: string; setValue: (v: string) => void; password?: boolean; email?: boolean; placeholder?: string; keyboardType?: TextInputProps['keyboardType'] }) {
  return <View><Text style={s.label}>{label}</Text><TextInput accessibilityLabel={label} value={value} onChangeText={setValue} style={s.field} placeholder={placeholder ?? label} placeholderTextColor="#667085" secureTextEntry={password} autoCapitalize={email || password ? 'none' : 'sentences'} autoCorrect={!email && !password} keyboardType={keyboardType ?? (email ? 'email-address' : 'default')} textContentType={password ? 'password' : email ? 'emailAddress' : 'none'} /></View>;
}
export function Message({ text }: { text: string }) { return text ? <Text accessibilityRole="alert" style={s.error}>{text}</Text> : null; }
export function DemoBanner() { return <View style={s.banner}><Text style={s.bannerText}>Demonstração • Dados fictícios</Text></View>; }
export const price = (value: number) => value === 0 ? 'Gratuito' : value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
