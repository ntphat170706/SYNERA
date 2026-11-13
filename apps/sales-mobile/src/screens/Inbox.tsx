import React, { useCallback, useRef, useState } from 'react';
import { ScrollView, View, Text, RefreshControl } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import * as Crypto from 'expo-crypto';
import { ActionRow, Button, Field, Section, State, useRead } from '../components/ui';
import { salesClient } from '../auth/session';
import { useColors, statusLabel } from '../theme';
import type { RootStack } from '../navigation';
import { noticeSummary } from '../domain/notifications';
type Row = {
  id: number;
  type?: string;
  payload?: string;
  read?: boolean;
  orderId?: number;
  status?: string;
  reason?: string;
  dueAt?: string;
  notifiedAt?: string;
  title?: string;
  customerFeedback?: string;
  referenceType?: string;
  referenceId?: number;
  storeId?: number;
  name?: string;
};
export function Inbox() {
  const nav = useNavigation<NativeStackNavigationProp<RootStack>>(),
    c = useColors();
  const notifications = useRead<Row[]>('/notifications', []),
    cases = useRead<Row[]>('/sales-replacements', []),
    tasks = useRead<Row[]>('/tasks', []),
    stores = useRead<Row[]>('/stores', []);
  const storeName = (id?: number) => stores.data.find((s) => s.id === id)?.name || 'Điểm bán';
  const [selectedId, setSelectedId] = useState<number | null>(null),
    [note, setNote] = useState(''),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(''),
    [showHistory, setShowHistory] = useState(false);
  const scroll = useRef<React.ComponentRef<typeof ScrollView>>(null);
  React.useEffect(() => {
    if (selectedId) scroll.current?.scrollTo({ y: 0, animated: true });
  }, [selectedId]);
  const pending = useRef<{ signature: string; key: string } | null>(null);
  const selected = cases.data.find((r) => r.id === selectedId) || null;
  const reload = useCallback(() => {
    notifications.reload();
    cases.reload();
    tasks.reload();
  }, [notifications.reload, cases.reload, tasks.reload]);
  async function post(path: string, body: unknown) {
    const signature = JSON.stringify([path, body]);
    if (pending.current?.signature !== signature)
      pending.current = { signature, key: Crypto.randomUUID() };
    await salesClient.post(path, body, pending.current.key);
    pending.current = null;
  }
  async function feedback() {
    if (!selected || !note.trim() || busy) return;
    setBusy(true);
    setError('');
    try {
      await post(`/sales-replacements/${selected.id}/customer-feedback`, {
        note: note.trim(),
        expectedStatus: selected.status,
      });
      setSelectedId(null);
      setNote('');
      reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Chưa lưu được kết quả.');
    } finally {
      setBusy(false);
    }
  }
  async function open(n: Row) {
    let payload: { orderId?: number; replacementId?: number } = {};
    try {
      const parsed = JSON.parse(n.payload || '{}');
      payload = parsed && typeof parsed === 'object' ? parsed : {};
    } catch {}
    if (payload.replacementId) {
      setSelectedId(payload.replacementId);
      setNote('');
    } else if (payload.orderId)
      nav.navigate('Order', { id: payload.orderId, name: `Đơn #${payload.orderId}` });
    try {
      if (!n.read) await salesClient.post(`/notifications/${n.id}/read`, {}, Crypto.randomUUID());
      notifications.reload();
    } catch (e) {
      setError('Đã mở nội dung. Chưa đánh dấu đã đọc; chạm thông báo để thử lại.');
    }
  }
  return (
    <ScrollView
      ref={scroll}
      style={{ flex: 1, backgroundColor: c.canvas }}
      contentContainerStyle={{ padding: 16, paddingBottom: 40, gap: 18 }}
      refreshControl={<RefreshControl refreshing={notifications.loading} onRefresh={reload} />}
    >
      <Text style={{ fontSize: 25, fontWeight: '700', color: c.text }}>Việc cần làm</Text>
      <State {...stores} retry={stores.reload} />
      {error && <State loading={false} error={error} retry={() => setError('')} />}

      {selected && (
        <Section title={`Kết quả liên hệ · GB-${selected.id}`}>
          <View style={{ padding: 18, gap: 14 }}>
            <Text style={{ color: c.text, lineHeight: 23 }}>{selected.reason}</Text>
            <Text style={{ color: c.muted }}>
              Hạn: {selected.dueAt ? new Date(selected.dueAt).toLocaleString('vi-VN') : 'Chưa có'}
            </Text>
            {selected.notifiedAt ? (
              <Text style={{ color: c.text }}>{selected.customerFeedback}</Text>
            ) : (
              <>
                <Field
                  label="Nội dung đã báo và phản hồi của khách"
                  value={note}
                  onChange={setNote}
                  multiline
                />
                <Button disabled={busy || !note.trim()} onPress={() => void feedback()}>
                  {busy ? 'Đang lưu...' : 'Lưu kết quả liên hệ'}
                </Button>
              </>
            )}
            <Button
              secondary
              onPress={() =>
                nav.navigate('Order', { id: selected.orderId!, name: `Đơn #${selected.orderId}` })
              }
            >
              Xem đơn gốc
            </Button>
          </View>
        </Section>
      )}

      <Section title="Công việc đang mở">
        <State {...tasks} retry={tasks.reload} />
        {!tasks.loading && !tasks.error && !tasks.data.some((t) => t.status === 'OPEN') && (
          <Text style={{ padding: 18, color: c.muted }}>Không có công việc đang mở.</Text>
        )}
        {tasks.data
          .filter((t) => t.status === 'OPEN')
          .map((t) =>
            t.referenceType === 'SALES_REPLACEMENT' ? (
              <ActionRow
                key={t.id}
                title={t.title || 'Báo lại khách'}
                detail={t.dueAt ? new Date(t.dueAt).toLocaleString('vi-VN') : ''}
                icon="chatbubbles-outline"
                onPress={() => {
                  setSelectedId(t.referenceId || null);
                  setNote('');
                  setError('');
                }}
              />
            ) : (
              <View
                key={t.id}
                style={{ padding: 18, borderBottomWidth: 1, borderColor: c.border, gap: 7 }}
              >
                <Text style={{ color: c.text, fontWeight: '600' }}>{t.title}</Text>
                <Text style={{ color: c.muted }}>
                  {t.dueAt ? new Date(t.dueAt).toLocaleString('vi-VN') : ''}
                </Text>
              </View>
            ),
          )}
      </Section>

      <Section title="Phần thiếu cần theo dõi">
        <State {...cases} retry={cases.reload} />
        {cases.data
          .filter(
            (r) =>
              showHistory ||
              (r.status !== 'CANCELLED' && (r.status !== 'RECEIVED' || !r.notifiedAt)),
          )
          .map((r) => (
            <ActionRow
              key={r.id}
              title={`${storeName(r.storeId)} · GB-${r.id}`}
              detail={`${statusLabel(r.status || '')} · ${r.notifiedAt ? 'Đã báo khách' : 'Cần báo lại khách'}`}
              icon="chatbubbles-outline"
              onPress={() => {
                setSelectedId(r.id);
                setNote('');
                setError('');
              }}
            />
          ))}
        {!cases.loading &&
          !cases.error &&
          !cases.data.some(
            (r) =>
              showHistory ||
              (r.status !== 'CANCELLED' && (r.status !== 'RECEIVED' || !r.notifiedAt)),
          ) && <Text style={{ padding: 18, color: c.muted }}>Không có phiếu cần xử lý.</Text>}
      </Section>
      <Button secondary onPress={() => setShowHistory((v) => !v)}>
        {showHistory ? 'Ẩn phiếu đã hoàn tất' : 'Xem cả phiếu đã hoàn tất'}
      </Button>
      <Section title="Cập nhật từ nhà phân phối">
        <State {...notifications} retry={notifications.reload} />
        {notifications.data.length ? (
          notifications.data
            .slice()
            .sort((a, b) => b.id - a.id)
            .slice(0, 30)
            .map((n) => (
              <ActionRow
                key={n.id}
                title={noticeSummary(n).title}
                detail={[noticeSummary(n).description, n.read ? 'Đã đọc' : 'Chưa đọc']
                  .filter(Boolean)
                  .join(' · ')}
                icon="notifications-outline"
                onPress={() => void open(n)}
              />
            ))
        ) : !notifications.loading && !notifications.error ? (
          <Text style={{ padding: 18, color: c.muted }}>Chưa có thông báo.</Text>
        ) : null}
      </Section>
    </ScrollView>
  );
}
