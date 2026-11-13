export type Role = 'nsx' | 'npp' | 'sales';
export type Status =
  | 'PENDING_APPROVAL'
  | 'ON_HOLD_CREDIT'
  | 'APPROVED'
  | 'NEED_INFO'
  | 'REJECTED'
  | 'IN_DELIVERY'
  | 'DELIVERED'
  | 'RECEIVED'
  | 'RECEIVED_DISCREPANCY'
  | 'CANCELLED'
  | 'PENDING_SYNC';
export interface Product {
  id: string;
  name: string;
  category: string;
  unit: string;
  price: number;
  stock: number;
  color: string;
  short: string;
}
export interface OrderLine {
  productId: string;
  qty: number;
  price: number;
  received?: number;
}
export interface Order {
  id: string;
  partnerId: string;
  date: string;
  status: Status;
  lines: OrderLine[];
  channel: 'sell-in' | 'sell-out';
  history: { status: string; time: string; note?: string }[];
  idempotencyKey: string;
}
export interface Partner {
  id: string;
  name: string;
  contact: string;
  region: string;
  credit: number;
  debt: number;
  overdue: number;
  revenue: number;
  target: number;
}
export interface Store {
  id: string;
  name: string;
  owner: string;
  phone: string;
  address: string;
  group: string;
  status: string;
  revenue: number;
  rep: string;
  lat: number;
  lng: number;
}
export interface Task {
  id: string;
  storeId: string;
  title: string;
  due: string;
  assignee: string;
  done: boolean;
}
export interface Ticket {
  id: string;
  title: string;
  partnerId: string;
  category: string;
  status: string;
  priority: string;
  owner: string;
  due: string;
  description: string;
}
export interface Policy {
  id: string;
  name: string;
  description: string;
  discount: number;
  status: string;
  end: string;
}
export interface Log {
  id: string;
  action: string;
  object: string;
  time: string;
  actor: string;
}
export interface DB {
  orders: Order[];
  partners: Partner[];
  stores: Store[];
  tasks: Task[];
  tickets: Ticket[];
  policies: Policy[];
  logs: Log[];
  stock: Record<string, number>;
  period: string;
  visits: string[];
  reportSubmitted: boolean;
}
