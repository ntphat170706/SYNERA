export function vietnamDay(now: number) {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date(now));
}
export function periodRange(period: 'today' | 'month', now: number) {
  const to = vietnamDay(now);
  return { from: period === 'month' ? to.slice(0, 8) + '01' : to, to };
}
export function greeting(now: number) {
  const hour = Number(
    new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Asia/Ho_Chi_Minh',
      hour: '2-digit',
      hourCycle: 'h23',
    }).format(new Date(now)),
  );
  return hour >= 5 && hour < 12
    ? 'Chào buổi sáng'
    : hour >= 12 && hour < 18
      ? 'Chào buổi chiều'
      : 'Chào buổi tối';
}
export function elapsedVisit(checkedInAt: string | undefined, now: number) {
  const start = checkedInAt ? Date.parse(checkedInAt) : NaN;
  if (!Number.isFinite(start)) return null;
  const seconds = Math.max(0, Math.floor((now - start) / 1000));
  return [Math.floor(seconds / 3600), Math.floor(seconds / 60) % 60, seconds % 60]
    .map((n) => String(n).padStart(2, '0'))
    .join(':');
}
export function compactAmount(value: number) {
  const format = (number: number) =>
    new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(number);
  return Math.abs(value) >= 1e9
    ? `${format(value / 1e9)} tỷ`
    : Math.abs(value) >= 1e6
      ? `${format(value / 1e6)} tr`
      : `${format(value)} đ`;
}
