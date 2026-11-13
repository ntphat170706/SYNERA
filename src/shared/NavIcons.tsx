// Tabler SVGs are vendored from tabler/tabler-icons under the MIT license.
function icon(name: string) {
  return function NavigationIcon({ size = 21 }: { size?: number; stroke?: number }) {
    return (
      <img
        className="navigation-icon"
        src={`/assets/icons/tabler/${name}.svg`}
        width={size}
        height={size}
        alt=""
        aria-hidden="true"
      />
    );
  };
}
export const IconLayoutDashboard = icon('layout-dashboard');
export const IconShoppingCart = icon('shopping-cart');
export const IconBuildingWarehouse = icon('building-warehouse');
export const IconAddressBook = icon('address-book');
export const IconReceipt2 = icon('receipt-2');
export const IconChartBar = icon('chart-bar');
