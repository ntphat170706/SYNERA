import React, { useCallback, useEffect, useState, useRef } from 'react';
import {
  ActivityIndicator,
  AppState,
  Image,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';
import { salesClient } from '../auth/session';
import { useColors, statusLabel } from '../theme';
import { useIsFocused } from '@react-navigation/native';
export function useRead<T>(path: string | null, initial: T) {
  const focused = useIsFocused(),
    pending = useRef(false),
    request = useRef(0),
    loaded = useRef<string | null>(null);
  const [data, setData] = useState(initial),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(''),
    [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true;
    if (!path) {
      setLoading(false);
      return;
    }
    const id = ++request.current;
    pending.current = true;
    setLoading(loaded.current !== path);
    setError('');
    salesClient
      .get<T>(path)
      .then((value) => {
        if (active) {
          setData(value);
          loaded.current = path;
        }
      })
      .catch((e) => {
        if (active) setError(e.message);
      })
      .finally(() => {
        if (request.current === id) pending.current = false;
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [path, version]);
  const reload = useCallback(() => setVersion((v) => v + 1), []);
  useEffect(() => {
    if (!path || !focused) return;
    const update = () => {
      if (!pending.current && AppState.currentState === 'active') reload();
    };
    const timer = setInterval(
      update,
      /^\/(notifications|tasks|sales-orders|sales-replacements)(\/|\?|$)/.test(path) ? 2000 : 20000,
    );
    const listener = AppState.addEventListener('change', (state) => {
      if (state === 'active') update();
    });
    update();
    return () => {
      clearInterval(timer);
      listener.remove();
    };
  }, [path, focused, reload]);
  return { data, loading, error, reload };
}
export function BodyText({
  children,
  muted = false,
}: {
  children: React.ReactNode;
  muted?: boolean;
}) {
  const c = useColors();
  return <Text style={[styles.body, { color: muted ? c.muted : c.text }]}>{children}</Text>;
}
export function Button({
  children,
  onPress,
  disabled = false,
  secondary = false,
}: {
  children: string;
  onPress: () => void;
  disabled?: boolean;
  secondary?: boolean;
}) {
  const c = useColors();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        {
          backgroundColor: secondary ? c.surface : c.primary,
          borderColor: secondary ? c.border : c.primary,
          opacity: disabled ? 0.55 : pressed ? 0.8 : 1,
        },
      ]}
    >
      <Text style={[styles.buttonLabel, { color: secondary ? c.primary : c.onPrimary }]}>
        {children}
      </Text>
    </Pressable>
  );
}
export function State({
  loading,
  error,
  retry,
  empty = false,
}: {
  loading: boolean;
  error: string;
  retry: () => void;
  empty?: boolean;
}) {
  const c = useColors();
  if (loading)
    return (
      <View style={styles.state}>
        <ActivityIndicator accessibilityLabel="Đang tải" color={c.primary} />
      </View>
    );
  if (error)
    return (
      <View style={styles.state}>
        <Text accessibilityRole="alert" style={[styles.body, { color: c.danger }]}>
          {error}
        </Text>
        <Button onPress={retry} secondary>
          Thử lại
        </Button>
      </View>
    );
  if (empty)
    return (
      <View style={styles.state}>
        <BodyText muted>Chưa có dữ liệu trong phạm vi được giao.</BodyText>
      </View>
    );
  return null;
}
export function Status({ value }: { value: string }) {
  const c = useColors();
  return (
    <Text style={[styles.status, { color: value === 'CANCELLED' ? c.muted : c.primary }]}>
      {statusLabel(value)}
    </Text>
  );
}
export function ListRow({
  title,
  detail,
  onPress,
  children,
}: {
  title: string;
  detail?: string;
  onPress?: () => void;
  children?: React.ReactNode;
}) {
  const c = useColors();
  const content = (
    <>
      <Text style={[styles.rowTitle, { color: c.text }]}>{title}</Text>
      {Boolean(detail) && <BodyText muted>{detail}</BodyText>}
      {children}
    </>
  );
  return onPress ? (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      onPress={onPress}
      style={({ pressed }) => [
        styles.row,
        { borderColor: c.border, backgroundColor: pressed ? c.accent : c.surface },
      ]}
    >
      {content}
    </Pressable>
  ) : (
    <View style={[styles.row, { borderColor: c.border, backgroundColor: c.surface }]}>
      {content}
    </View>
  );
}
export function Section({
  children,
  title,
  trailing,
  tone = 'plain',
}: {
  children: React.ReactNode;
  title?: string;
  trailing?: React.ReactNode;
  tone?: 'plain' | 'blue';
}) {
  const c = useColors();
  return (
    <View style={{ gap: 10, marginBottom: 20 }}>
      {Boolean(title) && (
        <View
          style={[
            kit.between,
            {
              paddingHorizontal: 12,
              paddingVertical: 10,
              borderRadius: 9,
              backgroundColor: c.accent,
            },
          ]}
        >
          <Text style={[styles.rowTitle, { color: c.text }]}>{title}</Text>
          {trailing}
        </View>
      )}
      <View style={[kit.elevation, { backgroundColor: c.surface }]}>
        <View style={[kit.section, { backgroundColor: tone === 'blue' ? c.routeBlue : c.surface }]}>
          {children}
        </View>
      </View>
    </View>
  );
}
export function Icon({
  name,
  size = 22,
  color,
}: {
  name: React.ComponentProps<typeof Ionicons>['name'];
  size?: number;
  color?: string;
}) {
  const c = useColors();
  return <Ionicons name={name} size={size} color={color || c.primary} />;
}
export function Search({
  value,
  onChange,
  placeholder = 'Tìm kiếm',
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}) {
  const c = useColors();
  return (
    <View style={[kit.search, { backgroundColor: c.mutedSurface }]}>
      <Icon name="search-outline" size={19} color={c.muted} />
      <TextInput
        accessibilityLabel={placeholder}
        value={value}
        onChangeText={onChange}
        placeholder={placeholder}
        placeholderTextColor={c.muted}
        style={{ flex: 1, minWidth: 0, fontSize: 16, color: c.text, paddingVertical: 10 }}
        autoCorrect={false}
      />
      {Boolean(value) && (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Xoá tìm kiếm"
          hitSlop={12}
          onPress={() => onChange('')}
        >
          <Icon name="close-circle" size={20} color={c.muted} />
        </Pressable>
      )}
    </View>
  );
}
export function ActionRow({
  title,
  detail,
  icon,
  onPress,
  disabled = false,
}: {
  title: string;
  detail?: string;
  icon: React.ComponentProps<typeof Ionicons>['name'];
  onPress: () => void;
  disabled?: boolean;
}) {
  const c = useColors();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        kit.action,
        {
          borderBottomColor: c.border,
          backgroundColor: pressed ? c.accent : c.surface,
          opacity: disabled ? 0.5 : 1,
        },
      ]}
    >
      <View style={[kit.actionIcon, { backgroundColor: c.accent }]}>
        <Icon name={icon} />
      </View>
      <View style={{ flex: 1, gap: 3 }}>
        <Text style={[styles.rowTitle, { color: c.text }]}>{title}</Text>
        {Boolean(detail) && <Text style={[kit.caption, { color: c.muted }]}>{detail}</Text>}
      </View>
      <Icon name="chevron-forward" size={18} color={c.muted} />
    </Pressable>
  );
}
export function Segment({
  items,
  value,
  onChange,
}: {
  items: { key: string; label: string }[];
  value: string;
  onChange: (key: string) => void;
}) {
  const c = useColors();
  return (
    <View style={[kit.segment, { backgroundColor: c.mutedSurface }]}>
      {items.map((item) => (
        <Pressable
          key={item.key}
          accessibilityRole="tab"
          accessibilityState={{ selected: item.key === value }}
          aria-selected={item.key === value}
          onPress={() => onChange(item.key)}
          style={[
            kit.segmentItem,
            { backgroundColor: item.key === value ? c.surface : 'transparent' },
          ]}
        >
          <Text
            style={{
              fontSize: 14,
              fontWeight: '600',
              color: item.key === value ? c.primary : c.muted,
            }}
          >
            {item.label}
          </Text>
        </Pressable>
      ))}
    </View>
  );
}
export function Field({
  label,
  value,
  onChange,
  placeholder,
  multiline = false,
  keyboardType = 'default',
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  multiline?: boolean;
  keyboardType?: React.ComponentProps<typeof TextInput>['keyboardType'];
}) {
  const c = useColors();
  return (
    <View style={styles.group}>
      <Text style={[styles.label, { color: c.text }]}>{label}</Text>
      <TextInput
        accessibilityLabel={label}
        style={[
          styles.input,
          {
            color: c.text,
            borderColor: c.border,
            backgroundColor: c.surface,
            minHeight: multiline ? 100 : 48,
            textAlignVertical: multiline ? 'top' : 'center',
          },
        ]}
        value={value}
        onChangeText={onChange}
        placeholder={placeholder}
        placeholderTextColor={c.muted}
        multiline={multiline}
        keyboardType={keyboardType}
      />
    </View>
  );
}
export function AmountRow({
  label,
  value,
  strong = false,
}: {
  label: string;
  value: string;
  strong?: boolean;
}) {
  const c = useColors();
  return (
    <View style={[kit.between, { paddingVertical: 8, gap: 16 }]}>
      <Text style={{ fontSize: strong ? 17 : 15, color: c.muted, flex: 1 }}>{label}</Text>
      <Text
        style={{
          fontSize: strong ? 23 : 16,
          fontWeight: strong ? '700' : '500',
          color: strong ? c.primary : c.text,
          fontVariant: ['tabular-nums'],
        }}
      >
        {value}
      </Text>
    </View>
  );
}
const productPhotos: Record<string, ReturnType<typeof require>> = {
  'SKU-001': require('../../assets/products/milk.png'),
  'SKU-002': require('../../assets/products/tea.jpg'),
  'SKU-003': require('../../assets/products/water.png'),
  'SKU-004': require('../../assets/products/orange.jpg'),
  'SKU-005': require('../../assets/products/cookies.png'),
  'SKU-006': require('../../assets/products/almond.jpg'),
};
export function ProductPicture({ code, size = 70 }: { code: string; size?: number }) {
  return (
    <View
      style={{
        width: size,
        height: size,
        backgroundColor: '#ffffff',
        borderRadius: 10,
        alignItems: 'center',
        justifyContent: 'center',
        overflow: 'hidden',
      }}
    >
      {productPhotos[code] ? (
        <Image
          accessibilityLabel="Ảnh sản phẩm minh hoạ"
          source={productPhotos[code]}
          resizeMode="contain"
          style={{ width: size - 8, height: size - 8 }}
        />
      ) : (
        <Icon name="cube-outline" size={30} />
      )}
    </View>
  );
}
export const kit = StyleSheet.create({
  elevation: { borderRadius: 14, boxShadow: '0 3px 14px rgba(27,39,51,0.075)' },
  section: { borderRadius: 14, overflow: 'hidden' },
  between: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  horizontal: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  caption: { fontSize: 13, lineHeight: 19 },
  search: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    borderRadius: 10,
    paddingHorizontal: 12,
    minHeight: 44,
  },
  action: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    padding: 15,
    borderBottomWidth: StyleSheet.hairlineWidth,
    minHeight: 68,
  },
  actionIcon: {
    width: 38,
    height: 38,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  segment: { flexDirection: 'row', padding: 3, borderRadius: 10 },
  segmentItem: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 44,
    borderRadius: 8,
    paddingHorizontal: 5,
  },
  footer: {
    paddingHorizontal: 20,
    paddingTop: 12,
    gap: 12,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
});
export const styles = StyleSheet.create({
  body: { fontSize: 17, lineHeight: 25 },
  rowTitle: { fontSize: 17, fontWeight: '600', lineHeight: 25 },
  row: {
    paddingHorizontal: 20,
    paddingVertical: 18,
    gap: 6,
    borderBottomWidth: StyleSheet.hairlineWidth,
    minHeight: 72,
  },
  status: { fontSize: 14, fontWeight: '600', lineHeight: 21 },
  button: {
    minHeight: 48,
    borderRadius: 8,
    paddingHorizontal: 18,
    paddingVertical: 12,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonLabel: { fontSize: 16, fontWeight: '600' },
  state: { padding: 24, gap: 16, alignItems: 'center' },
  page: { flex: 1 },
  intro: { padding: 20, gap: 8 },
  sectionTitle: { fontSize: 21, fontWeight: '700', lineHeight: 29 },
  content: { padding: 20, gap: 16 },
  input: { minHeight: 50, borderWidth: 1, borderRadius: 8, padding: 12, fontSize: 17 },
  label: { fontSize: 15, fontWeight: '500', lineHeight: 22 },
  group: { gap: 8 },
});
