/** Chart.js cannot resolve CSS var() values: resolve the shared tokens at render. */
export function chartTheme() {
  const css = getComputedStyle(document.documentElement);
  const get = (key: string) => css.getPropertyValue(key).trim();
  return {
    metadata: get('--metadata'),
    border: get('--border'),
    surface: get('--surface'),
    primary: get('--chart-primary'),
    secondary: get('--chart-secondary'),
    fill: get('--chart-fill'),
    status: [
      get('--chart-primary'),
      get('--chart-secondary'),
      get('--chart-pending'),
      get('--chart-approved'),
      get('--chart-other'),
    ],
  };
}
