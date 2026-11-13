import React, { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Image,
  Platform,
  Pressable,
  ScrollView,
  Text,
  View,
} from 'react-native';
import { useNavigation, type RouteProp } from '@react-navigation/native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Crypto from 'expo-crypto';
import * as ImagePicker from 'expo-image-picker';
import type { RootStack } from '../navigation';
import { salesClient, useSession } from '../auth/session';
import { Button, Icon, kit, Section, State, useRead } from '../components/ui';
import { useColors } from '../theme';
type Criteria = { id: number; name: string; rules: Record<string, unknown> };
type Audit = {
  id: number;
  status: string;
  aiScore: number | null;
  finalScore: number | null;
  provider: string;
  findings?: { duplicateImage?: boolean };
};
export function DisplayAudit({ route }: { route: RouteProp<RootStack, 'DisplayAudit'> }) {
  const c = useColors(),
    insets = useSafeAreaInsets(),
    nav = useNavigation(),
    { user } = useSession(),
    { storeId, name } = route.params;
  const criteria = useRead<Criteria[]>('/display-audits/criteria', []),
    [criteriaId, setCriteriaId] = useState<number | null>(null),
    [photo, setPhoto] = useState<ImagePicker.ImagePickerAsset | null>(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(''),
    [result, setResult] = useState<Audit | null>(null),
    [auditId, setAuditId] = useState<number | null>(null);
  const lock = useRef(false),
    attachment = useRef<number | null>(null),
    key = useRef(Crypto.randomUUID());
  const selected = criteriaId || criteria.data[0]?.id;
  useEffect(() => {
    if (!auditId) return;
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let attempts = 0;
    const poll = async () => {
      try {
        const rows = await salesClient.get<Audit[]>('/display-audits');
        if (!active) return;
        const row = rows.find((a) => a.id === auditId);
        if (row) {
          setResult(row);
          if (['NEEDS_REVIEW', 'AI_SCORED', 'REVIEWED'].includes(row.status)) return;
        }
        if (attempts++ < 15) timer = setTimeout(poll, 2000);
      } catch (e) {
        if (active) setError(e instanceof Error ? e.message : 'Chưa tải được kết quả.');
      }
    };
    void poll();
    return () => {
      active = false;
      if (timer) clearTimeout(timer);
    };
  }, [auditId]);
  async function choose(camera = false) {
    if (lock.current) return;
    lock.current = true;
    setError('');
    try {
      if (camera) {
        const p = await ImagePicker.requestCameraPermissionsAsync();
        if (!p.granted) throw Error('Cho phép camera trong cài đặt để chụp ảnh kệ hàng.');
      }
      const response = camera
        ? await ImagePicker.launchCameraAsync({ mediaTypes: ['images'], quality: 0.8 })
        : await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 });
      if (response.canceled) return;
      const asset = response.assets[0];
      if (asset.fileSize && asset.fileSize > 5 * 1024 * 1024)
        throw Error('Ảnh vượt 5 MB. Chọn ảnh nhỏ hơn rồi thử lại.');
      setPhoto(asset);
      setResult(null);
      setAuditId(null);
      attachment.current = null;
      key.current = Crypto.randomUUID();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Chưa mở được ảnh.');
    } finally {
      lock.current = false;
    }
  }
  async function submit() {
    if (lock.current || !photo || !selected || !user) return;
    lock.current = true;
    setBusy(true);
    setError('');
    try {
      if (!attachment.current) {
        const form = new FormData();
        if (Platform.OS === 'web') {
          const file = photo.file || (await fetch(photo.uri).then((r) => r.blob()));
          if (!file) throw Error('Không đọc được ảnh đã chọn. Vui lòng chọn lại.');
          form.append('file', file, photo.fileName || 'display.jpg');
        } else
          form.append('file', {
            uri: photo.uri,
            name: photo.fileName || 'display.jpg',
            type: photo.mimeType || 'image/jpeg',
          } as unknown as Blob);
        const value = await salesClient.post<{ id: number }>(
          `/attachments/upload?nppId=${user.nppId}`,
          form,
          key.current,
        );
        attachment.current = value.id;
      }
      const value = await salesClient.post<Audit>(
        '/display-audits',
        { storeId, criteriaId: selected, attachmentId: attachment.current },
        key.current,
      );
      setResult(value);
      setAuditId(value.id);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Chưa gửi được ảnh. Vui lòng thử lại.');
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  const score = result?.finalScore ?? result?.aiScore;
  const scoreLabel =
    result?.finalScore != null
      ? 'Điểm đã xác nhận'
      : result?.provider?.startsWith('MOCK')
        ? 'Điểm tham khảo'
        : 'Điểm đề xuất';
  return (
    <View style={{ flex: 1, backgroundColor: c.canvas }}>
      <ScrollView contentContainerStyle={{ padding: 18, paddingBottom: 30, gap: 16 }}>
        <View style={{ gap: 9 }}>
          <View style={kit.horizontal}>
            <View style={[kit.actionIcon, { backgroundColor: c.metricBlue }]}>
              <Icon name="scan-outline" />
            </View>
            <Text style={{ flex: 1, fontSize: 22, fontWeight: '700', color: c.text }}>
              AI kiểm tra trưng bày
            </Text>
          </View>
          <Text style={{ fontSize: 14, color: c.muted }}>{name}</Text>
        </View>
        <Section>
          <View style={{ padding: 16, gap: 13 }}>
            {photo ? (
              <Image
                source={{ uri: photo.uri }}
                accessibilityLabel="Ảnh kệ hàng đã chọn"
                style={{ width: '100%', height: 230, borderRadius: 10, backgroundColor: c.canvas }}
                resizeMode="contain"
              />
            ) : (
              <View
                style={{
                  height: 190,
                  alignItems: 'center',
                  justifyContent: 'center',
                  backgroundColor: c.routeBlue,
                  borderRadius: 12,
                  gap: 12,
                }}
              >
                <Icon name="camera-outline" size={44} />
                <Text style={{ fontSize: 15, color: c.muted }}>
                  Chụp toàn bộ mặt kệ, rõ nhãn sản phẩm
                </Text>
              </View>
            )}
            <View style={kit.horizontal}>
              <View style={{ flex: 1 }}>
                <Button secondary disabled={busy} onPress={() => void choose(true)}>
                  Chụp ảnh
                </Button>
              </View>
              <View style={{ flex: 1 }}>
                <Button secondary disabled={busy} onPress={() => void choose()}>
                  Chọn ảnh
                </Button>
              </View>
            </View>
          </View>
        </Section>
        <Section title="Bộ tiêu chí">
          <View style={{ padding: 14, gap: 7 }}>
            {criteria.data.map((item) => (
              <Pressable
                key={item.id}
                accessibilityRole="radio"
                accessibilityState={{
                  checked: item.id === selected,
                  disabled: busy || Boolean(auditId),
                }}
                disabled={busy || Boolean(auditId)}
                onPress={() => setCriteriaId(item.id)}
                style={{
                  minHeight: 48,
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: 10,
                  paddingVertical: 8,
                }}
              >
                <Icon name={item.id === selected ? 'radio-button-on' : 'radio-button-off'} />
                <Text style={{ flex: 1, fontSize: 15, lineHeight: 22, color: c.text }}>
                  {item.name}
                </Text>
              </Pressable>
            ))}
            <State loading={criteria.loading} error={criteria.error} retry={criteria.reload} />
            {!criteria.loading && !criteria.data.length && (
              <Text style={{ color: c.muted, lineHeight: 22 }}>
                Chưa có bộ tiêu chí cho điểm bán này. Cần người phụ trách thiết lập trước khi gửi
                ảnh.
              </Text>
            )}
          </View>
        </Section>
        {result && (
          <Section title="Kết quả kiểm tra" tone="blue">
            <View style={{ padding: 18, gap: 14 }}>
              {score != null ? (
                <>
                  <View style={[kit.between, { alignItems: 'baseline' }]}>
                    <Text style={{ fontSize: 16, color: c.text }}>{scoreLabel}</Text>
                    <Text
                      accessibilityLabel={`${scoreLabel} ${score} trên 100`}
                      style={{ fontSize: 38, fontWeight: '700', color: c.primary }}
                    >
                      {Math.round(score)}
                      <Text style={{ fontSize: 16, color: c.muted }}> /100</Text>
                    </Text>
                  </View>
                  <View style={{ height: 7, backgroundColor: c.metricBlue, borderRadius: 4 }}>
                    <View
                      style={{
                        width: `${Math.max(0, Math.min(100, score))}%`,
                        height: 7,
                        borderRadius: 4,
                        backgroundColor: c.primary,
                      }}
                    />
                  </View>
                </>
              ) : ['SUBMITTED', 'PROCESSING'].includes(result.status) ? (
                <View style={kit.horizontal}>
                  <ActivityIndicator color={c.primary} />
                  <Text style={{ color: c.primary }}>Đang kiểm tra ảnh…</Text>
                </View>
              ) : (
                <Text style={{ color: c.primary, fontSize: 18, fontWeight: '600' }}>
                  Ảnh đã được tiếp nhận
                </Text>
              )}
              <View style={kit.horizontal}>
                <Icon
                  name={
                    result.status === 'REVIEWED'
                      ? 'checkmark-circle-outline'
                      : 'person-circle-outline'
                  }
                />
                <Text style={{ fontSize: 15, color: c.text, flex: 1 }}>
                  {result.status === 'REVIEWED'
                    ? 'Đã được người phụ trách xác nhận'
                    : 'Chờ người phụ trách xác nhận'}
                </Text>
              </View>
              {result.findings?.duplicateImage && (
                <Text style={{ color: c.muted, lineHeight: 21 }}>
                  Ảnh này đã được gửi trước đó. Người phụ trách sẽ kiểm tra lại.
                </Text>
              )}
              <Text style={{ color: c.muted, fontSize: 13, lineHeight: 21 }}>
                Người phụ trách sẽ đối chiếu ảnh với bộ tiêu chí trước khi xác nhận điểm.
              </Text>
              <Button
                secondary
                onPress={() => {
                  setResult(null);
                  setAuditId(null);
                  setPhoto(null);
                  attachment.current = null;
                  key.current = Crypto.randomUUID();
                }}
              >
                Kiểm tra ảnh khác
              </Button>
            </View>
          </Section>
        )}
        {Boolean(error) && (
          <Text accessibilityRole="alert" style={{ color: c.danger, fontSize: 15, lineHeight: 23 }}>
            {error}
          </Text>
        )}
      </ScrollView>
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
        {auditId ? (
          <Button onPress={() => nav.goBack()}>Quay lại điểm bán</Button>
        ) : (
          <Button disabled={busy || !photo || !selected} onPress={() => void submit()}>
            {busy ? 'Đang gửi ảnh…' : 'Gửi ảnh kiểm tra'}
          </Button>
        )}
      </View>
    </View>
  );
}
