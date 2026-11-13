import { useRef, useState, type FormEvent } from 'react';
import { ArrowLeft, ArrowRight, Check, Store, UserRound, MapPin } from 'lucide-react';
import { Modal } from '../../shared/ui';

export default function StoreCreate({
  staff,
  onClose,
  onSubmit,
}: {
  staff: { value: string; label: string }[];
  onClose: () => void;
  onSubmit: (values: Record<string, string>) => Promise<void>;
}) {
  const [step, setStep] = useState(0),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('');
  const formRef = useRef<HTMLFormElement>(null);
  function next() {
    const fields = Array.from(
      formRef.current?.querySelectorAll<HTMLInputElement>('[data-step="0"] input') || [],
    );
    if (fields.every((field) => field.reportValidity())) setStep(1);
  }
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    if (step === 0) {
      next();
      return;
    }
    setBusy(true);
    setError('');
    try {
      await onSubmit(
        Object.fromEntries(new FormData(event.currentTarget).entries()) as Record<string, string>,
      );
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Không thể tạo điểm bán.');
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal title="Thêm cửa hàng" onClose={onClose} className="store-create-modal">
      <form ref={formRef} className="store-create-form" onSubmit={save}>
        <nav className="store-create-steps" aria-label="Các bước tạo điểm bán">
          {['Thông tin điểm bán', 'Phụ trách & vị trí'].map((title, i) => (
            <button
              type="button"
              key={title}
              aria-current={step === i ? 'step' : undefined}
              disabled={i > step}
              onClick={() => setStep(i)}
            >
              <span>{i < step ? <Check size={16} /> : i + 1}</span>
              {title}
            </button>
          ))}
        </nav>
        <section
          className="store-form-stage"
          data-step="0"
          hidden={step !== 0}
          aria-label="Thông tin điểm bán"
        >
          <div className="store-form-heading">
            <Store size={21} />
            <h3>Thông tin điểm bán</h3>
          </div>
          <label className="form-field-wide">
            Tên cửa hàng
            <input name="name" autoComplete="organization" placeholder="Tên điểm bán" required />
          </label>
          <label>
            Người liên hệ
            <input name="owner" autoComplete="name" placeholder="Họ và tên" />
          </label>
          <label>
            Điện thoại
            <input
              name="phone"
              type="tel"
              autoComplete="tel"
              placeholder="Số điện thoại"
              required
            />
          </label>
          <label className="form-field-wide">
            Địa chỉ
            <input
              name="address"
              autoComplete="street-address"
              placeholder="Số nhà, đường, phường/xã, tỉnh/thành"
              required
            />
          </label>
        </section>
        <section
          className="store-form-stage"
          data-step="1"
          hidden={step !== 1}
          aria-label="Phụ trách và vị trí"
        >
          <div className="store-form-heading">
            <UserRound size={21} />
            <h3>Sales phụ trách</h3>
          </div>
          <label className="form-field-wide">
            Nhân viên phụ trách
            <select
              name="repId"
              defaultValue={staff.length === 1 ? staff[0].value : ''}
              required={step === 1}
            >
              <option value="">Chọn Sales phụ trách</option>
              {staff.map((person) => (
                <option key={person.value} value={person.value}>
                  {person.label}
                </option>
              ))}
            </select>
          </label>
          {!staff.length && (
            <p className="form-error" role="alert">
              Chưa có Sales được phân công trong phạm vi NPP.
            </p>
          )}
          <details className="store-location form-field-wide">
            <summary>
              <MapPin size={18} />
              Vị trí GPS
            </summary>
            <div>
              <label>
                Vĩ độ
                <input name="latitude" type="number" step="any" min={-90} max={90} />
              </label>
              <label>
                Kinh độ
                <input name="longitude" type="number" step="any" min={-180} max={180} />
              </label>
            </div>
          </details>
        </section>
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        <footer className="store-create-footer">
          <button
            className="btn"
            type="button"
            disabled={busy}
            onClick={step ? () => setStep(0) : onClose}
          >
            {step ? (
              <>
                <ArrowLeft size={16} />
                Quay lại
              </>
            ) : (
              'Hủy'
            )}
          </button>
          {step === 0 ? (
            <button
              key="next"
              className="btn primary"
              type="button"
              onClick={(event) => {
                event.preventDefault();
                next();
              }}
            >
              Tiếp tục
              <ArrowRight size={16} />
            </button>
          ) : (
            <button
              key="create"
              className="btn primary"
              type="submit"
              disabled={busy || !staff.length}
            >
              {busy ? 'Đang tạo...' : 'Tạo điểm bán'}
              <Check size={16} />
            </button>
          )}
        </footer>
      </form>
    </Modal>
  );
}
