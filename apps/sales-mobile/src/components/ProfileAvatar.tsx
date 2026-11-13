import React from 'react';
import { Image, Text, View } from 'react-native';
import { useColors } from '../theme';
export function ProfileAvatar({
  name,
  size = 44,
  userId,
}: {
  name: string;
  size?: number;
  userId?: number;
}) {
  const c = useColors();
  return (
    <View
      style={{
        width: size,
        height: size,
        borderRadius: size / 2,
        backgroundColor: c.accent,
        alignItems: 'center',
        justifyContent: 'center',
        overflow: 'hidden',
      }}
    >
      {userId === 3 ? (
        <Image
          accessibilityLabel={`Ảnh đại diện ${name}`}
          source={require('../../assets/quan-dao-avatar.jpg')}
          resizeMode="cover"
          style={{ width: size, height: size }}
        />
      ) : (
        <Text style={{ color: c.primary, fontWeight: '600', fontSize: size / 3 }}>
          {name
            .split(' ')
            .filter(Boolean)
            .slice(-2)
            .map((n) => n[0])
            .join('')
            .toUpperCase()}
        </Text>
      )}
    </View>
  );
}
