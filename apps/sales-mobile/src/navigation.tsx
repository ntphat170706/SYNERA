import React from 'react';
import {
  DefaultTheme,
  NavigationContainer,
  createNavigationContainerRef,
  type NavigatorScreenParams,
} from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import Ionicons from '@expo/vector-icons/Ionicons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Account, Drafts, OrderDetail, Orders, StoreDetail, Stores } from './screens/FieldScreens';
import { Today } from './screens/Today';
import { OrderComposer } from './screens/OrderComposer';
import { Activity } from './screens/Activity';
import { OrderAssistant, AiStorePicker } from './screens/OrderAssistant';
import { DisplayAudit } from './screens/DisplayAudit';
import { Inbox } from './screens/Inbox';
import { ReplacementRequest } from './screens/ReplacementRequest';
import { useColors } from './theme';
export type RootStack = {
  Inbox: undefined;
  ReplacementRequest: { orderId: number };
  Main: NavigatorScreenParams<Sections> | undefined;
  Store: { id: number; name: string; section?: 'prepare' | 'actions' | 'history' };
  Order: { id: number; name: string };
  Compose: {
    storeId: number;
    storeName: string;
    reorderId?: number;
    initialLines?: import('./domain/contracts').CartLine[];
  };
  OrderAssistant: { storeId: number; name: string };
  DisplayAudit: { storeId: number; name: string };
  AiStorePicker: { kind: 'order' | 'display' };
  Activity: {
    kind: 'checkin' | 'checkout' | 'survey' | 'interaction' | 'ticket' | 'appointment' | 'report';
    storeId: number;
    name: string;
    visitId?: number;
  };
  Drafts: undefined;
};
type Sections = { Today: undefined; Stores: undefined; Orders: undefined; Account: undefined };
const Stack = createNativeStackNavigator<RootStack>(),
  Tabs = createBottomTabNavigator<Sections>();
const names = { Today: 'Hôm nay', Stores: 'Điểm bán', Orders: 'Đơn hàng', Account: 'Tài khoản' };
const icons = {
  Today: 'calendar-outline',
  Stores: 'storefront-outline',
  Orders: 'receipt-outline',
  Account: 'person-outline',
} as const;
function Main() {
  const c = useColors();
  const insets = useSafeAreaInsets();
  return (
    <Tabs.Navigator
      screenOptions={({ route }) => ({
        title: names[route.name],
        headerShadowVisible: false,
        headerTitleAlign: 'center',
        headerTitleStyle: { fontSize: 18, fontWeight: '600' },
        tabBarActiveTintColor: c.primary,
        tabBarInactiveTintColor: c.muted,
        tabBarStyle: {
          backgroundColor: c.surface,
          borderTopColor: c.border,
          height: 70 + insets.bottom,
          paddingTop: 6,
          paddingBottom: Math.max(insets.bottom, 8),
        },
        tabBarAllowFontScaling: true,
        tabBarLabelStyle: { fontSize: 12, lineHeight: 16 },
        tabBarIcon: ({ color, size }) => (
          <Ionicons name={icons[route.name]} size={size} color={color} />
        ),
      })}
    >
      <Tabs.Screen name="Today" component={Today} options={{ headerShown: false }} />
      <Tabs.Screen name="Stores" component={Stores} />
      <Tabs.Screen name="Orders" component={Orders} />
      <Tabs.Screen name="Account" component={Account} />
    </Tabs.Navigator>
  );
}
export const navigationRef = createNavigationContainerRef<RootStack>();
export function Navigation() {
  const c = useColors(),
    base = DefaultTheme;
  return (
    <NavigationContainer
      ref={navigationRef}
      theme={{
        ...base,
        colors: {
          ...base.colors,
          primary: c.primary,
          background: c.canvas,
          card: c.surface,
          text: c.text,
          border: c.border,
        },
      }}
    >
      <Stack.Navigator
        screenOptions={{ headerShadowVisible: false, headerBackButtonDisplayMode: 'minimal' }}
      >
        <Stack.Screen name="Main" component={Main} options={{ headerShown: false }} />
        <Stack.Screen
          name="Store"
          component={StoreDetail}
          options={({ route }) => ({ title: route.params.name })}
        />
        <Stack.Screen
          name="Order"
          component={OrderDetail}
          options={({ route }) => ({ title: route.params.name })}
        />
        <Stack.Screen
          name="Compose"
          component={OrderComposer}
          options={{ title: 'Tạo đơn hàng' }}
        />
        <Stack.Screen
          name="Activity"
          component={Activity}
          options={{ title: 'Hoạt động tại điểm' }}
        />
        <Stack.Screen name="Drafts" component={Drafts} options={{ title: 'Đơn chờ gửi' }} />
        <Stack.Screen name="Inbox" component={Inbox} options={{ title: 'Thông báo & lời hẹn' }} />
        <Stack.Screen
          name="ReplacementRequest"
          component={ReplacementRequest}
          options={{ title: 'Đề xuất giao bù' }}
        />
        <Stack.Screen
          name="OrderAssistant"
          component={OrderAssistant}
          options={{ title: 'AI hỗ trợ lên đơn' }}
        />
        <Stack.Screen
          name="DisplayAudit"
          component={DisplayAudit}
          options={{ title: 'Kiểm tra trưng bày' }}
        />
        <Stack.Screen
          name="AiStorePicker"
          component={AiStorePicker}
          options={{ title: 'Chọn điểm bán' }}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
