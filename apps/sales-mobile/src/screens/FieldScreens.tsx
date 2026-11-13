import React, { useCallback, useEffect, useState } from 'react';
import { ProfileAvatar as Avatar } from '../components/ProfileAvatar';
import * as Crypto from 'expo-crypto';
import { Image, Linking, Pressable, RefreshControl, ScrollView, Text, View } from 'react-native';
import { useFocusEffect, useNavigation, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import {
  ActionRow,
  BodyText,
  Button,
  Icon,
  kit,
  ProductPicture,
  Search,
  Section,
  Segment,
  State,
  Status,
  styles,
  useRead,
  AmountRow,
} from '../components/ui';
import { salesClient, useSession } from '../auth/session';
import type {
  OrderLine,
  SalesOrder,
  Store,
  StoreProfile,
  Visit,
  Offering,
  PriceQuote,
} from '../domain/contracts';
import type { RootStack } from '../navigation';
import { money, useColors } from '../theme';
import { unitLabel } from '../domain/cart';
import { ownDrafts, sendDraft } from '../data/orders';
import { acceptDraftPrice, type OrderDraft } from '../domain/draft';
type Nav = NativeStackNavigationProp<RootStack>;
const date = (value: string) =>
  value ? new Date(value).toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }) : '';
function StoreRow({ store, onPress, visit }: { store: Store; onPress: () => void; visit?: Visit }) {
  const c = useColors();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={store.name}
      onPress={onPress}
      style={({ pressed }) => [
        {
          padding: 16,
          gap: 10,
          borderBottomWidth: 0.5,
          borderBottomColor: c.border,
          backgroundColor: pressed ? c.accent : c.surface,
        },
      ]}
    >
      <View style={kit.horizontal}>
        <Avatar name={store.name} />
        <View style={{ flex: 1, gap: 3 }}>
          <Text style={[styles.rowTitle, { color: c.text }]}>{store.name}</Text>
          <Text style={[kit.caption, { color: c.muted }]}>
            {store.owner || 'Điểm bán được phân công'}
          </Text>
        </View>
        <Icon name="chevron-forward" color={c.muted} size={18} />
      </View>
      <View style={[kit.horizontal, { paddingLeft: 56, alignItems: 'flex-start', gap: 5 }]}>
        <Icon name="location-outline" size={15} color={c.muted} />
        <Text style={[kit.caption, { color: c.muted, flex: 1 }]}>
          {store.address || 'Chưa có địa chỉ'}
        </Text>
      </View>
      {visit && (
        <View style={{ paddingLeft: 56 }}>
          <Status value={visit.status} />
        </View>
      )}
    </Pressable>
  );
}
export { Today } from './Today';
export function Stores() {
  const c = useColors(),
    data = useRead<Store[]>('/stores', []),
    nav = useNavigation<Nav>(),
    [query, setQuery] = useState('');
  const rows = data.data.filter((s) =>
    `${s.name} ${s.phone} ${s.address}`
      .toLocaleLowerCase('vi')
      .includes(query.toLocaleLowerCase('vi')),
  );
  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: c.canvas }}
      contentContainerStyle={{ padding: 16 }}
      refreshControl={<RefreshControl refreshing={data.loading} onRefresh={data.reload} />}
    >
      <Search value={query} onChange={setQuery} placeholder="Tên điểm bán, số điện thoại…" />
      <View style={[kit.between, { paddingVertical: 16 }]}>
        <Text style={[styles.rowTitle, { color: c.text }]}>Điểm bán được giao</Text>
        <Text style={[kit.caption, { color: c.muted }]}>{rows.length} điểm</Text>
      </View>
      <State loading={data.loading} error={data.error} retry={data.reload} />
      <Section>
        {rows.map((s) => (
          <StoreRow
            key={s.id}
            store={s}
            onPress={() => nav.navigate('Store', { id: s.id, name: s.name })}
          />
        ))}
        {!data.loading && !rows.length && (
          <State loading={false} error="" retry={data.reload} empty />
        )}
      </Section>
    </ScrollView>
  );
}
export function Orders() {
  const c = useColors(),
    data = useRead<SalesOrder[]>('/sales-orders', []),
    stores = useRead<Store[]>('/stores', []),
    nav = useNavigation<Nav>(),
    [query, setQuery] = useState(''),
    [filter, setFilter] = useState('all');
  useFocusEffect(
    useCallback(() => {
      data.reload();
    }, [data.reload]),
  );
  const rows = data.data.filter(
    (o) =>
      (filter === 'all' ||
        (filter === 'done'
          ? o.status === 'DELIVERED'
          : !['DELIVERED', 'CANCELLED'].includes(o.status))) &&
      `${o.code} ${stores.data.find((s) => s.id === o.storeId)?.name || ''}`
        .toLowerCase()
        .includes(query.toLowerCase()),
  );
  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: c.canvas }}
      contentContainerStyle={{ padding: 16, paddingBottom: 30 }}
      refreshControl={<RefreshControl refreshing={data.loading} onRefresh={data.reload} />}
    >
      <Search value={query} onChange={setQuery} placeholder="Mã đơn, tên điểm bán…" />
      <View style={{ marginVertical: 14 }}>
        <Segment
          value={filter}
          onChange={setFilter}
          items={[
            { key: 'all', label: 'Tất cả' },
            { key: 'pending', label: 'Đang xử lý' },
            { key: 'done', label: 'Đã giao' },
          ]}
        />
      </View>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Đơn chờ gửi"
        onPress={() => nav.navigate('Drafts')}
        style={[
          kit.between,
          { padding: 14, backgroundColor: c.accent, borderRadius: 12, marginBottom: 18 },
        ]}
      >
        <View style={kit.horizontal}>
          <Icon name="cloud-upload-outline" />
          <Text style={{ color: c.primary, fontWeight: '600' }}>Đơn chờ gửi trên thiết bị</Text>
        </View>
        <Icon name="chevron-forward" size={18} />
      </Pressable>
      <State loading={data.loading} error={data.error} retry={data.reload} />
      <Section>
        {rows.map((o) => (
          <Pressable
            key={o.id}
            accessibilityRole="button"
            accessibilityLabel={`Đơn ${o.code}`}
            onPress={() => nav.navigate('Order', { id: o.id, name: o.code })}
            style={({ pressed }) => [
              {
                padding: 16,
                gap: 11,
                borderBottomWidth: 0.5,
                borderBottomColor: c.border,
                backgroundColor: pressed ? c.accent : c.surface,
              },
            ]}
          >
            <View style={kit.between}>
              <Text style={{ fontSize: 16, fontWeight: '700', color: c.primary }}>{o.code}</Text>
              <Status value={o.status} />
            </View>
            <Text style={{ color: c.text, fontSize: 15 }}>
              {stores.data.find((s) => s.id === o.storeId)?.name || `Điểm bán #${o.storeId}`}
            </Text>
            <View style={kit.between}>
              <Text style={[kit.caption, { color: c.muted }]}>{date(o.createdAt)}</Text>
              <Text style={{ fontSize: 19, fontWeight: '700', color: c.text }}>
                {money(o.total)}
              </Text>
            </View>
          </Pressable>
        ))}
        {!data.loading && !rows.length && (
          <State loading={false} error="" retry={data.reload} empty />
        )}
      </Section>
    </ScrollView>
  );
}
export function StoreDetail({ route }: { route: RouteProp<RootStack, 'Store'> }) {
  const c = useColors(),
    data = useRead<StoreProfile | null>(`/stores/${route.params.id}/360`, null),
    visits = useRead<Visit[]>('/visits/today', []),
    nav = useNavigation<Nav>(),
    [tab, setTab] = useState(route.params.section || 'prepare'),
    [linkError, setLinkError] = useState('');
  useEffect(() => {
    setTab(route.params.section || 'prepare');
  }, [route.params.section, route.params.id]);
  useFocusEffect(
    useCallback(() => {
      data.reload();
      visits.reload();
    }, [data.reload, visits.reload]),
  );
  const p = data.data,
    visit = visits.data.find((v) => v.storeId === route.params.id);
  async function open(url: string) {
    try {
      await Linking.openURL(url);
    } catch {
      setLinkError('Không mở được ứng dụng. Vui lòng kiểm tra số điện thoại hoặc bản đồ.');
    }
  }
  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: c.canvas }}
      contentContainerStyle={{ padding: 16, paddingBottom: 30 }}
      refreshControl={<RefreshControl refreshing={data.loading} onRefresh={data.reload} />}
    >
      <State loading={data.loading} error={data.error} retry={data.reload} />
      {p && (
        <>
          <Section>
            <View style={{ padding: 18, gap: 14 }}>
              <View style={[kit.horizontal, { alignItems: 'flex-start' }]}>
                <Avatar name={p.store.name} size={52} />
                <View style={{ flex: 1, gap: 5 }}>
                  <Text style={{ fontSize: 21, fontWeight: '700', lineHeight: 28, color: c.text }}>
                    {p.store.name}
                  </Text>
                  <Text style={{ color: c.muted, fontSize: 14 }}>
                    {p.store.owner} {p.store.groupName ? `· Nhóm ${p.store.groupName}` : ''}
                  </Text>
                </View>
              </View>
              <View style={[kit.horizontal, { alignItems: 'flex-start', gap: 6 }]}>
                <Icon name="location-outline" size={17} color={c.muted} />
                <Text style={{ flex: 1, fontSize: 14, lineHeight: 21, color: c.muted }}>
                  {p.store.address}
                </Text>
              </View>
              <View style={kit.horizontal}>
                {Boolean(p.store.phone) && (
                  <View style={{ flex: 1 }}>
                    <Button secondary onPress={() => void open(`tel:${p.store.phone}`)}>
                      Gọi điện
                    </Button>
                  </View>
                )}
                {p.store.latitude != null && p.store.longitude != null && (
                  <View style={{ flex: 1 }}>
                    <Button
                      secondary
                      onPress={() =>
                        void open(
                          `https://maps.apple.com/?daddr=${p.store.latitude},${p.store.longitude}`,
                        )
                      }
                    >
                      Chỉ đường
                    </Button>
                  </View>
                )}
              </View>
              {Boolean(linkError) && (
                <Text accessibilityRole="alert" style={{ color: c.danger }}>
                  {linkError}
                </Text>
              )}
              {visit && <Status value={visit.status} />}
            </View>
          </Section>
          <View style={{ marginBottom: 18 }}>
            <Segment
              value={tab}
              onChange={(value) => setTab(value as 'prepare' | 'actions' | 'history')}
              items={[
                { key: 'prepare', label: 'Chuẩn bị' },
                { key: 'actions', label: 'Hoạt động' },
                { key: 'history', label: 'Lịch sử' },
              ]}
            />
          </View>
          {tab === 'prepare' && (
            <>
              <Section title="Việc cần tiếp tục">
                {p.appointments
                  .filter((a) => a.status !== 'COMPLETED')
                  .slice(0, 4)
                  .map((a) => (
                    <View
                      key={a.id}
                      style={{
                        padding: 16,
                        gap: 5,
                        borderBottomWidth: 0.5,
                        borderBottomColor: c.border,
                      }}
                    >
                      <Text style={{ fontSize: 16, fontWeight: '600', color: c.text }}>
                        {a.title}
                      </Text>
                      <Text style={{ fontSize: 14, color: c.muted }}>{date(a.dueAt)}</Text>
                    </View>
                  ))}
                {!p.appointments.filter((a) => a.status !== 'COMPLETED').length && (
                  <View style={{ padding: 16 }}>
                    <BodyText muted>Chưa có lời hẹn đang mở.</BodyText>
                  </View>
                )}
              </Section>
              <Section title="Mua hàng gần nhất">
                {p.orders.slice(0, 2).map((o) => (
                  <ActionRow
                    key={o.id}
                    icon="receipt-outline"
                    title={o.code}
                    detail={`${date(o.createdAt)} · ${money(o.total)}`}
                    onPress={() => nav.navigate('Order', { id: o.id, name: o.code })}
                  />
                ))}
                {!p.orders.length && (
                  <View style={{ padding: 16 }}>
                    <BodyText muted>Chưa có đơn trong phạm vi được xem.</BodyText>
                  </View>
                )}
              </Section>
              <Button onPress={() => setTab('actions')}>Bắt đầu hoạt động tại điểm</Button>
            </>
          )}
          {tab === 'actions' && (
            <Section>
              <ActionRow
                title="AI kiểm tra trưng bày"
                detail="Chụp ảnh kệ và xem điểm đề xuất"
                icon="scan-outline"
                onPress={() =>
                  nav.navigate('DisplayAudit', { storeId: p.store.id, name: p.store.name })
                }
              />
              <ActionRow
                title="AI hỗ trợ lên đơn"
                detail="Chuyển yêu cầu của khách thành giỏ hàng"
                icon="sparkles-outline"
                onPress={() =>
                  nav.navigate('OrderAssistant', { storeId: p.store.id, name: p.store.name })
                }
              />
              <ActionRow
                title={visit?.status === 'CHECKED_IN' ? 'Kết thúc lượt ghé' : 'Check-in điểm bán'}
                detail={
                  visit?.status === 'COMPLETED'
                    ? 'Lượt ghé đã hoàn thành'
                    : !visit
                      ? 'Điểm chưa có lượt ghé hôm nay'
                      : 'GPS và kết quả lượt ghé'
                }
                icon="location-outline"
                disabled={!visit || visit.status === 'COMPLETED'}
                onPress={() =>
                  nav.navigate('Activity', {
                    kind: visit?.status === 'CHECKED_IN' ? 'checkout' : 'checkin',
                    storeId: p.store.id,
                    name: p.store.name,
                    visitId: visit?.id,
                  })
                }
              />
              <ActionRow
                title="Lên đơn hàng"
                detail="Chọn sản phẩm, đơn vị và số lượng"
                icon="cart-outline"
                onPress={() =>
                  nav.navigate('Compose', { storeId: p.store.id, storeName: p.store.name })
                }
              />
              <ActionRow
                title="Ghi tồn điểm bán"
                icon="cube-outline"
                onPress={() =>
                  nav.navigate('Activity', {
                    kind: 'survey',
                    storeId: p.store.id,
                    name: p.store.name,
                  })
                }
              />
              <ActionRow
                title="Ghi nhận nhu cầu"
                icon="chatbubble-ellipses-outline"
                onPress={() =>
                  nav.navigate('Activity', {
                    kind: 'interaction',
                    storeId: p.store.id,
                    name: p.store.name,
                  })
                }
              />
              <ActionRow
                title="Ý kiến khách hàng"
                icon="megaphone-outline"
                onPress={() =>
                  nav.navigate('Activity', {
                    kind: 'ticket',
                    storeId: p.store.id,
                    name: p.store.name,
                  })
                }
              />
              <ActionRow
                title="Lưu lời hẹn"
                icon="calendar-outline"
                onPress={() =>
                  nav.navigate('Activity', {
                    kind: 'appointment',
                    storeId: p.store.id,
                    name: p.store.name,
                  })
                }
              />
            </Section>
          )}
          {tab === 'history' && (
            <>
              <Section title="Đơn hàng">
                {p.orders.map((o) => (
                  <ActionRow
                    key={o.id}
                    title={o.code}
                    detail={`${money(o.total)} · ${date(o.createdAt)}`}
                    icon="receipt-outline"
                    onPress={() => nav.navigate('Order', { id: o.id, name: o.code })}
                  />
                ))}
                {!p.orders.length && (
                  <View style={{ padding: 16 }}>
                    <BodyText muted>Chưa có đơn được trả về.</BodyText>
                  </View>
                )}
              </Section>
              <Section title="Tương tác">
                {p.interactions.map((i) => (
                  <View
                    key={i.id}
                    style={{
                      padding: 16,
                      gap: 5,
                      borderBottomWidth: 0.5,
                      borderBottomColor: c.border,
                    }}
                  >
                    <Text style={{ color: c.text, fontSize: 16 }}>{i.note}</Text>
                    <Text style={{ color: c.muted, fontSize: 13 }}>
                      {date(i.at)} · {i.kind}
                    </Text>
                  </View>
                ))}
                {!p.interactions.length && (
                  <View style={{ padding: 16 }}>
                    <BodyText muted>Chưa có tương tác được trả về.</BodyText>
                  </View>
                )}
              </Section>
            </>
          )}
        </>
      )}
    </ScrollView>
  );
}
export function OrderDetail({ route }: { route: RouteProp<RootStack, 'Order'> }) {
  const c = useColors(),
    data = useRead<(SalesOrder & { lines: OrderLine[] }) | null>(
      `/sales-orders/${route.params.id}`,
      null,
    ),
    stores = useRead<Store[]>('/stores', []),
    products = useRead<Offering[]>('/catalog/skus', []),
    nav = useNavigation<Nav>();
  const o = data.data,
    store = stores.data.find((s) => s.id === o?.storeId);
  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: c.canvas }}
      contentContainerStyle={{ padding: 16, paddingBottom: 30 }}
      refreshControl={<RefreshControl refreshing={data.loading} onRefresh={data.reload} />}
    >
      <State loading={data.loading} error={data.error} retry={data.reload} />
      {o && (
        <>
          <Section>
            <View style={{ padding: 18, gap: 12 }}>
              <View style={kit.between}>
                <Text style={{ fontSize: 20, fontWeight: '700', color: c.text }}>{o.code}</Text>
                <Status value={o.status} />
              </View>
              <Text style={{ fontSize: 14, color: c.muted }}>{date(o.createdAt)}</Text>
              <Text style={{ fontSize: 18, fontWeight: '600', color: c.text }}>
                {store?.name || `Điểm bán #${o.storeId}`}
              </Text>
              <AmountRow label="Giá trị đơn" value={money(o.total)} strong />
            </View>
          </Section>
          <Section title={`Sản phẩm · ${o.lines.length} dòng`}>
            {o.lines.map((l) => {
              const product = products.data.find(
                (p) => (p as unknown as { id: number }).id === l.skuId,
              );
              return (
                <View
                  key={l.id}
                  style={{
                    padding: 16,
                    borderBottomWidth: 0.5,
                    borderBottomColor: c.border,
                    gap: 12,
                  }}
                >
                  <View style={kit.horizontal}>
                    <ProductPicture code={product?.code || ''} size={60} />
                    <View style={{ flex: 1, gap: 5 }}>
                      <Text style={{ color: c.text, fontWeight: '600', fontSize: 16 }}>
                        {product?.name || `SKU #${l.skuId}`}
                      </Text>
                      <Text style={{ color: c.muted, fontSize: 14 }}>
                        {unitLabel(l.uom)} · {money(l.price)}
                      </Text>
                    </View>
                  </View>
                  <View style={kit.horizontal}>
                    {[
                      { label: 'Đặt', n: l.qtyOrdered },
                      { label: 'Xác nhận', n: l.qtyConfirmed },
                      { label: 'Đã xuất', n: l.qtyShipped },
                      { label: 'Thực nhận', n: l.qtyReceived },
                    ].map((q) => (
                      <View key={q.label} style={{ flex: 1, gap: 4 }}>
                        <Text style={{ fontSize: 12, color: c.muted }}>{q.label}</Text>
                        <Text style={{ fontSize: 18, fontWeight: '600', color: c.text }}>
                          {q.n}
                        </Text>
                      </View>
                    ))}
                  </View>
                  {Number(l.qtyReplacementReceived) > 0 && (
                    <Text style={{ color: c.primary }}>
                      Đã nhận giao bù: {l.qtyReplacementReceived} {unitLabel(l.uom)}
                    </Text>
                  )}
                </View>
              );
            })}
          </Section>
          {o.status === 'DELIVERED_SHORT' && (
            <Section title="Theo phần giao thiếu">
              <View style={{ padding: 18, gap: 12 }}>
                {o.lines.some((l) => Number(l.qtyAvailableToReplace) > 0) && (
                  <Button onPress={() => nav.navigate('ReplacementRequest', { orderId: o.id })}>
                    Đề xuất phương án giao bù
                  </Button>
                )}
                <Button secondary onPress={() => nav.navigate('Inbox')}>
                  Theo dõi và báo lại khách
                </Button>
              </View>
            </Section>
          )}
          {store && (
            <Button
              onPress={() =>
                nav.navigate('Compose', {
                  storeId: store.id,
                  storeName: store.name,
                  reorderId: o.id,
                })
              }
            >
              Đặt lại đơn này
            </Button>
          )}
        </>
      )}
    </ScrollView>
  );
}
export function Drafts() {
  const c = useColors(),
    { user } = useSession(),
    nav = useNavigation<Nav>(),
    [rows, setRows] = useState<OrderDraft[]>([]),
    [review, setReview] = useState<{ id: string; quote: PriceQuote } | null>(null),
    [busy, setBusy] = useState(''),
    [error, setError] = useState('');
  const reload = useCallback(() => {
    if (user)
      ownDrafts(user)
        .then(setRows)
        .catch(() => setError('Không đọc được nháp trên thiết bị.'));
  }, [user]);
  useFocusEffect(reload);
  async function loadPrice(d: OrderDraft) {
    if (!user || busy) return;
    setBusy(d.id);
    setError('');
    try {
      const quote = await salesClient.post<PriceQuote>(
        '/pricing/quote',
        {
          nppId: user.nppId,
          storeId: d.payload.storeId,
          lines: d.payload.lines.map(({ skuId, uom, qty }) => ({ skuId, uom, qty })),
        },
        Crypto.randomUUID(),
      );
      setReview({ id: d.id, quote });
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Chưa lấy được giá mới.');
    } finally {
      setBusy('');
    }
  }
  async function send(d: OrderDraft) {
    if (!user || busy) return;
    setBusy(d.id);
    setError('');
    try {
      const result = await sendDraft(d, user);
      reload();
      if (result.orderId) nav.navigate('Order', { id: result.orderId, name: 'Đơn đã gửi' });
      else setError(result.message || 'Đơn vẫn được giữ để xử lý.');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Chưa gửi được nháp.');
    } finally {
      setBusy('');
    }
  }
  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: c.canvas }}
      contentContainerStyle={{ padding: 16 }}
    >
      <Text style={[styles.rowTitle, { color: c.text, marginBottom: 8 }]}>
        Đơn lưu trên thiết bị
      </Text>
      <Text style={{ fontSize: 14, color: c.muted, marginBottom: 20 }}>
        Giữ nguyên mã gửi lại, tránh tạo đơn trùng.
      </Text>
      {Boolean(error) && (
        <Text accessibilityRole="alert" style={{ color: c.danger, marginBottom: 16 }}>
          {error}
        </Text>
      )}
      {rows.map((d) => (
        <Section key={d.id}>
          <View style={{ padding: 16, gap: 12 }}>
            <View style={kit.between}>
              <Text style={{ color: c.text, fontSize: 17, fontWeight: '600', flex: 1 }}>
                {d.storeName}
              </Text>
              <Text style={{ color: c.primary, fontWeight: '600' }}>
                {d.status === 'LOCAL' ? 'Chờ gửi' : 'Cần xử lý'}
              </Text>
            </View>
            <Text style={{ color: c.muted, fontSize: 13 }}>
              {date(d.payload.createdAt)} · {d.payload.lines.length} dòng hàng
            </Text>
            <AmountRow label="Giá trị tham khảo" value={money(d.total)} />
            {Boolean(d.message) && (
              <Text style={{ color: c.danger, fontSize: 14, lineHeight: 21 }}>{d.message}</Text>
            )}
            {d.serverStatus === 'PRICE_CHANGED' ? (
              <>
                {review?.id === d.id ? (
                  <>
                    <AmountRow label="Tổng giá mới" value={money(review.quote.total)} strong />
                    <Button
                      disabled={Boolean(busy)}
                      onPress={() => {
                        if (!user) return;
                        try {
                          void send(acceptDraftPrice(d, review.quote, user));
                        } catch (e) {
                          setError(e instanceof Error ? e.message : 'Chưa xác nhận được giá mới.');
                        }
                      }}
                    >
                      {busy === d.id ? 'Đang gửi…' : 'Đồng ý giá mới và gửi lại'}
                    </Button>
                  </>
                ) : (
                  <Button disabled={Boolean(busy)} onPress={() => void loadPrice(d)}>
                    {busy === d.id ? 'Đang lấy giá…' : 'Kiểm tra giá hiện tại'}
                  </Button>
                )}
              </>
            ) : (
              <Button disabled={Boolean(busy)} onPress={() => void send(d)}>
                {busy === d.id ? 'Đang gửi…' : 'Gửi lại cùng mã'}
              </Button>
            )}
          </View>
        </Section>
      ))}
      {!rows.length && (
        <View style={{ padding: 28, alignItems: 'center', gap: 12 }}>
          <Icon name="cloud-done-outline" size={40} />
          <BodyText muted>Không có đơn chờ gửi trong tài khoản này.</BodyText>
        </View>
      )}
    </ScrollView>
  );
}
export function Account() {
  const identity = useRead<{ name: string } | null>('/auth/me', null);
  const c = useColors(),
    { user } = useSession(),
    orgs = useRead<{ id: number; name: string }[]>('/organizations', []),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('');
  async function logout() {
    setBusy(true);
    setError('');
    try {
      await salesClient.logout();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Chưa đăng xuất được.');
    } finally {
      setBusy(false);
    }
  }
  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: c.canvas }}
      contentContainerStyle={{ padding: 20 }}
    >
      <View style={{ alignItems: 'center', gap: 12, paddingVertical: 24 }}>
        <Avatar name={identity.data?.name || user?.name || 'Sales'} userId={user?.id} size={76} />
        <Text style={{ fontSize: 23, fontWeight: '700', color: c.text }}>
          {identity.data?.name || user?.name}
        </Text>
        <Text style={{ fontSize: 15, color: c.muted }}>Nhân viên Sales</Text>
      </View>
      <Section>
        <View style={{ padding: 18, gap: 10 }}>
          <Text style={{ color: c.muted, fontSize: 13 }}>NPP đang làm việc</Text>
          <Text style={{ color: c.text, fontSize: 17, fontWeight: '600' }}>
            {orgs.data.find((o) => o.id === user?.nppId)?.name || 'Theo phân công máy chủ'}
          </Text>
        </View>
      </Section>
      <View style={{ alignItems: 'center', marginVertical: 22 }}>
        <Image
          source={require('../../assets/synera-logo.png')}
          resizeMode="contain"
          style={{ width: 130, height: 44 }}
        />
      </View>
      {Boolean(error) && (
        <Text accessibilityRole="alert" style={{ color: c.danger, marginBottom: 16 }}>
          {error}
        </Text>
      )}
      <Button secondary disabled={busy} onPress={() => void logout()}>
        {busy ? 'Đang đăng xuất…' : 'Đăng xuất'}
      </Button>
    </ScrollView>
  );
}
