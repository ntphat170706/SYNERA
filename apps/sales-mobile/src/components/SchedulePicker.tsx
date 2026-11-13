import React from 'react';
import { useColors } from '../theme';
export function SchedulePicker({
  value,
  onChange,
}: {
  value: Date;
  onChange: (date: Date) => void;
}) {
  const c = useColors();
  const date = value.toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' }),
    time = value.toLocaleTimeString('en-GB', {
      timeZone: 'Asia/Ho_Chi_Minh',
      hour: '2-digit',
      minute: '2-digit',
    });
  return (
    <input
      aria-label="Ngày và giờ hẹn"
      type="datetime-local"
      value={`${date}T${time}`}
      onChange={(event) => {
        const parsed = new Date(`${event.target.value}:00+07:00`);
        if (!Number.isNaN(parsed.getTime())) onChange(parsed);
      }}
      style={{
        boxSizing: 'border-box',
        width: '100%',
        minHeight: 48,
        padding: 12,
        border: `1px solid ${c.border}`,
        borderRadius: 8,
        fontFamily: 'inherit',
        fontSize: 17,
        color: c.text,
        backgroundColor: c.surface,
      }}
    />
  );
}
