import {
  LayoutDashboard,
  ShoppingBag,
  Building2,
  Wallet,
  Package,
  Store,
  Route as RouteIcon,
  BarChart3,
  Tags,
  Headphones,
  Settings,
  CalendarDays,
  type LucideIcon,
} from 'lucide-react';
import type { Role } from '../shared/types';
import type { SessionUser } from '../shared/api';
import {
  IconLayoutDashboard,
  IconShoppingCart,
  IconBuildingWarehouse,
  IconAddressBook,
  IconReceipt2,
  IconChartBar,
} from '../shared/NavIcons';

export const nppNavigation = [
  { path: '/dashboard', label: 'Tổng quan', icon: IconLayoutDashboard, children: [] },
  {
    path: '/orders',
    label: 'Đơn hàng',
    icon: IconShoppingCart,
    children: ['/catalog', '/npp-workflow', '/delivery', '/policies', '/trade'],
  },
  { path: '/inventory', label: 'Kho hàng', icon: IconBuildingWarehouse, children: ['/demand'] },
  {
    path: '/crm',
    label: 'Điểm bán & CRM',
    icon: IconAddressBook,
    children: ['/engagement', '/display', '/references', '/tickets'],
  },
  { path: '/finance', label: 'Công nợ', icon: IconReceipt2, children: [] },
  { path: '/reports', label: 'Báo cáo', icon: IconChartBar, children: [] },
];

export const nppContextLinks: Record<string, [string, string][]> = {
  '/dashboard': [
    ['/npp-workflow', 'Giao bù'],
    ['/tickets', 'Yêu cầu hỗ trợ'],
    ['/settings', 'Thiết lập'],
  ],
  '/orders': [
    ['/catalog', 'Đặt hàng NSX'],
    ['/npp-workflow', 'Giao bù'],
    ['/delivery', 'Chuyến giao'],
    ['/policies', 'Chính sách bán hàng'],
  ],
  '/inventory': [
    ['/demand', 'Kế hoạch nhập'],
    ['/references?view=import', 'Nhập Excel'],
    ['/tickets', 'Yêu cầu hỗ trợ'],
  ],
  '/npp-workflow': [['/tickets', 'Yêu cầu hỗ trợ']],
  '/crm': [
    ['/engagement', 'Báo giá & chiến dịch'],
    ['/display', 'Trưng bày'],
    ['/tickets', 'Yêu cầu hỗ trợ'],
    ['/references?view=import', 'Nhập Excel'],
  ],
  '/policies': [['/trade', 'Hỗ trợ bán hàng']],
  '/trade': [['/policies', 'Chính sách bán hàng']],
};

export const nsxContextLinks: Record<string, [string, string][]> = {
  '/dashboard': [
    ['/tickets', 'Yêu cầu hãng xử lý'],
    ['/demand', 'Kế hoạch cung ứng'],
  ],
  '/network': [
    ['/engagement', 'Báo giá & chiến dịch'],
    ['/display', 'Duyệt trưng bày'],
    ['/references', 'Danh mục & nhập Excel'],
    ['/crm?view=leads', 'Cơ hội bán hàng'],
    ['/crm?view=appointments', 'Lịch hẹn'],
  ],
  '/orders': [
    ['/catalog', 'Lập đơn nhập'],
    ['/inventory', 'Kho hàng'],
    ['/delivery', 'Chuyến giao'],
    ['/npp-workflow', 'Giao bù & chăm sóc'],
  ],
  '/policies': [
    ['/inventory', 'Bảng giá & sản phẩm'],
    ['/trade', 'Hỗ trợ thị trường'],
  ],
  '/reports': [
    ['/finance', 'Công nợ & đối soát'],
    ['/exports', 'Xuất báo cáo & lịch gửi'],
  ],
  '/settings': [
    ['/network?view=organization', 'Tổ chức & vùng'],
    ['/network?view=routes', 'Tuyến & Sales'],
  ],
};

const routePermissions: Record<string, string[]> = {
  '/dashboard': ['REPORT_READ'],
  '/network': ['REPORT_READ'],
  '/npp-workflow': [
    'SELLIN_CREATE',
    'SALES_CREATE',
    'SALES_APPROVE',
    'WAREHOUSE_WRITE',
    'REPORT_READ',
  ],
  '/reports': ['REPORT_READ'],
  '/exports': ['REPORT_READ'],
  '/partners': ['CATALOG_WRITE', 'USER_MANAGE'],
  '/orders': ['SELLIN_CREATE', 'ORDER_APPROVE', 'SALES_CREATE', 'SALES_APPROVE', 'WAREHOUSE_WRITE'],
  '/catalog': ['SELLIN_CREATE', 'SALES_CREATE'],
  '/finance': ['CREDIT_WRITE', 'SELLIN_CREATE', 'CLOSING_WRITE'],
  '/inventory': ['WAREHOUSE_WRITE', 'CATALOG_WRITE', 'POLICY_WRITE'],
  '/crm': ['CRM_WRITE', 'ROUTE_WRITE'],
  '/routes': ['ROUTE_WRITE'],
  '/sales': ['SALES_CREATE'],
  '/policies': ['POLICY_WRITE', 'SELLIN_CREATE'],
  '/tickets': ['TICKET_WRITE'],
  '/settings': ['USER_MANAGE'],
  '/delivery': ['WAREHOUSE_WRITE', 'DELIVERY_EXECUTE'],
  '/demand': ['SELLIN_CREATE', 'ORDER_APPROVE'],
  '/trade': ['SELLIN_CREATE', 'TRADE_APPROVE', 'CREDIT_WRITE', 'TICKET_WRITE'],
  '/engagement': ['CRM_WRITE', 'ROUTE_WRITE'],
  '/display': ['SALES_CREATE', 'DISPLAY_REVIEW'],
  '/references': ['CATALOG_WRITE', 'CRM_WRITE'],
};
export function canVisit(path: string, user: SessionUser | null) {
  if (path === '/network' && (!user || user.nppId || user.role === 'SALES')) return false;
  if (path === '/sales' && user?.nppId && user.role !== 'SALES') return false;
  if (path === '/finance' && user?.role === 'ACCOUNTANT_NPP') return true;
  return (
    !!user &&
    (path === '/account' ||
      (routePermissions[path] || []).some((p) => user.authorities.includes(p)))
  );
}
export function homeRoute(user: SessionUser | null) {
  if (user?.role === 'SALES' && canVisit('/sales', user)) return '/sales';
  return Object.keys(routePermissions).find((p) => canVisit(p, user)) || '/account';
}

export const navigation: { title: string; items: [string, string, LucideIcon][] }[] = [
  {
    title: 'KINH DOANH',
    items: [
      ['/dashboard', 'Tổng quan', LayoutDashboard],
      ['/orders', 'Đơn hàng', ShoppingBag],
      ['/partners', 'Nhà phân phối', Building2],
      ['/catalog', 'Đặt hàng', Package],
      ['/finance', 'Công nợ & đối soát', Wallet],
    ],
  },
  {
    title: 'VẬN HÀNH',
    items: [
      ['/inventory', 'Kho & tồn hàng', Package],
      ['/crm', 'Cửa hàng & CRM', Store],
      ['/routes', 'Tuyến & đội ngũ', RouteIcon],
      ['/sales', 'Sales · Hôm nay', CalendarDays],
      ['/delivery', 'Chuyến giao', RouteIcon],
      ['/demand', 'Kế hoạch nhập', CalendarDays],
      ['/engagement', 'Báo giá & chiến dịch', Store],
      ['/display', 'Ảnh trưng bày', Package],
    ],
  },
  {
    title: 'QUẢN LÝ',
    items: [
      ['/reports', 'Báo cáo & chỉ tiêu', BarChart3],
      ['/policies', 'Chính sách & ưu đãi', Tags],
      ['/tickets', 'Trung tâm hỗ trợ', Headphones],
      ['/settings', 'Quản trị hệ thống', Settings],
      ['/trade', 'Trade marketing', Tags],
      ['/references', 'Danh mục & nhập Excel', Package],
    ],
  },
];
export const roleLabels: Record<Role, string> = {
  nsx: 'Điều hành Nhà sản xuất',
  npp: 'Nhà phân phối',
  sales: 'Nhân viên thị trường',
};
