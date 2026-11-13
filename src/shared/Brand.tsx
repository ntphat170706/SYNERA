/** Supplied full artwork retains the original lettering; symbol framing is used only in rails. */
export function Brand({ className = '' }: { className?: string }) {
  return (
    <span className={`brand-lockup ${className}`}>
      <img
        className="brand-artwork"
        src="/assets/brand/synera-logo.png"
        alt="SYNERA"
        width="2172"
        height="724"
      />
      <img
        className="brand-light-wordmark"
        src="/assets/brand/synera-logo.png"
        alt=""
        aria-hidden="true"
        width="2172"
        height="724"
      />
      <img
        className="brand-symbol"
        src="/icon.svg"
        alt=""
        aria-hidden="true"
        width="36"
        height="36"
      />
    </span>
  );
}
