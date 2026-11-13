import type { CartLine, Offering } from './contracts.ts';
import { lineKey } from './cart.ts';
export const normalizeRequest = (text: string) =>
  text
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
export interface SuggestedLine extends CartLine {
  name: string;
  code: string;
  source: string;
}
export function suggestOrder(
  text: string,
  offerings: Offering[],
): { lines: SuggestedLine[]; unmatched: string[] } {
  const chunks = normalizeRequest(text.replace(/\n/g, ';'))
    .replace(/\b(cho toi|lay giup|dat giup|toi muon|len don|tao don|vui long)\b/g, '')
    .split(/\s+(?:va|voi|them)\s+|[,;]+/)
    .map((s) => s.trim())
    .filter(Boolean);
  const numberWords: Record<string, string> = {
    mot: '1',
    hai: '2',
    ba: '3',
    bon: '4',
    nam: '5',
    sau: '6',
    bay: '7',
    tam: '8',
    chin: '9',
    muoi: '10',
  };
  const names = [...new Set(offerings.map((o) => o.skuId))].map((id) =>
    offerings.find((o) => o.skuId === id)!,
  );
  const lines: SuggestedLine[] = [],
    unmatched: string[] = [];
  for (let part of chunks) {
    part = part
      .replace(/^them /, '')
      .replace(/^(mot|hai|ba|bon|nam|sau|bay|tam|chin|muoi)\b/, (word) => numberWords[word]);
    const parsed = part.match(/^(\d+(?:\.\d{1,3})?)\s*(thung|loc|chai|hop|goi)?\s*(.+)$/);
    if (!parsed) {
      unmatched.push(part);
      continue;
    }
    const qty = Number(parsed[1]),
      unit = parsed[2]?.toUpperCase(),
      query = parsed[3].trim();
    const ranked = names
      .map((o) => {
        const name = normalizeRequest(o.name),
          code = normalizeRequest(o.code);
        const words = query.split(/\s+/).filter((w) => w.length > 1);
        return {
          o,
          score:
            query === code
              ? 100
              : words.reduce((sum, word) => sum + (name.split(' ').includes(word) ? 1 : 0), 0),
        };
      })
      .sort((a, b) => b.score - a.score);
    if (
      !Number.isFinite(qty) ||
      qty <= 0 ||
      qty > 999999999999999 ||
      !ranked[0]?.score ||
      (ranked[1] && ranked[0].score === ranked[1].score)
    ) {
      unmatched.push(part);
      continue;
    }
    const product = ranked[0].o,
      offering = offerings.find(
        (o) => o.skuId === product.skuId && o.uom === (unit || product.baseUnit),
      );
    if (
      !offering ||
      qty * offering.factor < offering.minOrder ||
      Math.abs(qty / offering.multiple - Math.round(qty / offering.multiple)) > 1e-8
    ) {
      unmatched.push(part);
      continue;
    }
    const existing = lines.find((l) => lineKey(l) === lineKey(offering));
    if (existing) existing.qty += qty;
    else
      lines.push({
        skuId: product.skuId,
        uom: offering.uom,
        qty,
        name: product.name,
        code: product.code,
        source: part,
      });
  }
  return { lines, unmatched };
}
