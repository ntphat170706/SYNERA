import React, { useEffect, useRef, useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  View,
} from 'react-native';
import type { RouteProp } from '@react-navigation/native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Crypto from 'expo-crypto';
import type { RootStack } from '../navigation';
import type { Offering, OrderLine, PriceQuote, SalesOrder, Store } from '../domain/contracts';
import { cartLines, lineKey, stepQuantity, unitLabel } from '../domain/cart';
import type { OrderDraft } from '../domain/draft';
import { salesClient, useSession } from '../auth/session';
import { saveDraft, sendDraft } from '../data/orders';
import {
  AmountRow,
  Button,
  Icon,
  kit,
  ProductPicture,
  Search,
  Section,
  Segment,
  State,
  styles,
  useRead,
} from '../components/ui';
import { money, useColors } from '../theme';

export function OrderComposer({ route }: { route: RouteProp<RootStack, 'Compose'> }) {
  const c = useColors(),
    insets = useSafeAreaInsets(),
    { user } = useSession(),
    nav = useNavigation<NativeStackNavigationProp<RootStack>>();
  const { storeId, storeName, reorderId } = route.params;
  const offers = useRead<Offering[]>(
      `/catalog/offerings?nppId=${user!.nppId}&storeId=${storeId}`,
      [],
    ),
    store = useRead<Store | null>(`/stores/${storeId}`, null);
  const [step, setStep] = useState(0),
    [query, setQuery] = useState(''),
    [category, setCategory] = useState('all'),
    [cart, setCart] = useState<Record<string, number>>({}),
    [units, setUnits] = useState<Record<number, string>>({}),
    [quote, setQuote] = useState<PriceQuote | null>(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('');
  const locked = useRef(false),
    draftRef = useRef<OrderDraft | null>(null);
  useEffect(() => {
    if (!route.params.initialLines?.length) return;
    const lines = route.params.initialLines;
    setCart(Object.fromEntries(lines.map((line) => [lineKey(line), line.qty])));
    setUnits(Object.fromEntries(lines.map((line) => [line.skuId, line.uom])));
    setStep(1);
  }, [route.params.initialLines]);
  useEffect(() => {
    if (!reorderId) return;
    let active = true;
    salesClient
      .get<SalesOrder & { lines: OrderLine[] }>(`/sales-orders/${reorderId}`)
      .then((o) => {
        if (active) {
          setCart(Object.fromEntries(o.lines.map((l) => [lineKey(l), l.qtyOrdered])));
          setUnits(Object.fromEntries(o.lines.map((l) => [l.skuId, l.uom])));
          setStep(1);
        }
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, [reorderId]);
  const selected = offers.data.filter((o) => (cart[lineKey(o)] || 0) > 0),
    estimate = selected.reduce((sum, o) => sum + o.price * cart[lineKey(o)], 0);
  const unavailable = Object.keys(cart).filter(
    (key) => cart[key] > 0 && !offers.data.some((o) => lineKey(o) === key),
  );
  const products = [...new Set(offers.data.map((o) => o.skuId))]
    .map((id) => {
      const rows = offers.data.filter((o) => o.skuId === id);
      return rows.find((o) => o.uom === (units[id] || o.baseUnit)) || rows[0];
    })
    .filter(
      (o) =>
        (category === 'all' || o.category === category) &&
        `${o.name} ${o.code}`.toLocaleLowerCase('vi').includes(query.toLocaleLowerCase('vi')),
    );
  function change(o: Offering, qty: number) {
    if (locked.current) return;
    setCart((previous) => ({ ...previous, [lineKey(o)]: qty }));
    setQuote(null);
    setError('');
    draftRef.current = null;
  }
  async function review() {
    if (locked.current || !user) return;
    locked.current = true;
    setBusy(true);
    setError('');
    try {
      const lines = cartLines(cart, offers.data);
      if (!lines.length) throw Error('Chọn ít nhất một sản phẩm.');
      const value = await salesClient.post<PriceQuote>(
        '/pricing/quote',
        { nppId: user.nppId, storeId, lines },
        Crypto.randomUUID(),
      );
      setQuote(value);
      setStep(2);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Không lấy được giá hiện tại.');
    } finally {
      locked.current = false;
      setBusy(false);
    }
  }
  function buildDraft(): OrderDraft {
    if (draftRef.current) return draftRef.current;
    if (!user) throw Error('Vui lòng đăng nhập lại.');
    const id = Crypto.randomUUID();
    const lines = cartLines(cart, offers.data);
    if (!lines.length) throw Error('Giỏ hàng đang trống.');
    const draft: OrderDraft = {
      id,
      ownerId: user.id,
      workspaceKey: user.workspaceKey,
      storeName,
      total: quote?.total ?? estimate,
      status: 'LOCAL',
      payload: {
        clientId: id,
        idempotencyKey: id,
        storeId,
        createdAt: new Date().toISOString(),
        lines: lines.map((l) => ({
          ...l,
          clientPrice:
            quote?.lines.find((q) => lineKey(q) === lineKey(l))?.price ??
            offers.data.find((o) => lineKey(o) === lineKey(l))!.price,
        })),
      },
    };
    draftRef.current = draft;
    return draft;
  }
  async function submit(saveOnly = false) {
    if (locked.current || !user) return;
    locked.current = true;
    setBusy(true);
    setError('');
    try {
      const draft = buildDraft();
      await saveDraft(draft);
      if (saveOnly) {
        nav.replace('Drafts');
        return;
      }
      const result = await sendDraft(draft, user);
      draftRef.current = result;
      if (result.status === 'ACKNOWLEDGED' && result.orderId) {
        nav.replace('Order', { id: result.orderId, name: 'Đơn đã gửi' });
      } else {
        setError(result.message || 'Đơn vẫn được lưu trên thiết bị.');
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Chưa lưu được đơn. Vui lòng thử lại.');
    } finally {
      locked.current = false;
      setBusy(false);
    }
  }
  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: c.canvas }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View style={{ padding: 16, paddingBottom: 12, backgroundColor: c.surface, gap: 12 }}>
        <View style={kit.horizontal}>
          <Icon name="storefront-outline" size={20} />
          <Text style={{ fontSize: 15, fontWeight: '600', color: c.text, flex: 1 }}>
            {storeName}
          </Text>
        </View>
        <View style={kit.horizontal}>
          {['Loại đơn', 'Sản phẩm', 'Kiểm tra'].map((title, i) => (
            <Pressable
              key={title}
              disabled={i > step || busy || Boolean(draftRef.current)}
              accessibilityRole="button"
              accessibilityLabel={`Bước ${i + 1}: ${title}`}
              onPress={() => setStep(i)}
              style={{ flex: 1, gap: 7 }}
            >
              <View
                style={{
                  height: 3,
                  borderRadius: 2,
                  backgroundColor: i <= step ? c.primary : c.border,
                }}
              />
              <Text
                style={{
                  fontSize: 12,
                  fontWeight: i === step ? '700' : '400',
                  color: i === step ? c.primary : c.muted,
                }}
              >
                {i + 1}. {title}
              </Text>
            </Pressable>
          ))}
        </View>
      </View>
      <ScrollView
        keyboardShouldPersistTaps="handled"
        style={{ flex: 1 }}
        contentContainerStyle={{ padding: 16, paddingBottom: 24 }}
      >
        {step === 0 && (
          <>
            <Text style={[styles.sectionTitle, { color: c.text, marginBottom: 18 }]}>
              Chọn loại đơn hàng
            </Text>
            <Section>
              <View style={{ padding: 18, gap: 7 }}>
                <View style={kit.horizontal}>
                  <Icon name="radio-button-on" />
                  <Text style={{ color: c.text, fontSize: 17, fontWeight: '600', flex: 1 }}>
                    Đơn giao sau
                  </Text>
                  <Icon name="time-outline" />
                </View>
                <Text style={{ color: c.muted, fontSize: 14, lineHeight: 21, paddingLeft: 34 }}>
                  Gửi đơn để NPP xác nhận và điều phối giao.
                </Text>
              </View>
              {[
                { title: 'Đơn giao ngay', icon: 'bicycle-outline' as const },
                { title: 'Đơn dùng thử', icon: 'flask-outline' as const },
              ].map((o) => (
                <View
                  key={o.title}
                  style={{
                    padding: 18,
                    borderTopWidth: 0.5,
                    borderTopColor: c.border,
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: 12,
                    opacity: 0.6,
                  }}
                >
                  <Icon name={o.icon} color={c.muted} />
                  <Text style={{ color: c.muted, fontSize: 16, flex: 1 }}>{o.title}</Text>
                  <Text style={{ color: c.muted, fontSize: 12 }}>Chưa hỗ trợ</Text>
                </View>
              ))}
            </Section>
            <Section title="Điểm bán">
              <View style={{ padding: 16, gap: 7 }}>
                <Text style={{ color: c.text, fontSize: 17, fontWeight: '600' }}>{storeName}</Text>
                <Text style={{ color: c.muted, fontSize: 14, lineHeight: 21 }}>
                  {store.data?.address}
                </Text>
              </View>
            </Section>
          </>
        )}
        {step === 1 && (
          <>
            <Search value={query} onChange={setQuery} placeholder="Tìm sản phẩm, mã hàng…" />
            <View style={{ marginVertical: 14 }}>
              <Segment
                value={category}
                onChange={setCategory}
                items={[
                  { key: 'all', label: 'Tất cả' },
                  { key: 'DRINK', label: 'Đồ uống' },
                  { key: 'FOOD', label: 'Thực phẩm' },
                ]}
              />
            </View>
            <State loading={offers.loading} error={offers.error} retry={offers.reload} />
            <Section>
              {products.map((o) => {
                const variants = offers.data.filter((p) => p.skuId === o.skuId),
                  qty = cart[lineKey(o)] || 0;
                return (
                  <View
                    key={o.skuId}
                    style={{
                      padding: 14,
                      borderBottomWidth: 0.5,
                      borderBottomColor: c.border,
                      gap: 12,
                    }}
                  >
                    <View style={[kit.horizontal, { alignItems: 'flex-start' }]}>
                      <ProductPicture code={o.code} size={74} />
                      <View style={{ flex: 1, gap: 5 }}>
                        <Text
                          style={{ color: c.text, fontSize: 16, fontWeight: '600', lineHeight: 22 }}
                        >
                          {o.name}
                        </Text>
                        <Text style={{ color: c.muted, fontSize: 12 }}>{o.code}</Text>
                        <Text style={{ color: c.primary, fontWeight: '700', fontSize: 17 }}>
                          {money(o.price)}
                          <Text style={{ fontSize: 12, fontWeight: '400', color: c.muted }}>
                            {' '}
                            / {unitLabel(o.uom).toLowerCase()}
                          </Text>
                        </Text>
                      </View>
                    </View>
                    <View style={kit.between}>
                      <View style={{ flexDirection: 'row', gap: 4, flex: 1, flexWrap: 'wrap' }}>
                        {variants.map((v) => (
                          <Pressable
                            key={v.uom}
                            accessibilityRole="button"
                            accessibilityLabel={`${o.name} đơn vị ${unitLabel(v.uom)}`}
                            accessibilityState={{ selected: v.uom === o.uom, disabled: busy }}
                            disabled={busy}
                            onPress={() => setUnits((prev) => ({ ...prev, [v.skuId]: v.uom }))}
                            style={{
                              minHeight: 44,
                              paddingHorizontal: 9,
                              justifyContent: 'center',
                              borderRadius: 7,
                              backgroundColor: v.uom === o.uom ? c.accent : 'transparent',
                            }}
                          >
                            <Text
                              style={{
                                color: v.uom === o.uom ? c.primary : c.muted,
                                fontWeight: v.uom === o.uom ? '600' : '400',
                                fontSize: 13,
                              }}
                            >
                              {unitLabel(v.uom)}
                              {(cart[lineKey(v)] || 0) > 0 ? ` (${cart[lineKey(v)]})` : ''}
                            </Text>
                          </Pressable>
                        ))}
                      </View>
                      <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                        <Pressable
                          accessibilityRole="button"
                          accessibilityLabel={`Giảm ${o.name}`}
                          disabled={qty <= 0 || busy}
                          onPress={() => change(o, stepQuantity(qty, o.multiple, -1))}
                          style={{
                            minWidth: 44,
                            minHeight: 44,
                            alignItems: 'center',
                            justifyContent: 'center',
                          }}
                        >
                          <Icon
                            name="remove-circle-outline"
                            size={25}
                            color={qty ? c.primary : c.border}
                          />
                        </Pressable>
                        <TextInput
                          accessibilityLabel={`Số lượng ${o.name}`}
                          keyboardType="decimal-pad"
                          value={String(qty)}
                          editable={!busy}
                          selectTextOnFocus
                          onChangeText={(v) => {
                            if (/^\d*(?:[.,]\d{0,3})?$/.test(v))
                              change(o, Number(v.replace(',', '.')));
                          }}
                          style={{
                            width: 38,
                            minHeight: 44,
                            textAlign: 'center',
                            fontSize: 17,
                            fontWeight: '600',
                            color: c.text,
                          }}
                        />
                        <Pressable
                          accessibilityRole="button"
                          accessibilityLabel={`Thêm ${o.name}`}
                          disabled={busy}
                          onPress={() => change(o, stepQuantity(qty, o.multiple, 1))}
                          style={{
                            minWidth: 44,
                            minHeight: 44,
                            alignItems: 'center',
                            justifyContent: 'center',
                          }}
                        >
                          <Icon name="add-circle" size={25} />
                        </Pressable>
                      </View>
                    </View>
                  </View>
                );
              })}
              {!offers.loading && !products.length && (
                <View style={{ padding: 24 }}>
                  <Text style={{ color: c.muted }}>Không tìm thấy sản phẩm.</Text>
                </View>
              )}
            </Section>
            <Text style={{ color: c.muted, fontSize: 11, textAlign: 'center' }}>
              Ảnh minh hoạ danh mục
            </Text>
          </>
        )}
        {step === 2 && quote && (
          <>
            <Section title="Sản phẩm đã chọn">
              {selected.map((o) => (
                <View
                  key={lineKey(o)}
                  style={{
                    padding: 14,
                    borderBottomWidth: 0.5,
                    borderBottomColor: c.border,
                    flexDirection: 'row',
                    gap: 12,
                    alignItems: 'center',
                  }}
                >
                  <ProductPicture code={o.code} size={54} />
                  <View style={{ flex: 1, gap: 5 }}>
                    <Text style={{ color: c.text, fontSize: 15, fontWeight: '600' }}>{o.name}</Text>
                    <Text style={{ fontSize: 13, color: c.muted }}>
                      {cart[lineKey(o)]} {unitLabel(o.uom).toLowerCase()}
                    </Text>
                  </View>
                  <Text style={{ fontSize: 15, fontWeight: '600', color: c.text }}>
                    {money(quote.lines.find((l) => lineKey(l) === lineKey(o))?.total || 0)}
                  </Text>
                </View>
              ))}
            </Section>
            <Section title="Ưu đãi và tổng tiền">
              <View style={{ padding: 16 }}>
                <AmountRow label="Tiền hàng" value={money(quote.subtotal)} />
                <AmountRow
                  label="Ưu đãi áp dụng"
                  value={quote.discount ? `−${money(quote.discount)}` : money(0)}
                />
                <View
                  style={{
                    borderTopWidth: 0.5,
                    borderTopColor: c.border,
                    marginTop: 7,
                    paddingTop: 7,
                  }}
                >
                  <AmountRow label="Tổng cộng" value={money(quote.total)} strong />
                </View>
              </View>
            </Section>
            <Section title="Thông tin điểm bán">
              <View style={{ padding: 16, gap: 7 }}>
                <Text style={{ fontSize: 16, fontWeight: '600', color: c.text }}>
                  {store.data?.owner || storeName}
                </Text>
                <Text style={{ fontSize: 14, color: c.muted }}>{store.data?.phone}</Text>
                <Text style={{ fontSize: 14, lineHeight: 22, color: c.muted }}>
                  {store.data?.address}
                </Text>
              </View>
            </Section>
          </>
        )}
        {!offers.loading && unavailable.length > 0 && (
          <View style={{ padding: 16, gap: 12, backgroundColor: c.surface, borderRadius: 12 }}>
            <Text accessibilityRole="alert" style={{ color: c.danger }}>
              Có {unavailable.length} dòng hàng/đơn vị không còn được cung cấp.
            </Text>
            <Button
              secondary
              onPress={() => {
                setCart((prev) =>
                  Object.fromEntries(
                    Object.entries(prev).filter(([key]) => !unavailable.includes(key)),
                  ),
                );
                setQuote(null);
                setError('');
              }}
            >
              Bỏ các dòng không còn bán
            </Button>
          </View>
        )}
        {Boolean(error) && (
          <View style={{ padding: 14, backgroundColor: c.surface, borderRadius: 12, gap: 8 }}>
            <Text
              accessibilityRole="alert"
              style={{ color: c.danger, fontSize: 15, lineHeight: 22 }}
            >
              {error}
            </Text>
            {Boolean(draftRef.current) && (
              <Button secondary onPress={() => nav.replace('Drafts')}>
                Xem đơn chờ gửi
              </Button>
            )}
          </View>
        )}
      </ScrollView>
      <View
        style={[
          kit.footer,
          {
            backgroundColor: c.surface,
            borderColor: c.border,
            paddingBottom: Math.max(insets.bottom, 14),
          },
        ]}
      >
        {step > 0 && (
          <View style={kit.between}>
            <Text style={{ color: c.muted, fontSize: 14 }}>
              {selected.length} dòng hàng · {step === 2 ? 'Tổng cộng' : 'Tạm tính'}
            </Text>
            <Text style={{ color: c.primary, fontSize: 22, fontWeight: '700' }}>
              {money(step === 2 ? quote?.total || 0 : estimate)}
            </Text>
          </View>
        )}
        {step === 0 ? (
          <Button onPress={() => setStep(1)}>Tiếp tục chọn sản phẩm</Button>
        ) : step === 1 ? (
          <View style={kit.horizontal}>
            <View style={{ flex: 1 }}>
              <Button
                secondary
                disabled={busy || !selected.length}
                onPress={() => void submit(true)}
              >
                Lưu nháp
              </Button>
            </View>
            <View style={{ flex: 1.5 }}>
              <Button disabled={busy || !selected.length} onPress={() => void review()}>
                {busy ? 'Đang lấy giá…' : 'Kiểm tra đơn'}
              </Button>
            </View>
          </View>
        ) : (
          <Button disabled={busy || !quote} onPress={() => void submit()}>
            {busy ? 'Đang gửi đơn…' : 'Tạo đơn hàng'}
          </Button>
        )}
      </View>
    </KeyboardAvoidingView>
  );
}
