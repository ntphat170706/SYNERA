import React, { useRef, useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, Text, TextInput, View } from 'react-native';
import { useNavigation, type RouteProp } from '@react-navigation/native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Crypto from 'expo-crypto';
import * as Location from 'expo-location';
import type { RootStack } from '../navigation';
import { salesClient, useSession } from '../auth/session';
import {
  AmountRow,
  BodyText,
  Button,
  Field,
  Icon,
  kit,
  Section,
  styles,
  useRead,
} from '../components/ui';
import { SchedulePicker } from '../components/SchedulePicker';
import { useColors, money } from '../theme';
import { unitLabel } from '../domain/cart';
type Product = { id: number; code: string; name: string; baseUnit: string };
const day = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' });
export function Activity({ route }: { route: RouteProp<RootStack, 'Activity'> }) {
  const c = useColors(),
    insets = useSafeAreaInsets(),
    nav = useNavigation(),
    { user } = useSession(),
    { kind, storeId, name, visitId } = route.params;
  const [title, setTitle] = useState(''),
    [note, setNote] = useState(''),
    [when, setWhen] = useState(new Date(Date.now() + 3600000)),
    [quantities, setQuantities] = useState<Record<number, string>>({}),
    [fix, setFix] = useState<Location.LocationObject | null>(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(''),
    [success, setSuccess] = useState(false),
    [report, setReport] = useState<{
      visits: number;
      orders: number;
      amount: number;
      status: string;
    } | null>(null);
  const lock = useRef(false),
    command = useRef({ signature: '', key: '' });
  const products = useRead<Product[]>(kind === 'survey' ? '/catalog/skus' : null, []);
  const label = {
    checkin: 'Check-in điểm bán',
    checkout: 'Kết thúc lượt ghé',
    interaction: 'Ghi nhận nhu cầu',
    ticket: 'Ý kiến khách hàng',
    appointment: 'Lưu lời hẹn',
    survey: 'Ghi tồn điểm bán',
    report: 'Báo cáo cuối ngày',
  }[kind];
  async function locate() {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError('');
    try {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (permission.status !== 'granted')
        throw Error('Chưa được cấp vị trí. Cho phép vị trí trong cài đặt rồi thử lại.');
      setFix(await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High }));
    } catch (e) {
      setError(
        e instanceof Error ? e.message : 'Chưa lấy được vị trí. Hãy thử lại ở nơi có tín hiệu tốt.',
      );
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  async function send(preview = false) {
    if (lock.current || !user) return;
    lock.current = true;
    setBusy(true);
    setError('');
    try {
      let path = '',
        payload: unknown;
      if (kind === 'checkin') {
        if (!fix || !visitId) throw Error('Lấy vị trí trước khi check-in.');
        path = `/visits/${visitId}/check-in`;
        payload = {
          latitude: fix.coords.latitude,
          longitude: fix.coords.longitude,
          reason: note.trim() || null,
        };
      } else if (kind === 'checkout') {
        path = `/visits/${visitId}/check-out`;
        payload = { reason: note.trim() };
      } else if (kind === 'interaction') {
        if (!note.trim()) throw Error('Nhập nhu cầu hoặc kết quả trao đổi.');
        path = '/interactions';
        payload = { storeId, kind: 'VISIT', note: note.trim() };
      } else if (kind === 'ticket') {
        if (!title.trim() || !note.trim()) throw Error('Nhập tiêu đề và nội dung phản ánh.');
        path = '/tickets';
        payload = {
          nppId: user.nppId,
          storeId,
          title: title.trim(),
          category: 'OTHER',
          description: note.trim(),
        };
      } else if (kind === 'appointment') {
        if (!title.trim()) throw Error('Nhập nội dung lời hẹn.');
        if (when.getTime() <= Date.now()) throw Error('Chọn thời điểm hẹn trong tương lai.');
        path = '/appointments';
        payload = { storeId, title: title.trim(), dueAt: when.toISOString() };
      } else if (kind === 'survey') {
        const lines = products.data
          .filter((p) => quantities[p.id] !== undefined && quantities[p.id] !== '')
          .map((p) => ({ skuId: p.id, qtyBase: Number(quantities[p.id].replace(',', '.')) }));
        if (!lines.length || lines.some((l) => !Number.isFinite(l.qtyBase) || l.qtyBase < 0))
          throw Error('Nhập tồn hợp lệ cho ít nhất một sản phẩm. Số 0 là hết hàng.');
        path = '/stock-surveys';
        payload = { storeId, date: day(), lines, note: note.trim() };
      } else {
        path = '/sales/daily-report';
        payload = { day: day(), note: note.trim(), submit: !preview };
      }
      const signature = JSON.stringify({ path, payload });
      if (command.current.signature !== signature)
        command.current = { signature, key: Crypto.randomUUID() };
      const result = await salesClient.post<unknown>(path, payload, command.current.key);
      if (kind === 'report' && preview) {
        setReport(result as typeof report);
        return;
      }
      setSuccess(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Chưa lưu được. Vui lòng thử lại.');
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: c.canvas }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{ padding: 20, gap: 18, paddingBottom: 30 }}
      >
        {success ? (
          <View style={{ alignItems: 'center', paddingVertical: 40, gap: 18 }}>
            <View
              style={{
                width: 76,
                height: 76,
                borderRadius: 38,
                backgroundColor: c.accent,
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Icon name="checkmark-circle" size={46} />
            </View>
            <Text style={{ color: c.text, fontSize: 24, fontWeight: '700' }}>
              Đã lưu thành công
            </Text>
            <BodyText muted>{kind === 'report' ? 'Báo cáo đã được gửi.' : name}</BodyText>
            <Button onPress={() => nav.goBack()}>Quay lại điểm trước</Button>
          </View>
        ) : (
          <>
            <View style={{ gap: 8 }}>
              <Text style={[styles.sectionTitle, { color: c.text }]}>{label}</Text>
              <Text style={{ color: c.muted, fontSize: 15, lineHeight: 22 }}>
                {kind === 'report' ? `Ngày ${new Date().toLocaleDateString('vi-VN')}` : name}
              </Text>
            </View>
            {kind === 'checkin' && (
              <Section>
                <View style={{ padding: 18, gap: 16 }}>
                  <View style={kit.horizontal}>
                    <Icon name="navigate-circle-outline" size={32} />
                    <View style={{ flex: 1, gap: 4 }}>
                      <Text style={{ fontSize: 17, fontWeight: '600', color: c.text }}>
                        {fix ? 'Đã lấy vị trí' : 'Vị trí hiện tại'}
                      </Text>
                      <Text style={{ fontSize: 13, color: c.muted }}>
                        {fix
                          ? `Độ chính xác khoảng ${Math.round(fix.coords.accuracy || 0)} m`
                          : 'Lấy GPS tại điểm bán'}
                      </Text>
                    </View>
                  </View>
                  {fix && (
                    <Text style={{ fontSize: 14, color: c.muted }}>
                      {fix.coords.latitude.toFixed(5)}, {fix.coords.longitude.toFixed(5)}
                    </Text>
                  )}
                  <Button secondary disabled={busy} onPress={() => void locate()}>
                    {busy ? 'Đang lấy vị trí…' : fix ? 'Lấy lại vị trí' : 'Lấy vị trí GPS'}
                  </Button>
                </View>
              </Section>
            )}
            {['ticket', 'appointment'].includes(kind) && (
              <Field
                label={kind === 'ticket' ? 'Tiêu đề phản ánh' : 'Nội dung lời hẹn'}
                value={title}
                onChange={setTitle}
                placeholder={
                  kind === 'ticket' ? 'Vấn đề khách cần hỗ trợ' : 'Ví dụ: Gọi xác nhận lịch giao'
                }
              />
            )}
            {kind === 'appointment' && (
              <Section title="Ngày và giờ hẹn">
                <View style={{ padding: 16 }}>
                  <SchedulePicker value={when} onChange={setWhen} />
                </View>
              </Section>
            )}
            {kind === 'survey' && (
              <Section title="Tồn thực tế tại điểm">
                {products.error ? (
                  <View style={{ padding: 16 }}>
                    <Text style={{ color: c.danger }}>{products.error}</Text>
                    <Button secondary onPress={products.reload}>
                      Tải lại sản phẩm
                    </Button>
                  </View>
                ) : (
                  products.data.map((p) => (
                    <View
                      key={p.id}
                      style={[
                        kit.between,
                        {
                          padding: 14,
                          gap: 12,
                          borderBottomWidth: 0.5,
                          borderBottomColor: c.border,
                        },
                      ]}
                    >
                      <View style={{ flex: 1, gap: 4 }}>
                        <Text style={{ color: c.text, fontSize: 15, fontWeight: '600' }}>
                          {p.name}
                        </Text>
                        <Text style={{ color: c.muted, fontSize: 13 }}>
                          {unitLabel(p.baseUnit)}
                        </Text>
                      </View>
                      <TextInput
                        accessibilityLabel={`Tồn ${p.name}`}
                        placeholder="—"
                        placeholderTextColor={c.muted}
                        keyboardType="decimal-pad"
                        value={quantities[p.id] || ''}
                        onChangeText={(value) => {
                          if (/^\d*(?:[.,]\d{0,3})?$/.test(value))
                            setQuantities((q) => ({ ...q, [p.id]: value }));
                        }}
                        style={{
                          width: 80,
                          minHeight: 48,
                          borderWidth: 0.5,
                          borderColor: c.border,
                          borderRadius: 8,
                          textAlign: 'center',
                          fontSize: 18,
                          color: c.text,
                        }}
                      />
                    </View>
                  ))
                )}
              </Section>
            )}
            {kind !== 'appointment' && (
              <Field
                label={
                  kind === 'checkin'
                    ? 'Lý do nếu ngoài bán kính'
                    : kind === 'checkout'
                      ? 'Kết quả lượt ghé'
                      : kind === 'ticket'
                        ? 'Nội dung phản ánh'
                        : kind === 'report'
                          ? 'Ghi chú cuối ngày'
                          : kind === 'survey'
                            ? 'Ghi chú khảo sát'
                            : 'Nhu cầu / kết quả trao đổi'
                }
                value={note}
                onChange={setNote}
                multiline
                placeholder={
                  kind === 'checkout' ? 'Đã trao đổi gì, có đơn hay chưa mua…' : 'Nhập nội dung…'
                }
              />
            )}
            {report && (
              <Section title="Số liệu máy chủ">
                <View style={{ padding: 16 }}>
                  <AmountRow label="Lượt đã check-in" value={String(report.visits)} />
                  <AmountRow label="Số đơn" value={String(report.orders)} />
                  <AmountRow label="Giá trị đơn không huỷ" value={money(report.amount)} />
                </View>
              </Section>
            )}
            {Boolean(error) && (
              <Text
                accessibilityRole="alert"
                style={{ color: c.danger, fontSize: 15, lineHeight: 22 }}
              >
                {error}
              </Text>
            )}
          </>
        )}
      </ScrollView>
      {!success && (
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
          {kind === 'report' && (
            <Button secondary disabled={busy} onPress={() => void send(true)}>
              {busy ? 'Đang lấy số liệu…' : 'Lấy số liệu báo cáo'}
            </Button>
          )}
          <Button
            disabled={busy || (kind === 'checkin' && !fix) || (kind === 'report' && !report)}
            onPress={() => void send()}
          >
            {busy
              ? 'Đang lưu…'
              : kind === 'report'
                ? 'Gửi báo cáo'
                : kind === 'checkin'
                  ? 'Xác nhận check-in'
                  : kind === 'checkout'
                    ? 'Kết thúc lượt ghé'
                    : 'Lưu thông tin'}
          </Button>
        </View>
      )}
    </KeyboardAvoidingView>
  );
}
