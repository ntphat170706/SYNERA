import React, { useRef, useState } from 'react';
import { ScrollView, View, Text } from 'react-native';
import { useNavigation, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import * as Crypto from 'expo-crypto';
import { Button, Field, Section, State, useRead } from '../components/ui';
import { SchedulePicker } from '../components/SchedulePicker';
import { salesClient } from '../auth/session';
import { useColors } from '../theme';
import { unitLabel } from '../domain/cart';
import type { RootStack } from '../navigation';
import type { OrderLine, SalesOrder } from '../domain/contracts';
export function ReplacementRequest({
  route,
}: {
  route: RouteProp<RootStack, 'ReplacementRequest'>;
}) {
  const c = useColors(),
    nav = useNavigation<NativeStackNavigationProp<RootStack>>();
  const data = useRead<(SalesOrder & { lines: OrderLine[] }) | null>(
    `/sales-orders/${route.params.orderId}`,
    null,
  );
  const catalog = useRead<{ id: number; name: string }[]>('/catalog/skus', []);
  const [quantities, setQuantities] = useState<Record<number, string>>({}),
    [reason, setReason] = useState(''),
    [due, setDue] = useState(new Date(Date.now() + 3600000)),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('');
  const pending = useRef<{ signature: string; key: string } | null>(null);
  const lines = (data.data?.lines || []).filter((l) => Number(l.qtyAvailableToReplace) > 0);
  async function submit() {
    if (busy || !data.data) return;
    setBusy(true);
    setError('');
    try {
      const input = Object.fromEntries(
        lines
          .map((l) => [
            l.id,
            Number((quantities[l.id] ?? String(l.qtyAvailableToReplace)).replace(',', '.')),
          ])
          .filter(([, qty]) => Number(qty) > 0),
      );
      if (!Object.keys(input).length || due.getTime() <= Date.now())
        throw Error('Chọn lượng cần giao bù và hạn xử lý trong tương lai.');
      const body = {
          orderId: route.params.orderId,
          quantities: input,
          dueAt: due.toISOString(),
          reason: reason.trim(),
        },
        signature = JSON.stringify(body);
      if (pending.current?.signature !== signature)
        pending.current = { signature, key: Crypto.randomUUID() };
      await salesClient.post('/sales-replacements', body, pending.current.key);
      pending.current = null;
      nav.replace('Inbox');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Chưa gửi được đề xuất.');
      data.reload();
    } finally {
      setBusy(false);
    }
  }
  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: c.canvas }}
      contentContainerStyle={{ padding: 16, gap: 18, paddingBottom: 40 }}
    >
      <State {...data} retry={data.reload} />
      <State {...catalog} retry={catalog.reload} />
      {error && <State loading={false} error={error} retry={() => setError('')} />}
      <Text style={{ fontSize: 23, fontWeight: '700', color: c.text }}>
        {data.data?.code || 'Đơn hàng'}
      </Text>
      <Section title="Phần thiếu còn có thể đề xuất">
        <View style={{ padding: 18, gap: 18 }}>
          {lines.map((l) => (
            <Field
              key={l.id}
              label={`${catalog.data.find((p) => p.id === l.skuId)?.name || `SKU ${l.skuId}`} · ${unitLabel(l.uom)} · tối đa ${l.qtyAvailableToReplace}`}
              value={quantities[l.id] ?? String(l.qtyAvailableToReplace)}
              keyboardType="decimal-pad"
              onChange={(v) => setQuantities((q) => ({ ...q, [l.id]: v }))}
            />
          ))}
          {!data.loading && !lines.length && (
            <Text style={{ color: c.muted }}>Không còn lượng thiếu chưa được đề xuất.</Text>
          )}
        </View>
      </Section>
      <Section title="Lời hẹn và phương án">
        <View style={{ padding: 18, gap: 18 }}>
          <Field label="Ghi chú" value={reason} onChange={setReason} multiline />
          <Text style={{ color: c.text }}>Hạn xử lý / báo lại khách</Text>
          <SchedulePicker value={due} onChange={setDue} />
          <Text style={{ color: c.muted, lineHeight: 22 }}>
            NPP sẽ kiểm tra, duyệt và giữ hàng. Đề xuất này không tạo đơn bán hoặc khoản thu mới.
          </Text>
          <Button disabled={busy || !lines.length} onPress={() => void submit()}>
            {busy ? 'Đang gửi...' : 'Gửi đề xuất giao bù'}
          </Button>
        </View>
      </Section>
    </ScrollView>
  );
}
