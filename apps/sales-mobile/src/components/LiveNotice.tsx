import React, { useEffect, useState } from 'react';
import { AppState, Pressable, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Ionicons from '@expo/vector-icons/Ionicons';
import { salesClient } from '../auth/session';
import { noticeSummary, type SalesNotice } from '../domain/notifications';
import { useColors } from '../theme';
export function LiveNotice({ userId, onOpen }: { userId: number; onOpen: () => void }) {
  const [notice, setNotice] = useState<SalesNotice | null>(null),
    c = useColors(),
    insets = useSafeAreaInsets();
  useEffect(() => {
    let active = true,
      ready = false,
      pending = false,
      seen = 0,
      timer: ReturnType<typeof setTimeout>;
    const read = async () => {
      if (pending || AppState.currentState !== 'active') return;
      pending = true;
      try {
        const rows = await salesClient.get<SalesNotice[]>('/notifications');
        if (!active) return;
        const fresh = rows.filter((n) => n.id > seen && !n.read).sort((a, b) => b.id - a.id);
        if (ready && fresh.length) setNotice(fresh[0]);
        seen = Math.max(seen, ...rows.map((n) => n.id));
        ready = true;
      } catch {
        /* The visible screens keep their own network error and retry states. */
      } finally {
        pending = false;
      }
    };
    const poll = () => {
      void read().finally(() => {
        if (active) timer = setTimeout(poll, 2000);
      });
    };
    poll();
    const listener = AppState.addEventListener('change', (s) => {
      if (s === 'active') void read();
    });
    return () => {
      active = false;
      clearTimeout(timer);
      listener.remove();
    };
  }, [userId]);
  useEffect(() => {
    if (!notice) return;
    const t = setTimeout(() => setNotice(null), 8000);
    return () => clearTimeout(t);
  }, [notice]);
  if (!notice) return null;
  const info = noticeSummary(notice);
  return (
    <View
      accessibilityLiveRegion="polite"
      style={{
        position: 'absolute',
        top: insets.top + 10,
        left: 16,
        right: 16,
        zIndex: 1000,
        backgroundColor: c.surface,
        borderWidth: 1,
        borderColor: c.border,
        borderRadius: 14,
        padding: 16,
        flexDirection: 'row',
        gap: 12,
        boxShadow: '0 6px 24px rgba(0,0,0,0.14)',
      }}
    >
      <Ionicons name="notifications" size={23} color={c.primary} />
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Mở thông báo mới"
        onPress={() => {
          setNotice(null);
          onOpen();
        }}
        style={{ flex: 1, gap: 5 }}
      >
        <Text style={{ color: c.text, fontWeight: '700' }}>{info.title}</Text>
        <Text style={{ color: c.muted }}>{info.description}</Text>
      </Pressable>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Đóng thông báo"
        onPress={() => setNotice(null)}
        hitSlop={10}
      >
        <Ionicons name="close" size={19} color={c.muted} />
      </Pressable>
    </View>
  );
}
