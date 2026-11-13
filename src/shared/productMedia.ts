export function unitName(unit: string) {
  return (
    (
      { THUNG: 'Thùng', CHAI: 'Chai', LOC: 'Lốc', HOP: 'Hộp', GOI: 'Gói' } as Record<string, string>
    )[unit] || unit
  );
}
/** Illustrative assets already supplied in this repository; see public/assets/npp-products/sources.json. */
export function productImage(name: string) {
  const key = name.toLocaleLowerCase('vi');
  const file = key.includes('hạnh nhân')
    ? 'almond.jpg'
    : key.includes('sữa')
      ? 'milk.png'
      : key.includes('bánh')
        ? 'cookies.png'
        : key.includes('trà')
          ? 'tea.jpg'
          : key.includes('cam')
            ? 'orange.jpg'
            : key.includes('nước')
              ? 'water.png'
              : '';
  return file ? `/assets/npp-products/${file}` : '';
}
