import React, { useState } from 'react';
import {
  Image,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { salesClient } from '../auth/session';
import { BodyText, Button, styles } from '../components/ui';
import { useColors } from '../theme';
export function Login() {
  const c = useColors();
  const [email, setEmail] = useState(''),
    [password, setPassword] = useState(''),
    [otp, setOtp] = useState(''),
    [challenge, setChallenge] = useState<number | null>(null),
    [error, setError] = useState(''),
    [busy, setBusy] = useState(false);
  async function submit() {
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      if (challenge !== null) await salesClient.verify(challenge, otp);
      else {
        const next = await salesClient.login(email, password);
        setPassword('');
        if (next) setChallenge(next.challengeId);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Không thể đăng nhập.');
    } finally {
      setBusy(false);
    }
  }
  return (
    <SafeAreaView style={[styles.page, { backgroundColor: c.canvas }]}>
      <KeyboardAvoidingView
        style={styles.page}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView
          keyboardShouldPersistTaps="handled"
          contentContainerStyle={[
            styles.content,
            {
              flexGrow: 1,
              justifyContent: 'center',
              maxWidth: 480,
              width: '100%',
              alignSelf: 'center',
            },
          ]}
        >
          <Image
            source={require('../../assets/synera-logo.png')}
            accessibilityLabel="SYNERA"
            style={{ width: 168, height: 56, resizeMode: 'contain', alignSelf: 'flex-start' }}
          />
          <Text
            accessibilityRole="header"
            style={[styles.sectionTitle, { fontSize: 30, lineHeight: 38, color: c.text }]}
          >
            Sales thị trường
          </Text>
          <BodyText muted>Điểm bán, lịch ghé và đơn hàng được giao cho bạn.</BodyText>
          {challenge === null ? (
            <>
              <View style={styles.group}>
                <Text style={[styles.label, { color: c.text }]}>Email</Text>
                <TextInput
                  accessibilityLabel="Email"
                  autoCapitalize="none"
                  autoComplete="email"
                  keyboardType="email-address"
                  value={email}
                  onChangeText={setEmail}
                  style={[
                    styles.input,
                    { color: c.text, borderColor: c.border, backgroundColor: c.surface },
                  ]}
                />
              </View>
              <View style={styles.group}>
                <Text style={[styles.label, { color: c.text }]}>Mật khẩu</Text>
                <TextInput
                  accessibilityLabel="Mật khẩu"
                  secureTextEntry
                  autoComplete="current-password"
                  value={password}
                  onChangeText={setPassword}
                  onSubmitEditing={() => void submit()}
                  style={[
                    styles.input,
                    { color: c.text, borderColor: c.border, backgroundColor: c.surface },
                  ]}
                />
              </View>
            </>
          ) : (
            <View style={styles.group}>
              <Text style={[styles.label, { color: c.text }]}>Mã xác thực</Text>
              <TextInput
                accessibilityLabel="Mã xác thực"
                autoComplete="one-time-code"
                keyboardType="number-pad"
                value={otp}
                onChangeText={setOtp}
                maxLength={6}
                style={[
                  styles.input,
                  { color: c.text, borderColor: c.border, backgroundColor: c.surface },
                ]}
              />
            </View>
          )}
          {error ? (
            <Text accessibilityRole="alert" style={[styles.body, { color: c.danger }]}>
              {error}
            </Text>
          ) : null}
          <Button
            onPress={() => void submit()}
            disabled={busy || (challenge === null ? !email || !password : otp.length !== 6)}
          >
            {busy ? 'Đang xác thực…' : challenge === null ? 'Đăng nhập' : 'Xác nhận mã'}
          </Button>
          {challenge !== null && (
            <Button
              secondary
              onPress={() => {
                setChallenge(null);
                setOtp('');
                setError('');
              }}
            >
              Đăng nhập lại
            </Button>
          )}
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
