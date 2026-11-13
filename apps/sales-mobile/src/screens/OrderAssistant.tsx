import React, { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, View } from 'react-native';
import { useNavigation, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { RootStack } from '../navigation';
import { useSession } from '../auth/session';
import type { Offering, Store } from '../domain/contracts';
import { suggestOrder, type SuggestedLine } from '../domain/orderAssistant';
import { unitLabel } from '../domain/cart';
import {
  Button,
  Field,
  Icon,
  kit,
  ListRow,
  ProductPicture,
  Search,
  Section,
  State,
  useRead,
} from '../components/ui';
import { useColors } from '../theme';
export function OrderAssistant({ route }: { route: RouteProp<RootStack, 'OrderAssistant'> }) {
  const c = useColors(),
    { user } = useSession(),
    insets = useSafeAreaInsets(),
    nav = useNavigation<NativeStackNavigationProp<RootStack>>(),
    { storeId, name } = route.params;
  const offers = useRead<Offering[]>(
    `/catalog/offerings?nppId=${user!.nppId}&storeId=${storeId}`,
    [],
  );
  const [text, setText] = useState(''),
    [lines, setLines] = useState<SuggestedLine[]>([]),
    [unmatched, setUnmatched] = useState<string[]>([]),
    [analyzed, setAnalyzed] = useState(false);
  function analyze() {
    const result = suggestOrder(text, offers.data);
    setLines(result.lines);
    setUnmatched(result.unmatched);
    setAnalyzed(true);
  }
  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: c.canvas }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{ padding: 18, paddingBottom: 30, gap: 16 }}
      >
        <View style={{ gap: 12, paddingVertical: 4 }}>
          <View style={kit.horizontal}>
            <View style={[kit.actionIcon, { backgroundColor: c.metricBlue }]}>
              <Icon name="sparkles-outline" />
            </View>
            <View style={{ flex: 1, gap: 4 }}>
              <Text style={{ fontSize: 22, fontWeight: '700', color: c.text }}>
                Bạn muốn lên đơn gì?
              </Text>
              <Text style={{ fontSize: 14, color: c.muted }}>{name}</Text>
            </View>
          </View>
          <Text style={{ fontSize: 15, lineHeight: 23, color: c.muted }}>
            Nhập yêu cầu của khách, mình sẽ sắp thành các dòng hàng để bạn kiểm tra.
          </Text>
        </View>
        <Section tone="blue">
          <View style={{ padding: 16, gap: 14 }}>
            <Field
              label="Yêu cầu đặt hàng"
              value={text}
              onChange={(value) => {
                setText(value);
                setAnalyzed(false);
                setLines([]);
                setUnmatched([]);
              }}
              placeholder="Ví dụ: 2 thùng sữa tươi và 3 lốc trà xanh"
              multiline
            />
            <Text style={{ fontSize: 12, lineHeight: 18, color: c.muted }}>
              Bạn có thể dùng micro trên bàn phím iPhone để nhập bằng giọng nói.
            </Text>
            <Button
              disabled={!text.trim() || offers.loading || Boolean(offers.error)}
              onPress={analyze}
            >
              Tạo gợi ý đơn hàng
            </Button>
          </View>
        </Section>
        {!text && (
          <View style={{ gap: 9 }}>
            <Text style={{ fontSize: 14, color: c.muted }}>Thử một yêu cầu</Text>
            {['2 thùng sữa tươi và 3 lốc trà xanh', '1 thùng nước khoáng và 2 lốc nước ép cam'].map(
              (example) => (
                <Pressable
                  key={example}
                  accessibilityRole="button"
                  accessibilityLabel={example}
                  onPress={() => setText(example)}
                  style={{
                    padding: 14,
                    minHeight: 48,
                    backgroundColor: c.accent,
                    borderRadius: 12,
                  }}
                >
                  <Text style={{ fontSize: 14, lineHeight: 21, color: c.primary }}>{example}</Text>
                </Pressable>
              ),
            )}
          </View>
        )}
        <State loading={offers.loading} error={offers.error} retry={offers.reload} />
        {analyzed && (
          <>
            <Section title={`Gợi ý đơn hàng · ${lines.length} dòng`}>
              {lines.map((line, index) => (
                <View
                  key={`${line.skuId}:${line.uom}`}
                  style={{
                    padding: 14,
                    borderBottomWidth: 0.5,
                    borderBottomColor: c.border,
                    gap: 12,
                  }}
                >
                  <View style={kit.horizontal}>
                    <ProductPicture code={line.code} size={54} />
                    <View style={{ flex: 1, gap: 5 }}>
                      <Text style={{ fontSize: 16, fontWeight: '600', color: c.text }}>
                        {line.name}
                      </Text>
                      <Text style={{ fontSize: 13, color: c.muted }}>
                        {line.code} · {unitLabel(line.uom)}
                      </Text>
                    </View>
                  </View>
                  <View style={kit.between}>
                    <Text style={{ fontSize: 14, color: c.muted }}>Số lượng</Text>
                    <View style={kit.horizontal}>
                      <Pressable
                        accessibilityRole="button"
                        accessibilityLabel={`Giảm gợi ý ${line.name}`}
                        disabled={line.qty <= 1}
                        onPress={() =>
                          setLines((previous) =>
                            previous.map((l, i) =>
                              i === index ? { ...l, qty: Math.max(1, l.qty - 1) } : l,
                            ),
                          )
                        }
                        style={{
                          width: 44,
                          height: 44,
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <Icon name="remove-circle-outline" />
                      </Pressable>
                      <Text
                        style={{
                          fontSize: 20,
                          fontWeight: '700',
                          color: c.primary,
                          minWidth: 28,
                          textAlign: 'center',
                        }}
                      >
                        {line.qty}
                      </Text>
                      <Pressable
                        accessibilityRole="button"
                        accessibilityLabel={`Tăng gợi ý ${line.name}`}
                        onPress={() =>
                          setLines((previous) =>
                            previous.map((l, i) => (i === index ? { ...l, qty: l.qty + 1 } : l)),
                          )
                        }
                        style={{
                          width: 44,
                          height: 44,
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <Icon name="add-circle" />
                      </Pressable>
                    </View>
                  </View>
                </View>
              ))}
              {!lines.length && (
                <View style={{ padding: 18, gap: 8 }}>
                  <Text style={{ fontSize: 16, color: c.text }}>
                    Chưa tìm được dòng hàng phù hợp.
                  </Text>
                  <Text style={{ fontSize: 14, color: c.muted, lineHeight: 22 }}>
                    Thêm số lượng, đơn vị và tên sản phẩm trong danh mục rồi thử lại.
                  </Text>
                </View>
              )}
            </Section>
            {unmatched.length > 0 && (
              <Section title="Cần bạn làm rõ">
                <View style={{ padding: 16, gap: 8 }}>
                  {unmatched.map((value, index) => (
                    <Text key={index} style={{ fontSize: 14, lineHeight: 22, color: c.muted }}>
                      {value}
                    </Text>
                  ))}
                  <Text style={{ fontSize: 13, color: c.primary }}>
                    Sửa phần yêu cầu hoặc chọn thủ công trong giỏ hàng.
                  </Text>
                </View>
              </Section>
            )}
          </>
        )}
      </ScrollView>
      {lines.length > 0 && (
        <View
          style={[
            kit.footer,
            {
              backgroundColor: c.surface,
              borderColor: c.border,
              paddingBottom: Math.max(insets.bottom, 16),
            },
          ]}
        >
          <Button
            onPress={() =>
              nav.navigate('Compose', {
                storeId,
                storeName: name,
                initialLines: lines.map(({ skuId, uom, qty }) => ({ skuId, uom, qty })),
              })
            }
          >
            {`Đưa ${lines.length} dòng vào giỏ nháp`}
          </Button>
        </View>
      )}
    </KeyboardAvoidingView>
  );
}
export function AiStorePicker({ route }: { route: RouteProp<RootStack, 'AiStorePicker'> }) {
  const c = useColors(),
    stores = useRead<Store[]>('/stores', []),
    nav = useNavigation<NativeStackNavigationProp<RootStack>>(),
    [query, setQuery] = useState('');
  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: c.canvas }}
      contentContainerStyle={{ padding: 18 }}
    >
      <Text style={{ fontSize: 20, fontWeight: '700', color: c.text, marginBottom: 8 }}>
        {route.params.kind === 'order' ? 'AI hỗ trợ lên đơn' : 'AI kiểm tra trưng bày'}
      </Text>
      <Text style={{ fontSize: 14, color: c.muted, marginBottom: 18 }}>
        Chọn điểm bán để bắt đầu.
      </Text>
      <Search value={query} onChange={setQuery} placeholder="Tìm điểm bán…" />
      <View style={{ marginTop: 18 }}>
        <State loading={stores.loading} error={stores.error} retry={stores.reload} />
        <Section>
          {stores.data
            .filter((s) =>
              `${s.name} ${s.phone}`
                .toLocaleLowerCase('vi')
                .includes(query.toLocaleLowerCase('vi')),
            )
            .map((store) => (
              <ListRow
                key={store.id}
                title={store.name}
                detail={store.address}
                onPress={() =>
                  nav.replace(route.params.kind === 'order' ? 'OrderAssistant' : 'DisplayAudit', {
                    storeId: store.id,
                    name: store.name,
                  })
                }
              />
            ))}
        </Section>
      </View>
    </ScrollView>
  );
}
