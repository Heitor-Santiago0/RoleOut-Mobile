import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
import { createOfflineStore } from './offlineStore';

const memory = new Map<string, string>();
const storage = Platform.OS === 'web' ? {
  get: async (key: string) => memory.get(key) ?? null,
  set: async (key: string, value: string) => { memory.set(key, value); },
  remove: async (key: string) => { memory.delete(key); },
} : {
  get: SecureStore.getItemAsync,
  set: SecureStore.setItemAsync,
  remove: SecureStore.deleteItemAsync,
};
export const offlineItineraries = createOfflineStore(storage, async () => Platform.OS === 'web' ? null : SecureStore.getItemAsync('roleout.session'));
