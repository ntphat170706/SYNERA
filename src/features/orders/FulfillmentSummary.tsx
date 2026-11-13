import { Link } from 'react-router-dom';
import { ArrowRight, PackageCheck, MessageSquareText } from 'lucide-react';
import { useApi } from '../../shared/hooks/useApi';
import { useStore } from '../../shared/store';
import { numberValue, records, textValue, type RecordData } from '../../shared/api';
import { LoadState } from '../../shared/ApiUI';

export default function FulfillmentSummary({ order }: { order: RecordData }) {
  const { products } = useStore();
  const cases = useApi<RecordData[]>(`/sales-replacements?orderId=${order.id}`, []);
  const staff = useApi<RecordData[]>(order.nppId ? `/staff?nppId=${order.nppId}` : null, []);
  const related = cases.data.filter(
    (r) => String(r.orderId) === String(order.id) && r.status !== 'CANCELLED',
  );
  const lines = records(order.lines);
  const goodsDone =
    order.status === 'DELIVERED_SHORT' &&
    lines.length > 0 &&
    lines.every(
      (l) =>
        numberValue(l.qtyShipped) <=
        numberValue(l.qtyReceived) + numberValue(l.qtyReplacementReceived),
    );
  const person = (id: unknown) =>
    textValue(staff.data.find((s) => String(s.id) === String(id))?.name) || 'Sales được phân công';
  const quantity = (value: unknown) =>
    numberValue(value).toLocaleString('vi-VN', { maximumFractionDigits: 3 });
  return (
    <section className="order-service-summary" aria-label="Giao nhận và chăm sóc">
      <div className="order-goods-result">
        {goodsDone && <p className="order-care-done">Đã giao bù đủ</p>}
        <h3>
          <PackageCheck size={19} />
          Giao nhận
        </h3>
        {lines.map((line) => (
          <div className="order-goods-line" key={textValue(line.id)}>
            <strong>
              {products.find((p) => p.id === String(line.skuId))?.name || 'Mặt hàng'}{' '}
              <span>· {textValue(line.uom)}</span>
            </strong>
            <div>
              <span>
                Thực nhận{' '}
                <b>
                  {quantity(line.qtyReceived)}/{quantity(line.qtyShipped)}
                </b>
              </span>
              <span>
                Đã nhận bù <b>{quantity(line.qtyReplacementReceived)}</b>
              </span>
              <span>
                {order.status === 'DELIVERED_SHORT' ? 'Còn thiếu' : 'Chưa nhận'}{' '}
                <b>
                  {quantity(
                    Math.max(
                      0,
                      numberValue(line.qtyShipped) -
                        numberValue(line.qtyReceived) -
                        numberValue(line.qtyReplacementReceived),
                    ),
                  )}
                </b>
              </span>
            </div>
            {numberValue(line.qtyOrdered) > numberValue(line.qtyConfirmed) && (
              <p className="order-unconfirmed">
                Chưa xác nhận{' '}
                {quantity(numberValue(line.qtyOrdered) - numberValue(line.qtyConfirmed))}{' '}
                {textValue(line.uom)} so với yêu cầu ban đầu
              </p>
            )}
          </div>
        ))}
      </div>
      <div className="order-care-result">
        <h3>
          <MessageSquareText size={19} />
          Chăm sóc khách hàng
        </h3>
        <LoadState {...cases} retry={cases.reload} />
        <LoadState {...staff} retry={staff.reload} />
        {!cases.loading && !cases.error && related.length === 0 && (
          <p>{person(order.repId)} · Chưa có phiếu giao bù</p>
        )}
        {related.map((item) => (
          <article key={textValue(item.id)}>
            <strong>{person(item.repId)}</strong>
            <span className={item.notifiedAt ? 'order-care-done' : 'order-care-pending'}>
              {item.notifiedAt ? 'Đã báo khách' : 'Chờ báo khách'}
            </span>
            <span>Hạn {new Date(textValue(item.dueAt)).toLocaleString('vi-VN')}</span>
            {Boolean(item.notifiedAt) && (
              <details>
                <summary>Xem kết quả</summary>
                <p>{textValue(item.customerFeedback)}</p>
              </details>
            )}
            <Link className="text-button" to={`/npp-workflow?replacement=${item.id}`}>
              GB-{textValue(item.id)} · Xem xử lý
              <ArrowRight size={14} />
            </Link>
          </article>
        ))}
      </div>
    </section>
  );
}
