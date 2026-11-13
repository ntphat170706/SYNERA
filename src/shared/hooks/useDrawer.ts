import { useEffect, useRef } from 'react';

export function useDrawer(open: boolean, onClose: () => void) {
  const ref = useRef<HTMLElement>(null);
  useEffect(() => {
    if (!open || !window.matchMedia('(max-width:760px)').matches) return;
    const previous = document.activeElement as HTMLElement | null;
    const old = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    ref.current?.focus({ preventScroll: true });
    const key = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
        return;
      }
      if (e.key !== 'Tab') return;
      const items = Array.from(
        ref.current?.querySelectorAll<HTMLElement>(
          'a[href],button:not([disabled]),select,[tabindex="0"]',
        ) || [],
      ).filter((x) => x.getClientRects().length);
      if (!items.length) return;
      if (
        e.shiftKey &&
        (document.activeElement === items[0] || document.activeElement === ref.current)
      ) {
        e.preventDefault();
        items.at(-1)?.focus({ preventScroll: true });
      } else if (!e.shiftKey && document.activeElement === items.at(-1)) {
        e.preventDefault();
        items[0].focus({ preventScroll: true });
      }
    };
    document.addEventListener('keydown', key);
    const media = window.matchMedia('(max-width:760px)');
    const resize = () => {
      if (!media.matches) onClose();
    };
    media.addEventListener('change', resize);
    return () => {
      document.body.style.overflow = old;
      document.removeEventListener('keydown', key);
      media.removeEventListener('change', resize);
      previous?.focus({ preventScroll: true });
    };
  }, [open, onClose]);
  return ref;
}
