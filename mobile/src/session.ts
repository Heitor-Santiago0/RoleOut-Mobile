import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';
const key = 'roleout.session';
let temporary: string | null = null;
export async function readToken() {
  return Platform.OS === 'web' ? temporary : SecureStore.getItemAsync(key);
}
export async function saveToken(token: string) {
  if (Platform.OS === 'web') temporary = token;
  else await SecureStore.setItemAsync(key, token);
}
export async function clearToken() {
  if (Platform.OS === 'web') temporary = null;
  else await SecureStore.deleteItemAsync(key);
}
