import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck, ArrowRight, Eye, EyeOff } from 'lucide-react';
import { Brand } from '../../shared/Brand';
import { api, browserSession } from '../../shared/api';
import { useStore } from '../../shared/store';
import { useApi } from '../../shared/hooks/useApi';
export default function Login() {
  const { completeLogin, sessionError } = useStore();
  const navigate = useNavigate();
  const config = useApi<{ localMode?: boolean }>('/public/config', {});
  const [step, setStep] = useState('login'),
    [username, setUsername] = useState(''),
    [password, setPassword] = useState(''),
    [otp, setOtp] = useState(''),
    [challenge, setChallenge] = useState(0),
    [localOtp, setLocalOtp] = useState(''),
    [resetToken, setResetToken] = useState(''),
    [replacement, setReplacement] = useState(''),
    [error, setError] = useState(''),
    [info, setInfo] = useState(''),
    [busy, setBusy] = useState(false),
    [show, setShow] = useState(false);
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    setInfo('');
    try {
      if (step === 'forgot') {
        const result = await api<{ localResetToken?: string }>('/auth/forgot-password', {
          body: { username },
        });
        setResetToken(result.localResetToken || '');
        setStep('reset');
        setInfo('Nếu tài khoản hợp lệ, hướng dẫn khôi phục đã được gửi.');
        return;
      }
      if (step === 'reset') {
        await api('/auth/reset-password', {
          body: { token: resetToken, newPassword: replacement },
        });
        setStep('login');
        setInfo('Đã đổi mật khẩu. Hãy đăng nhập bằng mật khẩu mới.');
        setPassword('');
        return;
      }
      const result = await browserSession(
        step === 'otp' ? 'otp' : 'login',
        step === 'otp' ? { challengeId: challenge, code: otp } : { username, password },
      );
      if (result.requiresOtp) {
        setChallenge(result.challengeId || 0);
        setLocalOtp(result.localOtp || '');
        setStep('otp');
        return;
      }
      await completeLogin(result);
      navigate(result.user?.role === 'SALES' ? '/sales' : '/dashboard', { replace: true });
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Đăng nhập thất bại.');
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="auth-page">
      <section className="auth-story">
        <a className="brand" href="#/landing">
          <Brand />
        </a>
        <div className="auth-message">
          <span className="eyebrow">CONNECTED DISTRIBUTION</span>
          <h1>
            Kết nối hệ sinh thái.
            <br />
            Mở lối tăng trưởng.
          </h1>
          <p>Một không gian chung cho nhà sản xuất, nhà phân phối và đội ngũ bán hàng.</p>
          <div className="auth-network">
            <span>NHÀ SẢN XUẤT</span>
            <i />
            <span>NHÀ PHÂN PHỐI</span>
            <i />
            <span>ĐIỂM BÁN</span>
          </div>
        </div>
        <span className="auth-footer">DMS + CRM · Đồng hành trên từng điểm chạm.</span>
      </section>
      <section className="auth-form-area">
        <div className="auth-form">
          <span className="soft-icon">
            <ShieldCheck size={24} />
          </span>
          <h2>
            {step === 'login'
              ? 'Chào mừng trở lại'
              : step === 'otp'
                ? 'Xác nhận OTP'
                : step === 'forgot'
                  ? 'Khôi phục mật khẩu'
                  : 'Đặt mật khẩu mới'}
          </h2>
          <p>Đăng nhập bằng tài khoản được quản trị viên cấp.</p>
          <form className="stack" onSubmit={submit}>
            {step !== 'otp' && step !== 'reset' && (
              <label>
                Email
                <input
                  type="email"
                  autoComplete="username"
                  required
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                />
              </label>
            )}
            {step === 'login' && (
              <label>
                Mật khẩu
                <div className="password-input">
                  <input
                    required
                    type={show ? 'text' : 'password'}
                    autoComplete="current-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                  />
                  <button
                    type="button"
                    aria-label={show ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                    onClick={() => setShow(!show)}
                  >
                    {show ? <EyeOff size={17} /> : <Eye size={17} />}
                  </button>
                </div>
              </label>
            )}
            {step === 'otp' && (
              <>
                <label>
                  Mã OTP
                  <input
                    required
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    pattern="[0-9]{6}"
                    maxLength={6}
                    value={otp}
                    onChange={(e) => setOtp(e.target.value)}
                  />
                </label>
                {localOtp && (
                  <p className="info-box">
                    Mã local: <strong>{localOtp}</strong>. Chỉ hiển thị trong môi trường phát triển.
                  </p>
                )}
              </>
            )}
            {step === 'reset' && (
              <>
                <label>
                  Mã khôi phục
                  <input
                    required
                    value={resetToken}
                    onChange={(e) => setResetToken(e.target.value)}
                    autoComplete="off"
                  />
                </label>
                <label>
                  Mật khẩu mới
                  <input
                    required
                    type="password"
                    minLength={12}
                    maxLength={200}
                    autoComplete="new-password"
                    value={replacement}
                    onChange={(e) => setReplacement(e.target.value)}
                  />
                </label>
              </>
            )}
            {(error || sessionError) && (
              <p className="form-error" role="alert">
                {error || sessionError}
              </p>
            )}
            {info && <p role="status">{info}</p>}
            <button className="btn primary" disabled={busy}>
              {busy
                ? 'Đang xử lý...'
                : step === 'forgot'
                  ? 'Gửi yêu cầu khôi phục'
                  : step === 'reset'
                    ? 'Đổi mật khẩu'
                    : 'Tiếp tục đăng nhập'}
              <ArrowRight size={16} />
            </button>
            <button
              className="text-button"
              type="button"
              onClick={() => {
                setStep(step === 'login' ? 'forgot' : 'login');
                setError('');
              }}
            >
              {step === 'login' ? 'Quên mật khẩu?' : 'Quay lại đăng nhập'}
            </button>
          </form>
          {config.data.localMode && (
            <div className="auth-demo-note">
              <span>
                Local: admin@synera.local / npp@synera.local / sales@synera.local. Mật khẩu được cấu
                hình khi khởi tạo; mặc định SyneraLocal2026!.
              </span>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
