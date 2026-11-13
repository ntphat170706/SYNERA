import React from 'react';
import { Platform, Text, useWindowDimensions, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { SessionProvider, useSession } from './src/auth/session';
import { Navigation, navigationRef } from './src/navigation';
import { LiveNotice } from './src/components/LiveNotice';
import { Login } from './src/screens/Login';
import { State } from './src/components/ui';
import { useColors } from './src/theme';
function Content() {
  const { user, loading } = useSession();
  return loading ? (
    <State loading error="" retry={() => {}} />
  ) : user ? (
    <View style={{ flex: 1 }}>
      <Navigation />
      <LiveNotice
        key={user.id}
        userId={user.id}
        onOpen={() => {
          if (navigationRef.isReady()) navigationRef.navigate('Inbox');
        }}
      />
    </View>
  ) : (
    <Login />
  );
}
export default function App() {
  const { width, height } = useWindowDimensions(),
    c = useColors();
  const framed = Platform.OS === 'web' && width > 680 && height > 600;
  return (
    <SafeAreaProvider>
      <SessionProvider>
        <StatusBar style="dark" />
        {framed ? (
          <View
            style={{
              flex: 1,
              alignItems: 'center',
              justifyContent: 'center',
              gap: 12,
              backgroundColor: c.mutedSurface,
              padding: 16,
            }}
          >
            <Text style={{ fontSize: 14, color: c.muted }}>SYNERA Sales · Preview iPhone</Text>
            <View
              style={{
                width: 414,
                height: Math.min(860, height - 78),
                backgroundColor: c.surface,
                borderWidth: 7,
                borderColor: '#253342',
                borderRadius: 38,
                overflow: 'hidden',
                boxShadow: '0 20px 60px rgba(20,36,52,0.16)',
              }}
            >
              <Content />
            </View>
          </View>
        ) : (
          <Content />
        )}
      </SessionProvider>
    </SafeAreaProvider>
  );
}
