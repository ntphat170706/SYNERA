# SYNERA Sales native client

This is the separately requested React Native/Expo client. Do not replace or upgrade the root React 18/Vite app or its router/store. Backend remains authoritative.

- Read `../../docs/sales-mobile/README.md` and the gap matrix before implementing business actions.
- Follow `../../docs/sales-mobile/06-nguon-thiet-ke-va-tuong-thich.md`: trace behavior to the system-analysis PDF FR/BF IDs and layout/interactions to supplied mockups. Keep the shared backend contract compatible with the web; document gaps before adding writes. The first read-only prototype was replaced by the mockup-led field workflow. Avoid generic decorative dashboards.
- Current app supports Sales login, scoped reads, order quote/sync/reorder, owned order drafts, GPS check-in/note checkout, survey, interaction, ticket, appointment and daily report using existing APIs. NSX manages Sales; Sales works at an assigned NPP. Preserve NPP operational scope. Attendance, visit photos/checklists, media outbox, NSX management assignments and unsupported order types remain backlog; do not simulate them or promote Sales to an NSX admin.
- Use the existing native `/api/v1/auth` token contract. Access token stays in memory; native refresh token uses SecureStore. Never hardcode credentials. Web preview is memory-only, not a secure native storage substitute.
- Reuse branding and canonical root token values through the mobile theme. Support safe area, readable text scaling, light/dark mode and platform navigation.
- New writes require the existing scope/idempotency/server validation contracts and meaningful tests. Preserve drafts until per-item successful ACK.
- Physical iPhone reports Expo Go SDK 58; keep this isolated package aligned to SDK 58. Native API defaults to Metro's LAN host bridge, never the phone's loopback. `npm run iphone` creates `/phone` QR instructions. Web screenshots remain web evidence; native acceptance needs the user's device.
- The user explicitly requests normal feature titles with no Demo label for the prototype AI flows. Display scoring uses the existing backend mock provider and requires manual review; text-to-order currently uses catalog-based parsing, not an LLM. Keep these facts accurate in developer documentation, never fabricate image detections or final approvals. See `../../docs/sales-mobile/09-tro-ly-va-trung-bay.md`.
- Run `npm run check`, `npm test`, `npx expo install --check` and `npm run export:web` inside this directory. Native acceptance still requires a simulator/device. Keep its lockfile separate from the root pnpm lockfile.
