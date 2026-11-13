export interface SalesUser {
  id: number;
  name: string;
  role: string;
  nppId: number;
  workspaceKey: string;
  authorities: string[];
}
export interface Session {
  accessToken: string;
  refreshToken: string;
  user: SalesUser;
}
export interface Challenge {
  requiresOtp: true;
  challengeId: number;
}
export interface Store {
  id: number;
  name: string;
  owner?: string;
  phone?: string;
  address?: string;
  nppId: number;
  repId: number;
  latitude?: number;
  longitude?: number;
  groupName?: string;
  status?: string;
}
export interface Visit {
  id: number;
  storeId: number;
  repId: number;
  day: string;
  status: string;
  outsideRadius: boolean;
  distanceMetres: number;
  checkedInAt: string;
}
export interface SalesOrder {
  id: number;
  code: string;
  storeId: number;
  status: string;
  total: number;
  createdAt: string;
}
export interface OrderLine {
  qtyReplacementReceived?: number;
  qtyAvailableToReplace?: number;
  id: number;
  skuId: number;
  uom: string;
  qtyOrdered: number;
  qtyConfirmed: number;
  qtyShipped: number;
  qtyReceived: number;
  price: number;
}
export interface StoreProfile {
  store: Store;
  orders: SalesOrder[];
  interactions: { id: number; kind: string; note: string; at: string }[];
  appointments: { id: number; title: string; dueAt: string; status: string }[];
  tickets?: { id: number; title: string; status: string; createdAt: string }[];
}
export interface Offering {
  skuId: number;
  code: string;
  name: string;
  category: string;
  uom: string;
  factor: number;
  price: number;
  multiple: number;
  minOrder: number;
  baseUnit: string;
}
export interface CartLine {
  skuId: number;
  uom: string;
  qty: number;
}
export interface PriceQuote {
  lines: (CartLine & { price: number; discount: number; total: number; factor: number })[];
  subtotal: number;
  discount: number;
  total: number;
  benefits: { promotionId?: number; name?: string; amount?: number }[];
}
export interface Paged<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
}

// A future native outbox adapter must implement persistence. Not a claim of offline UI support.
export interface OwnedCommand {
  clientId: string;
  idempotencyKey: string;
  ownerId: number;
  workspaceKey: string;
  createdAt: string;
  status: 'LOCAL' | 'SENDING' | 'NEEDS_REVIEW' | 'ACKNOWLEDGED';
  payload: unknown;
}
export interface CommandAck {
  clientId: string;
  status: string;
  orderId?: number;
}
export function acknowledgedIds(commands: OwnedCommand[], replies: CommandAck[], user: SalesUser) {
  const owned = new Set(
    commands
      .filter((c) => c.ownerId === user.id && c.workspaceKey === user.workspaceKey)
      .map((c) => c.clientId),
  );
  return replies
    .filter(
      (r) =>
        owned.has(r.clientId) &&
        ['CREATED', 'DUPLICATE'].includes(r.status) &&
        Number.isSafeInteger(r.orderId) &&
        Number(r.orderId) > 0,
    )
    .map((r) => r.clientId);
}
export interface EvidenceCapture {
  uri: string;
  capturedAt: string;
  latitude?: number;
  longitude?: number;
  accuracyMetres?: number;
}
export interface DevicePorts {
  locate(): Promise<{
    latitude: number;
    longitude: number;
    accuracyMetres: number | null;
    capturedAt: string;
  }>;
  photograph(): Promise<EvidenceCapture | null>;
}
