import { useEffect, useRef, useState, isValidElement, type ReactNode } from 'react';
import {
  ArrowDownToLine,
  ChevronLeft,
  ChevronRight,
  Search,
  X,
  Inbox,
  ArrowUpDown,
} from 'lucide-react';
import { statusLabels } from './utils';
import type { Status } from './types';
export function Badge({ status }: { status: string }) {
  const label = statusLabels[status as Status] || status;
  const color =
    /REJECTED|ON_HOLD|DELIVERED_SHORT|DISCREPANCY|FAILED|DEAD|nguy cơ|Quá hạn|Cao|chênh lệch/.test(
      status,
    )
      ? 'red'
      : /PENDING|PROPOSED|NEED_INFO|NEW|OPEN|WAITING|Mới|Nháp|Chờ|Đang xử lý/.test(status)
        ? 'amber'
        : /APPROVED|RECEIVED|COMPLETED|RESOLVED|DONE|hoạt động|áp dụng|giải quyết|Đã chốt|Hoàn thành|Đã phản hồi/.test(
              status,
            )
          ? 'green'
          : 'blue';
  return (
    <span className={`badge ${color}`}>
      <i />
      {label}
    </span>
  );
}
export function Empty({ text = 'Chưa có dữ liệu phù hợp.' }: { text?: string }) {
  return (
    <div className="empty">
      <Inbox size={32} />
      <strong>{text}</strong>
    </div>
  );
}
export function PageHead({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children?: ReactNode;
}) {
  return (
    <div className="page-head">
      <div>
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      <div className="head-actions">{children}</div>
    </div>
  );
}
export function Panel({
  title,
  subtitle,
  children,
  action,
  className = '',
}: {
  title?: string;
  subtitle?: string;
  children: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <section className={`panel ${className}`}>
      {title && (
        <div className="panel-head">
          <div>
            <h2>{title}</h2>
            {subtitle && <p>{subtitle}</p>}
          </div>
          {action}
        </div>
      )}
      {children}
    </section>
  );
}
export function Modal({
  title,
  children,
  onClose,
  wide = false,
  variant = 'dialog',
  className = '',
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  wide?: boolean;
  variant?: 'dialog' | 'sheet';
  className?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement;
    ref.current?.focus();
    const key = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
      if (e.key === 'Tab') {
        const nodes = Array.from(
          ref.current?.querySelectorAll<HTMLElement>(
            'button,input,select,textarea,a[href],[tabindex="0"]',
          ) || [],
        ).filter((node) => !node.hasAttribute('disabled') && node.getClientRects().length > 0);
        if (!nodes?.length) return;
        const first = nodes[0],
          last = nodes[nodes.length - 1];
        if (
          e.shiftKey &&
          (document.activeElement === first || document.activeElement === ref.current)
        ) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener('keydown', key);
    const old = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', key);
      document.body.style.overflow = old;
      previous?.focus();
    };
  }, []);
  return (
    <div
      className={`modal-backdrop ${variant === 'sheet' ? 'sheet-backdrop' : ''}`}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        ref={ref}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={`modal ${wide ? 'wide' : ''} ${variant === 'sheet' ? 'action-sheet' : ''} ${className}`}
      >
        <div className="modal-head">
          <h2>{title}</h2>
          <button className="icon-button" aria-label="Đóng" onClick={onClose}>
            <X size={20} />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
export function Table<T>({
  rows,
  columns,
  searchText = '',
  onRow,
  pageSize = 6,
  pagination = true,
}: {
  rows: T[];
  columns: {
    key: string;
    label: string;
    render: (r: T) => ReactNode;
    sortValue?: (r: T) => string | number;
  }[];
  searchText?: string;
  onRow?: (r: T) => void;
  pageSize?: number;
  pagination?: boolean;
}) {
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<{ key: string; direction: 1 | -1 } | null>(null);
  useEffect(() => setPage(1), [searchText, rows.length, sort]);
  const text = (node: ReactNode): string => {
    if (typeof node === 'string' || typeof node === 'number') return String(node);
    if (Array.isArray(node)) return node.map(text).join(' ');
    if (isValidElement<{ children?: ReactNode; status?: string }>(node))
      return text(node.props.children) || node.props.status || '';
    return '';
  };
  const column = columns.find((c) => c.key === sort?.key);
  const sorted =
    sort && column
      ? [...rows].sort((a, b) => {
          const av = column.sortValue?.(a) ?? text(column.render(a));
          const bv = column.sortValue?.(b) ?? text(column.render(b));
          if (typeof av === 'number' && typeof bv === 'number') return (av - bv) * sort.direction;
          const numeric = (value: string | number) => {
            const s = String(value).trim();
            return /^[\d.,\s]+(?:₫|thùng|%)?$/.test(s)
              ? Number(s.replace(/[^\d,]/g, '').replace(',', '.'))
              : NaN;
          };
          const an = numeric(av),
            bn = numeric(bv);
          return (
            (Number.isFinite(an) && Number.isFinite(bn)
              ? an - bn
              : String(av).localeCompare(String(bv), 'vi', {
                  numeric: true,
                  sensitivity: 'base',
                })) * sort.direction
          );
        })
      : rows;
  const pages = Math.max(1, Math.ceil(rows.length / pageSize));
  const current = Math.min(page, pages);
  return (
    <>
      <div className="table-wrap">
        <table className="responsive-table">
          <thead>
            <tr>
              {columns.map((c) => (
                <th
                  key={c.key}
                  data-column={c.key}
                  aria-sort={
                    sort?.key === c.key
                      ? sort.direction === 1
                        ? 'ascending'
                        : 'descending'
                      : 'none'
                  }
                >
                  <button
                    className="sort-header"
                    onClick={() =>
                      setSort({
                        key: c.key,
                        direction: sort?.key === c.key && sort.direction === 1 ? -1 : 1,
                      })
                    }
                  >
                    {c.label}
                    <ArrowUpDown size={12} />
                  </button>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {sorted.slice((current - 1) * pageSize, current * pageSize).map((r, i) => (
              <tr
                key={i}
                className={onRow ? 'clickable' : ''}
                data-status={
                  typeof r === 'object' && r !== null && 'status' in r
                    ? String(r.status)
                    : undefined
                }
              >
                {columns.map((c, j) => (
                  <td key={c.key} data-label={c.label} data-column={c.key}>
                    {j === 0 && onRow ? (
                      <button className="table-link" onClick={() => onRow(r)}>
                        {c.render(r)}
                      </button>
                    ) : (
                      c.render(r)
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
        {!rows.length && <Empty />}
      </div>
      {pagination && rows.length > pageSize && (
        <div className="pagination">
          <span>
            Hiển thị {rows.length ? (current - 1) * pageSize + 1 : 0}–
            {Math.min(current * pageSize, rows.length)} / {rows.length} kết quả
          </span>
          <div>
            <button
              className="icon-button"
              disabled={current === 1}
              aria-label="Trang trước"
              onClick={() => setPage((p) => p - 1)}
            >
              <ChevronLeft size={16} />
            </button>
            <button className="page-number">{current}</button>
            <button
              className="icon-button"
              disabled={current === pages}
              aria-label="Trang sau"
              onClick={() => setPage((p) => p + 1)}
            >
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
      )}
    </>
  );
}
export function SearchBox({
  value,
  onChange,
  placeholder = 'Tìm kiếm...',
}: {
  value: string;
  onChange: (s: string) => void;
  placeholder?: string;
}) {
  return (
    <div className="search-box">
      <Search size={17} />
      <input
        aria-label={placeholder}
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      {value && (
        <button aria-label="Xóa tìm kiếm" onClick={() => onChange('')}>
          <X size={14} />
        </button>
      )}
    </div>
  );
}
export function ExportButton({ onClick }: { onClick: () => void }) {
  return (
    <button className="btn" onClick={onClick}>
      <ArrowDownToLine size={16} />
      Xuất dữ liệu
    </button>
  );
}
