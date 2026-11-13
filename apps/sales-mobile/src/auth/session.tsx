import React, { createContext, useContext, useEffect, useState } from 'react';
import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import Constants from 'expo-constants';
import { createSalesClient, type TokenVault } from '../api/client';
import type { SalesUser } from '../domain/contracts';
let webToken: string | null = null;
const vault: TokenVault = {
  read: () =>
    Platform.OS === 'web'
      ? Promise.resolve(webToken)
      : SecureStore.getItemAsync('synera.sales.refresh'),
  write: async (token) => {
    if (Platform.OS === 'web') {
      webToken = token;
      return;
    }
    if (token)
      await SecureStore.setItemAsync('synera.sales.refresh', token, {
        keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
      });
    else await SecureStore.deleteItemAsync('synera.sales.refresh');
  },
};
export const apiUrl =
  process.env.EXPO_PUBLIC_API_URL ||
  (Platform.OS === 'web'
    ? ''
    : Constants.expoConfig?.hostUri
      ? `http://${Constants.expoConfig.hostUri}`
      : 'http://127.0.0.1:8080');
export const salesClient = createSalesClient(apiUrl, vault);
const Context = createContext<{ user: SalesUser | null; loading: boolean }>({
  user: null,
  loading: true,
});
export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<SalesUser | null>(null),
    [loading, setLoading] = useState(true);
  useEffect(() => {
    const stop = salesClient.subscribe(setUser);
    salesClient
      .restore()
      .catch(() => {})
      .finally(() => setLoading(false));
    return stop;
  }, []);
  return <Context.Provider value={{ user, loading }}>{children}</Context.Provider>;
}
export const useSession = () => useContext(Context);
