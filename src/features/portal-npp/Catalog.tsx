import { useEffect, useState, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ShoppingCart, Plus, Minus, Package, X, ArrowRight } from 'lucide-react';
import { useStore, offlineDB } from '../../shared/store';
import { api, command, records, numberValue, textValue, type RecordData } from '../../shared/api';
import { useApi } from '../../shared/hooks/useApi';
import { LoadState } from '../../shared/ApiUI';
import { PageHead, Panel, SearchBox, Empty } from '../../shared/ui';
import { money, today, now } from '../../shared/utils';
import { productImage, unitName } from '../../shared/productMedia';
export default function Catalog() {
  const categoryName = (value: string) =>
    (({ DRINK: 'Đồ uống', FOOD: 'Thực phẩm' }) as Record<string, string>)[value] || value;
  const { user, db, role, online, notify, refresh, has } = useStore();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [category, setCategory] = useState('');
  const [chosenUnits, setChosenUnits] = useState<Record<string, string>>({});
  const [quickText, setQuickText] = useState(''),
    [quickResult, setQuickResult] = useState<{ lines: RecordData[]; unresolved: string[] } | null>(
      null,
    ),
    [quickBusy, setQuickBusy] = useState(false),
    [quickError, setQuickError] = useState('');
  const quickSequence = useRef(0);
  const [nppId, setNppId] = useState(user?.nppId || Number(db.partners[0]?.id) || 0),
    [storeId, setStoreId] = useState(params.get('store') || ''),
    [query, setQuery] = useState(params.get('q') || ''),
    [cart, setCart] = useState<Record<string, number>>({}),
    [quote, setQuote] = useState<RecordData | null>(null),
    [error, setError] = useState(''),
    [busy, setBusy] = useState(false);
  const salesOrder = role === 'sales' || Boolean(storeId) || params.get('channel') === 'sales';
  useEffect(() => {
    quickSequence.current++;
    setQuickResult(null);
    setQuickBusy(false);
    setQuickError('');
  }, [nppId, storeId, quickText]);
  async function assist() {
    const sequence = ++quickSequence.current;
    setQuickBusy(true);
    setQuickError('');
    try {
      const r = await api<{ lines: RecordData[]; unresolved: string[] }>(
        '/catalog/order-assistance',
        {
          body: { nppId, storeId: salesOrder && storeId ? Number(storeId) : null, text: quickText },
        },
      );
      if (sequence === quickSequence.current) setQuickResult(r);
    } catch (e) {
      if (sequence === quickSequence.current)
        setQuickError(e instanceof Error ? e.message : 'Không thể phân tích yêu cầu.');
    } finally {
      if (sequence === quickSequence.current) setQuickBusy(false);
    }
  }
  const offerings = useApi<RecordData[]>(
    nppId && (!salesOrder || Boolean(storeId))
      ? `/catalog/offerings?nppId=${nppId}${salesOrder && storeId ? '&storeId=' + storeId : ''}`
      : null,
    [],
  );
  useEffect(() => {
    const id = params.get('draft');
    if (!id || !user) return;
    void offlineDB.drafts.get(id).then((draft) => {
      if (
        draft?.ownerId !== user.id ||
        draft.workspaceKey !== user.workspaceKey ||
        !draft.serverPayload
      )
        return;
      setStoreId(textValue(draft.serverPayload.storeId));
      setCart(
        Object.fromEntries(
          records(draft.serverPayload.lines).map((line) => [
            `${line.skuId}:${line.uom}`,
            numberValue(line.qty),
          ]),
        ),
      );
    });
  }, [params.get('draft'), user?.id]);
  useEffect(() => {
    if (!nppId && db.partners[0]) setNppId(Number(db.partners[0].id));
  }, [db.partners, nppId]);
  const lines = offerings.data
    .filter((p) => cart[`${p.skuId}:${p.uom}`] > 0)
    .map((p) => ({
      skuId: numberValue(p.skuId),
      uom: textValue(p.uom),
      qty: cart[`${p.skuId}:${p.uom}`],
    }));
  const signature = JSON.stringify(lines);
  useEffect(() => {
    setQuote(null);
    setError('');
    if (!lines.length || !online) return;
    let active = true;
    const timeout = setTimeout(() => {
      api<RecordData>('/pricing/quote', {
        body: { nppId, lines, storeId: salesOrder && storeId ? Number(storeId) : null },
      })
        .then((value) => {
          if (active) setQuote(value);
        })
        .catch((e) => {
          if (active) setError(e.message);
        });
    }, 250);
    return () => {
      active = false;
      clearTimeout(timeout);
    };
  }, [signature, nppId, storeId, salesOrder, online]);
  async function submit() {
    if (busy || !lines.length) return;
    setBusy(true);
    setError('');
    try {
      if (salesOrder && !storeId) throw new Error('Vui lòng chọn cửa hàng.');
      const key = crypto.randomUUID();
      if (!online) {
        if (!salesOrder) throw new Error('Đặt hàng nhà phân phối cần có kết nối mạng.');
        const payload = {
          clientId: key,
          idempotencyKey: key,
          storeId: Number(storeId),
          createdAt: new Date().toISOString(),
          lines: lines.map((l) => ({
            ...l,
            clientPrice: numberValue(
              offerings.data.find((p) => p.skuId === l.skuId && p.uom === l.uom)?.price,
            ),
          })),
        };
        await offlineDB.drafts.put({
          id: key,
          ownerId: user!.id,
          workspaceKey: user!.workspaceKey,
          partnerId: storeId,
          date: today(),
          status: 'PENDING_SYNC',
          channel: 'sell-out',
          idempotencyKey: key,
          lines: lines.map((l) => ({
            productId: String(l.skuId),
            qty: l.qty,
            price: numberValue(payload.lines.find((p) => p.skuId === l.skuId)?.clientPrice),
          })),
          history: [{ status: 'Chờ gửi', time: now() }],
          serverPayload: payload,
        });
        notify('Đã giữ nháp trên thiết bị. Chỉ đồng bộ trong tài khoản hiện tại.');
        navigate('/sales');
        return;
      }
      const created = await command(
        salesOrder ? '/sales-orders' : '/sell-in-orders',
        salesOrder ? { storeId: Number(storeId), lines } : { nppId, lines },
        key,
      );
      if (!salesOrder) {
        try {
          await command(`/sell-in-orders/${created.id}/submit`);
        } catch {
          notify(
            `Đơn #${created.id} đã được lưu. Kiểm tra trạng thái và gửi lại ngay trong chi tiết đơn.`,
          );
          navigate(`/orders?channel=sellin&order=${created.id}`);
          return;
        }
      }
      const edited = params.get('draft');
      if (edited) {
        const original = await offlineDB.drafts.get(edited);
        if (original?.ownerId === user?.id && original?.workspaceKey === user?.workspaceKey)
          await offlineDB.drafts.delete(edited);
      }
      await refresh();
      setCart({});
      notify('Đã gửi đơn tới máy chủ.');
      navigate(`/orders?channel=${salesOrder ? 'sales' : 'sellin'}&order=${created.id}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Không thể gửi đơn.');
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHead title={salesOrder ? 'Lập đơn bán cửa hàng' : 'Đặt hàng từ nhà sản xuất'} />
      {role === 'npp' && has('SELLIN_CREATE') && has('SALES_CREATE') && (
        <div className="tabs">
          <button
            className={!salesOrder ? 'active' : ''}
            onClick={() => {
              setStoreId('');
              setCart({});
              navigate('/catalog');
            }}
          >
            Đặt nhập từ NSX
          </button>
          <button
            className={salesOrder ? 'active' : ''}
            onClick={() => {
              setStoreId('');
              setCart({});
              navigate('/catalog?channel=sales');
            }}
          >
            Lập đơn cho điểm bán
          </button>
        </div>
      )}
      <div className="toolbar">
        {!user?.nppId && (
          <label>
            Nhà phân phối
            <select
              value={nppId}
              disabled={Boolean(user?.nppId)}
              onChange={(e) => {
                setNppId(Number(e.target.value));
                setCart({});
              }}
            >
              {db.partners.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </label>
        )}
        {has('SALES_CREATE') && salesOrder && (
          <label>
            Cửa hàng
            <select
              value={storeId}
              onChange={(e) => {
                setStoreId(e.target.value);
                setCart({});
                setQuote(null);
              }}
            >
              <option value="">Chọn điểm bán</option>
              {db.stores.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </label>
        )}
        <SearchBox value={query} onChange={setQuery} />
      </div>
      <LoadState {...offerings} retry={offerings.reload} />
      <details className="npp-order-assist" open={params.get('assistant') === '1' || undefined}>
        <summary>Trợ lý lên đơn</summary>
        <div>
          <label>
            Nội dung yêu cầu
            <textarea
              value={quickText}
              onChange={(e) => setQuickText(e.target.value)}
              placeholder={
                offerings.data[0]
                  ? `2 ${unitName(textValue(offerings.data[0].uom)).toLowerCase()} ${offerings.data[0].code}`
                  : 'Nội dung đặt hàng'
              }
            />
          </label>
          <button
            className="btn"
            disabled={!quickText.trim() || quickBusy || !online}
            onClick={() => void assist()}
          >
            {quickBusy ? 'Đang phân tích...' : 'Phân tích yêu cầu'}
          </button>
          {quickError && (
            <p className="form-error" role="alert">
              {quickError}
            </p>
          )}
          {quickResult && (
            <div aria-live="polite">
              {quickResult.lines.map((l, i) => (
                <p key={i}>
                  <strong>{textValue(l.name)}</strong> · {textValue(l.qty)} {textValue(l.uom)}
                </p>
              ))}
              {quickResult.unresolved.map((message, i) => (
                <p className="form-error" key={i}>
                  {message}
                </p>
              ))}
              <button
                className="btn primary"
                disabled={!quickResult.lines.length || quickResult.unresolved.length > 0}
                onClick={() => {
                  setCart((c) => {
                    const next = { ...c };
                    for (const l of quickResult.lines) {
                      const id = `${l.skuId}:${l.uom}`;
                      next[id] = (next[id] || 0) + numberValue(l.qty);
                    }
                    return next;
                  });
                  setQuickResult(null);
                }}
              >
                Xác nhận và thêm vào giỏ
              </button>
            </div>
          )}
        </div>
      </details>
      <div className="tabs npp-category-tabs" aria-label="Nhóm sản phẩm">
        {['', ...new Set(offerings.data.map((p) => textValue(p.category)).filter(Boolean))].map(
          (name) => (
            <button
              key={name}
              onClick={() => setCategory(name)}
              className={category === name ? 'active' : ''}
            >
              {name ? categoryName(name) : 'Tất cả sản phẩm'}
            </button>
          ),
        )}
      </div>
      <div className="catalog-layout">
        <div className="product-grid">
          {offerings.data
            .filter((p) => !category || p.category === category)
            .filter((p) =>
              `${p.name} ${p.code}`.toLocaleLowerCase('vi').includes(query.toLocaleLowerCase('vi')),
            )
            .filter((p, index, all) => all.findIndex((other) => other.skuId === p.skuId) === index)
            .map((first) => {
              const options = offerings.data.filter((p) => p.skuId === first.skuId);
              const p =
                options.find((p) => p.uom === chosenUnits[String(first.skuId)]) ||
                options.find((p) => p.uom === 'THUNG') ||
                first;
              const id = `${p.skuId}:${p.uom}`;
              return (
                <article className="product-card" key={String(p.skuId)}>
                  <div className="product-art">
                    {productImage(textValue(p.name)) ? (
                      <>
                        <img
                          src={productImage(textValue(p.name))}
                          alt={textValue(p.name)}
                          loading="lazy"
                        />
                        <span>Ảnh minh họa</span>
                      </>
                    ) : (
                      <Package size={42} />
                    )}
                  </div>
                  <small>
                    {textValue(p.code)} · {categoryName(textValue(p.category))}
                  </small>
                  <h3>{textValue(p.name)}</h3>
                  <select
                    aria-label={`Quy cách ${p.name}`}
                    value={textValue(p.uom)}
                    onChange={(e) =>
                      setChosenUnits((units) => ({ ...units, [String(p.skuId)]: e.target.value }))
                    }
                  >
                    {options.map((option) => (
                      <option key={textValue(option.uom)} value={textValue(option.uom)}>
                        {unitName(textValue(option.uom))}
                      </option>
                    ))}
                  </select>
                  <p>
                    1 {unitName(textValue(p.uom)).toLowerCase()} = {textValue(p.factor)}{' '}
                    {unitName(textValue(p.baseUnit)).toLowerCase()}
                  </p>
                  <strong>{money(numberValue(p.price))}</strong>
                  <div className="quantity-input">
                    <button
                      aria-label={`Giảm ${p.name}`}
                      onClick={() =>
                        setCart((c) => ({
                          ...c,
                          [id]: Math.max(0, (c[id] || 0) - numberValue(p.multiple)),
                        }))
                      }
                    >
                      <Minus size={15} />
                    </button>
                    <input
                      aria-label={`Số lượng ${p.name}`}
                      type="number"
                      min="0"
                      step={textValue(p.multiple)}
                      value={cart[id] || 0}
                      onChange={(e) =>
                        setCart((c) => ({ ...c, [id]: Math.max(0, Number(e.target.value)) }))
                      }
                    />
                    <button
                      aria-label={`Thêm ${p.name}`}
                      onClick={() =>
                        setCart((c) => ({ ...c, [id]: (c[id] || 0) + numberValue(p.multiple) }))
                      }
                    >
                      <Plus size={15} />
                    </button>
                  </div>
                </article>
              );
            })}
          {!offerings.loading &&
            !offerings.data.filter(
              (p) =>
                (!category || p.category === category) &&
                `${p.name} ${p.code}`
                  .toLocaleLowerCase('vi')
                  .includes(query.toLocaleLowerCase('vi')),
            ).length && (
              <Empty
                text={
                  salesOrder && !storeId
                    ? 'Chưa chọn điểm bán.'
                    : offerings.data.length
                      ? 'Không có sản phẩm phù hợp bộ lọc.'
                      : 'Chưa có sản phẩm với bảng giá đang hiệu lực.'
                }
              />
            )}
        </div>
        <Panel
          title={`Giỏ hàng · ${lines.length} dòng`}
          className="cart-panel"
          action={<ShoppingCart size={20} />}
        >
          <div className="stack">
            {lines.map((l) => (
              <div className="cart-line" key={`${l.skuId}:${l.uom}`}>
                <span>
                  {textValue(
                    offerings.data.find((p) => p.skuId === l.skuId && p.uom === l.uom)?.name,
                  )}
                  <small>
                    {l.qty} {l.uom}
                  </small>
                </span>
                <button
                  className="icon-button"
                  aria-label={`Bỏ ${l.skuId} ${l.uom} khỏi giỏ`}
                  onClick={() => setCart((c) => ({ ...c, [`${l.skuId}:${l.uom}`]: 0 }))}
                >
                  <X size={15} />
                </button>
              </div>
            ))}
            {!lines.length && <p className="cart-empty">Giỏ hàng trống</p>}
            {quote && (
              <>
                <p>
                  Tạm tính: <strong>{money(numberValue(quote.subtotal))}</strong>
                </p>
                <p>Ưu đãi: {money(numberValue(quote.discount))}</p>
                <div className="cart-total">
                  Tổng thanh toán <strong>{money(numberValue(quote.total))}</strong>
                </div>
              </>
            )}
            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}
            <button
              className="btn primary full-width"
              onClick={() => void submit()}
              disabled={busy || !lines.length || (online && !quote)}
            >
              {busy ? (
                'Đang gửi...'
              ) : online ? (
                <>
                  {salesOrder ? 'Gửi đơn cửa hàng' : 'Đặt hàng NSX'} <ArrowRight size={15} />
                </>
              ) : (
                'Lưu nháp ngoại tuyến'
              )}
            </button>
          </div>
        </Panel>
      </div>
    </>
  );
}
