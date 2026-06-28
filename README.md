# pixel-tag-drawer

Google Pixel 10a + Pixel Launcher 向けの補助ランチャーアプリ。

## 目的

Pixel Launcherを置き換えず、Pixel Launcherのまま使いながら、
標準アプリドロワーでは弱い以下の機能を補う。

- アプリの用途別分類
- 複数タグ付与
- 複数タグ絞り込み
- 未分類アプリの整理
- タグ別ショートカットからの起動

## 非目的

- HOMEランチャーとしてPixel Launcherを置き換えない
- 端末内の全パッケージ管理ツールにはしない
- v0.1ではWidget、UsageStats、Google Play公開対応は対象外

## v0.1方針

- 対象は起動可能アプリのみ
- HOMEランチャー宣言はしない
- QUERY_ALL_PACKAGESは前提にしない
- Intent.ACTION_MAIN + CATEGORY_LAUNCHER で起動可能Activityを列挙する
- アプリIDは packageName + className
- Kotlin + Jetpack Compose + Room
- Material You / Dynamic Color 対応を初期から意識する
- Pinned Shortcutはv0.1で最低限検証する
