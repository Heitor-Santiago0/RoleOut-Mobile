import { requireOptionalNativeModule } from 'expo';
import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import * as Location from 'expo-location';

export const travelTaskName = 'roleout-travel-suggestions-v1';
const key = 'roleout.travel.session';
const checkedKey = 'roleout.travel.checked';
let queue: Promise<unknown> = Promise.resolve();
export function serializeTravel<T>(work: () => Promise<T>): Promise<T> {
  const next = queue.catch(() => {}).then(work); queue = next; return next;
}
export function travelManager() {
  if (Platform.OS !== 'android' || !requireOptionalNativeModule('ExpoTaskManager')) return null;
  // Existing APKs remain usable until the new native build is installed.
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  return require('expo-task-manager') as typeof import('expo-task-manager');
}
export async function travelSession() { return Platform.OS === 'android' ? SecureStore.getItemAsync(key) : null; }
export async function consentTravel(token: string) { await SecureStore.setItemAsync(key, token); }
export async function stopTravel() {
  if (Platform.OS !== 'android') return;
  // Revoke first, so an already running evaluation cannot display a notification.
  await SecureStore.deleteItemAsync(key);
  await SecureStore.deleteItemAsync(checkedKey);
  if (travelManager() && await Location.hasStartedLocationUpdatesAsync(travelTaskName)) await Location.stopLocationUpdatesAsync(travelTaskName);
}
export async function claimTravelCheck(now: number) {
  const previous = Number(await SecureStore.getItemAsync(checkedKey));
  if (previous > 0 && now >= previous && now - previous < 15 * 60000) return false;
  await SecureStore.setItemAsync(checkedKey, String(now));
  return true;
}
