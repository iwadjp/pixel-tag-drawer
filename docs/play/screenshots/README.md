# Google Play phone screenshots

Google Play requires each screenshot's longer side to be no more than **twice**
the shorter side (min 320px, max 3840px). The source screenshots in
`docs/images/` are 1080×2424 (ratio 2.24), which fails that check.

These files are 1216×2424 (ratio 1.99): the original 1080×2424 image is
pasted unmodified and centered, with 68px of flat padding added on each side
(color sampled from that image's own background — no crop, no stretch, no
content change). They are for a possible future Google Play submission and
are not referenced by the current README or F-Droid metadata.

| File | Source | Padded |
|---|---|---|
| `all-apps.png` | `docs/images/all-apps.png` (1080×2424) | 1216×2424 |
| `google-tag.png` | `docs/images/google-tag.png` (1080×2424) | 1216×2424 |
| `media-google-and-filter.png` | `docs/images/media-google-and-filter.png` (1080×2424) | 1216×2424 |
| `tag-management.png` | `docs/images/tag-management.png` (1080×2424) | 1216×2424 |
