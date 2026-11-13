import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  AccessibilityInfo,
  Animated,
  AppState,
  Easing,
  Linking,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useFocusEffect, useIsFocused, useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { ActionRow, Button, Icon, kit, Section, Segment, State, useRead } from '../components/ui';
import { ProfileAvatar } from '../components/ProfileAvatar';
import { useSession } from '../auth/session';
import type { RootStack } from '../navigation';
import type { Store, Visit } from '../domain/contracts';
import { compactAmount, elapsedVisit, greeting, periodRange } from '../domain/home';
import { money, useColors } from '../theme';
type Summary = {
  orderCount: number;
  orderValue: number;
  receivedSales: number;
  generatedAt: string;
  layer: 'PROVISIONAL';
  repId: number;
};
type Nav = NativeStackNavigationProp<RootStack>;
function VisitClock({ visit }: { visit: Visit }) {
  const c = useColors(),
    [now, setNow] = useState(Date.now());
  useEffect(() => {
    let timer: ReturnType<typeof setInterval> | undefined;
    const update = () => setNow(Date.now());
    const run = () => {
      if (timer) clearInterval(timer);
      update();
      if (AppState.currentState === 'active' || !AppState.currentState)
        timer = setInterval(update, 1000);
    };
    run();
    const sub = AppState.addEventListener('change', run);
    return () => {
      if (timer) clearInterval(timer);
      sub.remove();
    };
  }, [visit.id, visit.checkedInAt]);
  const value = elapsedVisit(visit.checkedInAt, now);
  return (
    <View style={s.timer}>
      <Icon name="time-outline" size={20} />
      <Text style={{ fontSize: 14, color: c.muted }}>Thời gian ghé thăm</Text>
      <Text
        testID="visit-clock"
        accessibilityLabel={value ? `Thời gian ghé thăm ${value}` : 'Chưa có thời điểm check-in'}
        style={{
          fontSize: 22,
          fontWeight: '700',
          color: c.primary,
          fontVariant: ['tabular-nums'],
          marginLeft: 'auto',
        }}
      >
        {value || '—'}
      </Text>
    </View>
  );
}
function ActiveVisit({ visit, store }: { visit: Visit; store?: Store }) {
  const c = useColors(),
    nav = useNavigation<Nav>(),
    opacity = useRef(new Animated.Value(1)).current;
  useEffect(() => {
    let active = true;
    let motion: Animated.CompositeAnimation | undefined;
    AccessibilityInfo.isReduceMotionEnabled().then((reduce) => {
      if (!active || reduce) return;
      opacity.setValue(0.6);
      motion = Animated.timing(opacity, {
        toValue: 1,
        duration: 420,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      });
      motion.start();
    });
    return () => {
      active = false;
      motion?.stop();
      opacity.setValue(1);
    };
  }, [visit.id, opacity]);
  const name = store?.name || `Điểm bán #${visit.storeId}`;
  const open = (section: 'prepare' | 'actions' | 'history' = 'actions') =>
    nav.navigate('Store', { id: visit.storeId, name, section });
  return (
    <Animated.View style={[s.activeShell, { opacity }]} testID="active-visit">
      <View style={[s.activeBand, { backgroundColor: c.primary }]}>
        <View style={kit.horizontal}>
          <View style={s.liveDot} />
          <Text style={s.whiteBold}>Đang ghé thăm</Text>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`Tiếp tục ghé ${name}`}
          onPress={() => open()}
          style={s.continue}
        >
          <Text style={{ color: '#ffffff', fontWeight: '600' }}>Tiếp tục</Text>
          <Icon name="chevron-forward" size={16} color="#ffffff" />
        </Pressable>
      </View>
      <View style={{ padding: 17, gap: 12, backgroundColor: c.routeBlue }}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`Mở điểm đang ghé ${name}`}
          onPress={() => open()}
        >
          <Text style={{ fontSize: 20, fontWeight: '700', color: c.text, lineHeight: 28 }}>
            {name}
          </Text>
          <Text style={{ fontSize: 14, lineHeight: 21, color: c.muted, marginTop: 5 }}>
            {store?.address || 'Chưa có địa chỉ'}
          </Text>
        </Pressable>
        <VisitClock visit={visit} />
        <View style={[kit.horizontal, { gap: 8 }]}>
          <View style={{ flex: 1 }}>
            <Button secondary onPress={() => open('history')}>
              Lịch sử đơn
            </Button>
          </View>
          <View style={{ flex: 1 }}>
            <Button
              onPress={() => nav.navigate('Compose', { storeId: visit.storeId, storeName: name })}
            >
              Tạo đơn
            </Button>
          </View>
        </View>
      </View>
    </Animated.View>
  );
}
export function Today() {
  const c = useColors(),
    { user } = useSession(),
    nav = useNavigation<Nav>(),
    insets = useSafeAreaInsets(),
    focused = useIsFocused();
  const [now, setNow] = useState(Date.now()),
    [period, setPeriod] = useState<'today' | 'month'>('today'),
    [filter, setFilter] = useState('all'),
    [linkError, setLinkError] = useState('');
  const range = periodRange(period, now);
  const visits = useRead<Visit[]>('/visits/today', []),
    stores = useRead<Store[]>('/stores', []),
    identity = useRead<{ id: number; name: string } | null>('/auth/me', null),
    summary = useRead<Summary | null>(
      `/sales-orders/summary?from=${range.from}&to=${range.to}`,
      null,
    );
  const reload = useCallback(() => {
    setNow(Date.now());
    visits.reload();
    stores.reload();
    summary.reload();
    identity.reload();
  }, [visits.reload, stores.reload, summary.reload, identity.reload]);
  useFocusEffect(reload);
  useEffect(() => {
    const interval = setInterval(() => setNow(Date.now()), 60000),
      sub = AppState.addEventListener('change', (state) => {
        if (state === 'active' && focused) reload();
      });
    return () => {
      clearInterval(interval);
      sub.remove();
    };
  }, [reload, focused]);
  const active = visits.data.filter((v) => v.status === 'CHECKED_IN'),
    done = visits.data.filter((v) => v.status === 'COMPLETED').length;
  const rows = visits.data.filter(
    (v) =>
      v.status !== 'CHECKED_IN' &&
      (filter === 'all' || (filter === 'done' ? v.status === 'COMPLETED' : v.status === 'PLANNED')),
  );
  const name = identity.data?.name || user?.name || 'Sales';
  const today = new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  }).format(new Date(now));
  async function directions(store: Store) {
    try {
      if (store.latitude == null || store.longitude == null)
        throw Error('Điểm bán chưa có toạ độ.');
      await Linking.openURL(`https://maps.apple.com/?daddr=${store.latitude},${store.longitude}`);
    } catch (e) {
      setLinkError(e instanceof Error ? e.message : 'Chưa mở được bản đồ.');
    }
  }
  return (
    <View style={{ flex: 1, backgroundColor: c.canvas }}>
      {focused && <StatusBar style="light" />}
      <ScrollView
        testID="today-scroll"
        style={{ flex: 1 }}
        contentContainerStyle={{ paddingBottom: 28 }}
        refreshControl={
          <RefreshControl refreshing={visits.loading} onRefresh={reload} tintColor="#ffffff" />
        }
      >
        <LinearGradient
          colors={[c.heroBlue, c.primary, c.heroMid, c.canvas]}
          locations={[0, 0.55, 0.84, 1]}
          style={{
            paddingTop: insets.top + 24,
            paddingHorizontal: 22,
            paddingBottom: 67,
            minHeight: 222 + insets.top,
          }}
        >
          <View style={[kit.between, { alignItems: 'flex-start' }]}>
            <View style={{ flex: 1, gap: 8, paddingRight: 16 }}>
              <Text style={{ fontSize: 17, lineHeight: 25, color: '#e4efff' }}>
                {greeting(now)}
              </Text>
              <Text
                accessibilityRole="header"
                style={{ fontSize: 29, fontWeight: '700', color: '#ffffff', lineHeight: 37 }}
              >
                {name}
              </Text>
              <Text style={{ fontSize: 13, color: '#e4efff', marginTop: 5 }}>{today}</Text>
            </View>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Mở tài khoản Sales"
              onPress={() => nav.navigate('Main', { screen: 'Account' })}
              style={{ borderRadius: 32, padding: 3, backgroundColor: 'rgba(255,255,255,0.3)' }}
            >
              <ProfileAvatar userId={user?.id} name={name} size={57} />
            </Pressable>
          </View>
        </LinearGradient>
        <View style={{ paddingHorizontal: 16, marginTop: -42 }}>
          <Section tone="blue">
            <View style={{ padding: 15, paddingBottom: 17, gap: 16 }}>
              <View style={[kit.between, { gap: 12 }]}>
                <Text style={{ fontSize: 15, fontWeight: '700', color: c.text }}>
                  Kết quả bán hàng
                </Text>
                <View style={{ minWidth: 175 }}>
                  <Segment
                    value={period}
                    onChange={(v) => setPeriod(v as 'today' | 'month')}
                    items={[
                      { key: 'today', label: 'Hôm nay' },
                      { key: 'month', label: 'Tháng này' },
                    ]}
                  />
                </View>
              </View>
              <View style={{ flexDirection: 'row', gap: 9 }}>
                {[
                  {
                    label: 'Đơn hàng',
                    value: summary.data ? String(summary.data.orderCount) : '—',
                    full: 'Số đơn không huỷ',
                    icon: 'receipt-outline' as const,
                  },
                  {
                    label: 'Giá trị đơn',
                    value: summary.data ? compactAmount(summary.data.orderValue) : '—',
                    full: summary.data ? money(summary.data.orderValue) : 'Chưa có dữ liệu',
                    icon: 'cart-outline' as const,
                  },
                  {
                    label: 'Doanh thu',
                    value: summary.data ? compactAmount(summary.data.receivedSales) : '—',
                    full: summary.data ? money(summary.data.receivedSales) : 'Chưa có dữ liệu',
                    icon: 'trending-up-outline' as const,
                  },
                ].map((m, i) => (
                  <View
                    key={m.label}
                    style={{
                      flex: 1,
                      minWidth: 0,
                      padding: 11,
                      borderRadius: 11,
                      gap: 8,
                      backgroundColor: i === 1 ? c.metricBlue : i === 2 ? c.routeBlue : c.surface,
                    }}
                  >
                    <Icon name={m.icon} size={20} />
                    <Text style={{ fontSize: 12, color: c.muted, lineHeight: 18 }}>{m.label}</Text>
                    <Text
                      accessibilityLabel={`${m.label}: ${m.full === 'Số đơn không huỷ' ? m.value : m.full}`}
                      style={{
                        fontSize: 20,
                        fontWeight: '700',
                        color: c.primary,
                        fontVariant: ['tabular-nums'],
                      }}
                    >
                      {m.value}
                    </Text>
                  </View>
                ))}
              </View>
              {summary.error && (
                <State loading={false} error={summary.error} retry={summary.reload} />
              )}
            </View>
          </Section>
          <Section>
            <ActionRow
              title="Thông báo & lời hẹn"
              detail="Cập nhật từ NPP, phần thiếu và việc cần báo lại khách"
              icon="notifications-outline"
              onPress={() => nav.navigate('Inbox')}
            />
          </Section>
          <View style={[kit.between, { marginTop: 3, marginBottom: 13, paddingHorizontal: 2 }]}>
            <Text style={{ fontSize: 20, fontWeight: '700', color: c.text }}>
              Lộ trình đi tuyến
            </Text>
            <Text style={{ fontSize: 13, color: c.muted }}>
              {done}/{visits.data.length} đã ghé
            </Text>
          </View>
          {active.map((v) => (
            <ActiveVisit key={v.id} visit={v} store={stores.data.find((s) => s.id === v.storeId)} />
          ))}
          <View style={{ marginBottom: 13, marginTop: active.length ? 8 : 0 }}>
            <Segment
              value={filter}
              onChange={setFilter}
              items={[
                { key: 'all', label: 'Tất cả' },
                { key: 'pending', label: 'Chưa ghé' },
                { key: 'done', label: 'Đã ghé' },
              ]}
            />
          </View>
          <State
            loading={visits.loading}
            error={visits.error || stores.error}
            retry={() => {
              visits.reload();
              stores.reload();
            }}
          />
          {Boolean(linkError) && (
            <Text accessibilityRole="alert" style={{ color: c.danger, marginBottom: 12 }}>
              {linkError}
            </Text>
          )}
          <Section>
            {rows.map((v) => {
              const store = stores.data.find((s) => s.id === v.storeId);
              if (!store) return null;
              return (
                <View
                  key={v.id}
                  style={{
                    padding: 16,
                    gap: 12,
                    borderBottomWidth: StyleSheet.hairlineWidth,
                    borderBottomColor: c.border,
                  }}
                >
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={store.name}
                    onPress={() => nav.navigate('Store', { id: store.id, name: store.name })}
                    style={[kit.horizontal, { alignItems: 'flex-start' }]}
                  >
                    <View
                      style={[
                        kit.actionIcon,
                        { backgroundColor: v.status === 'COMPLETED' ? c.routeBlue : c.accent },
                      ]}
                    >
                      <Icon
                        name={
                          v.status === 'COMPLETED'
                            ? 'checkmark-circle-outline'
                            : 'storefront-outline'
                        }
                      />
                    </View>
                    <View style={{ flex: 1, gap: 5 }}>
                      <Text
                        style={{ fontSize: 16, fontWeight: '600', color: c.text, lineHeight: 23 }}
                      >
                        {store.name}
                      </Text>
                      <Text style={{ fontSize: 13, lineHeight: 20, color: c.muted }}>
                        {store.address}
                      </Text>
                    </View>
                    <Text style={{ fontSize: 11, color: c.muted, marginTop: 4 }}>
                      {v.status === 'COMPLETED' ? 'Đã ghé' : 'Chưa ghé'}
                    </Text>
                  </Pressable>
                  <View style={{ flexDirection: 'row', gap: 10, paddingLeft: 50 }}>
                    <View style={{ flex: 1 }}>
                      <Button secondary onPress={() => void directions(store)}>
                        Chỉ đường
                      </Button>
                    </View>
                    <View style={{ flex: 1 }}>
                      <Button
                        secondary={v.status === 'COMPLETED'}
                        onPress={() =>
                          v.status === 'COMPLETED'
                            ? nav.navigate('Store', { id: store.id, name: store.name })
                            : nav.navigate('Activity', {
                                kind: 'checkin',
                                storeId: store.id,
                                name: store.name,
                                visitId: v.id,
                              })
                        }
                      >
                        {v.status === 'COMPLETED' ? 'Xem lịch sử' : 'Ghé thăm'}
                      </Button>
                    </View>
                  </View>
                </View>
              );
            })}
            {!visits.loading && !rows.length && (
              <View style={{ padding: 22, gap: 10, alignItems: 'center' }}>
                <Icon name="calendar-clear-outline" size={30} />
                <Text style={{ color: c.muted, textAlign: 'center', lineHeight: 21 }}>
                  {active.length
                    ? 'Lượt đang ghé được hiển thị ở phía trên.'
                    : 'Chưa có lượt ghé trong danh sách này.'}
                </Text>
              </View>
            )}
          </Section>
          <Section title="Trợ lý bán hàng" tone="blue">
            <ActionRow
              title="AI kiểm tra trưng bày"
              detail="Chụp ảnh, đối chiếu bộ tiêu chí"
              icon="scan-outline"
              onPress={() => nav.navigate('AiStorePicker', { kind: 'display' })}
            />
            <ActionRow
              title="AI hỗ trợ lên đơn"
              detail="Nhập lời yêu cầu, nhận giỏ hàng gợi ý"
              icon="sparkles-outline"
              onPress={() => nav.navigate('AiStorePicker', { kind: 'order' })}
            />
          </Section>
          <Section title="Công việc" tone="blue">
            <ActionRow
              icon="storefront-outline"
              title="Điểm bán của tôi"
              detail="Hồ sơ và công việc tại điểm"
              onPress={() => nav.navigate('Main', { screen: 'Stores' })}
            />
            <ActionRow
              icon="document-text-outline"
              title="Báo cáo cuối ngày"
              onPress={() =>
                nav.navigate('Activity', { kind: 'report', storeId: 0, name: 'Báo cáo cuối ngày' })
              }
            />
            <ActionRow
              icon="cloud-upload-outline"
              title="Đơn chờ gửi"
              onPress={() => nav.navigate('Drafts')}
            />
          </Section>
        </View>
      </ScrollView>
    </View>
  );
}
const s = StyleSheet.create({
  activeShell: {
    borderRadius: 15,
    overflow: 'hidden',
    marginBottom: 16,
    boxShadow: '0 5px 20px rgba(23,105,194,0.15)',
  },
  activeBand: {
    minHeight: 50,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingLeft: 17,
    paddingRight: 9,
  },
  whiteBold: { color: '#ffffff', fontWeight: '700', fontSize: 15 },
  liveDot: { width: 7, height: 7, borderRadius: 4, backgroundColor: '#ffffff' },
  continue: {
    minHeight: 44,
    paddingHorizontal: 8,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  timer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: 7,
    paddingVertical: 7,
  },
});
