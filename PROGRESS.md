# PROGRESS

pixel-tag-drawer の公開用進捗ログ。実機確認やマイルストーンを記録する。

---

## 2026-06-28 起動可能アプリ一覧の実機確認

- **対象コミット**: `4fdb7da List launchable apps`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **結果**: test ok

### 確認項目

- [x] APK ダウンロード
- [x] インストール
- [x] アプリ起動
- [x] 起動可能アプリ一覧表示
- [x] 一覧からのタップ起動

### 判断

v0.1 の最初の実用品の芯として受け入れ。
「起動可能アプリ一覧表示 + タップ起動」が実機で一通り動作することを確認できた。

### 次候補

- アイコン表示
- ローディング / 0件表示
- 検索 / 簡易フィルタ
- Room / タグDB

---

## 2026-06-28 アプリアイコン表示の実機確認

- **対象コミット**: `05234ed Show app icons in launchable app list`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**: アプリアイコン表示付き起動可能アプリ一覧
- **結果**: test ok

### 確認項目

- [x] APK ダウンロード
- [x] インストール
- [x] アプリ起動
- [x] 起動可能アプリ一覧表示
- [x] アプリアイコン表示
- [x] 一覧からのタップ起動

### 判断

Pixel Launcher 補助ランチャーとして、テキスト一覧よりも実用性が上がった。
アイコン付き一覧が実機で一通り動作することを確認できた。

### 次候補

- ローディング / 0件表示
- 検索 / 簡易フィルタ
- Room / タグDB

---

## 2026-06-28 検索 / 簡易フィルタの実機確認

- **対象コミット**: `15764da Add app list search filter`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**: アプリ名 / packageName 検索・簡易フィルタ
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] 検索欄表示
- [x] アプリ名検索
- [x] packageName検索
- [x] 大文字小文字を区別しない検索
- [x] 該当なし表示「一致するアプリがありません」
- [x] 検索文字削除で全件復帰
- [x] アイコン表示維持
- [x] 一覧からのタップ起動維持

### 判断

Pixel Launcher 補助ランチャーとして、アプリ数が多い場合の探索性が向上した。
次は Room / タグDB の土台に進める。

### 次候補

- Room / タグDB

---

## 2026-06-28 Room DB 同期の実機確認

- **対象コミット**: `9c31c67 Sync launchable apps to database`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**: 起動可能アプリ一覧の Room DB 同期 (launcher_apps への upsert)
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] 起動可能アプリ一覧表示
- [x] アプリアイコン表示
- [x] アプリ名 / packageName 検索
- [x] 一覧からのタップ起動
- [x] DB同期追加後もクラッシュなし

### 判断

Room DB 土台が実際に動作する段階に進んだ。
既存UIを壊さず、launcher_apps への同期を追加できた。
次は TagRepository または最小タグ作成UIに進める。

### 次候補

- TagRepository (TagDao / AppTagDao を束ねる)
- 最小タグ作成UI

---

## 2026-06-28 最小タグ作成UIの実機確認

- **対象コミット**: `4447079 Add minimal tag creation UI`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**: 最小タグ作成UI (タグ名入力 → タグ作成 → タグ一覧表示)
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] タグ入力欄表示
- [x] 「タグ追加」ボタン表示
- [x] 新しいタグ追加
- [x] 追加タグの一覧即時反映
- [x] 空文字でクラッシュしない
- [x] 重複タグでクラッシュしない
- [x] アプリ再起動後もタグが残る
- [x] 既存のアプリ一覧表示
- [x] アプリアイコン表示
- [x] アプリ名 / packageName 検索
- [x] 一覧からのタップ起動

### 判断

タグ機能が初めてUIから操作可能になった。
Room / TagRepository / TagViewModel / Compose UI の一連の流れが実機で確認できた。

### 次候補

- タグ削除UI
- アプリへのタグ割り当てUI

---

## 2026-06-28 タグ削除UIの実機確認

- **対象コミット**: `fd69cd5 Add tag deletion UI`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**: タグ削除UI (タグ一覧の各行に「削除」ボタン)
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] 既存タグ表示
- [x] 「削除」ボタン表示
- [x] タグ削除で一覧から即時消える
- [x] 最後のタグ削除後に「タグがありません」へ戻る
- [x] 削除時にクラッシュしない
- [x] タグ作成が引き続き動作する
- [x] 作成後に削除できる
- [x] アプリ再起動後も削除状態が維持される
- [x] 既存のアプリ一覧表示
- [x] アプリ名 / packageName 検索
- [x] 一覧からのタップ起動

### 判断

タグの作成・表示・削除・永続化の基本サイクルが実機で確認できた。

### 次候補

- 削除確認ダイアログ
- タグ一覧の見た目改善
- アプリへのタグ割り当てUI

---

## 2026-06-28 タグ操作エリアと検索欄の固定表示の実機確認

- **対象コミット**: `c5cb6dc Keep tag controls and search visible`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**: タグ操作エリアと検索欄の固定表示 (アプリ一覧のみスクロール)
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] 一覧を下へスクロールしても検索欄が上部に残る
- [x] 一覧を下へスクロールしてもタグ操作エリアが上部に残る
- [x] アプリ一覧だけがスクロールする
- [x] 件数表示が固定エリアに残る
- [x] タグパネル展開時も一覧がスクロールできる
- [x] 検索0件時も表示が崩れない
- [x] アプリ行タップ起動が従来どおり動作
- [x] 「タグ」ボタンが従来どおり動作
- [x] Checkbox ON/OFF が従来どおり動作
- [x] タグ作成・削除が従来どおり動作
- [x] アプリ名 / packageName 検索が従来どおり動作

### 判断

アプリ一覧をスクロールしても、検索とタグ操作へ戻る手間が減った。
アプリへのタグ割り当て作業の実用性が上がった。

### 次候補

- アプリへのタグ割り当てUIの実機確認記録
- タグによるアプリ一覧絞り込み

---

## 2026-06-28 アプリへのタグ割り当てUIの実機確認

- **対象コミット**: `8de95df Add app tag assignment UI`
- **関連UX改善コミット**: `c5cb6dc Keep tag controls and search visible`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**: アプリへのタグ割り当てUI (選択中アプリへのタグON/OFF)
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] アプリ一覧の各行に「タグ」ボタン表示
- [x] アプリ行タップは従来どおりアプリ起動
- [x] 「タグ」ボタンで選択中アプリのタグパネル表示
- [x] Checkbox ON でタグ付与
- [x] Checkbox OFF でタグ解除
- [x] 同じアプリを再選択するとチェック状態が保持されている
- [x] 別アプリを選択するとチェック状態が正しく切り替わる
- [x] 「閉じる」で選択解除
- [x] タグがない場合の「タグがありません」表示
- [x] タグ作成・削除が引き続き動作
- [x] アプリ再起動後もタグ割り当て状態が維持される
- [x] 固定表示UX改善後もタグ割り当て操作が可能
- [x] 既存のアプリ一覧表示
- [x] アプリアイコン表示
- [x] アプリ名 / packageName 検索
- [x] 一覧からのタップ起動

### 判断

アプリにタグを付け外しする中核機能が実機で確認できた。
タグ作成・削除・割り当て・永続化の基本サイクルが成立した。

### 次候補

- タグによるアプリ一覧絞り込み
- アプリ一覧行への付与済みタグ表示

---

## 2026-06-28 タグによるアプリ一覧絞り込みの実機確認

- **対象コミット**: `0105372 Add tag-based app filtering`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**: タグによるアプリ一覧絞り込み (複数タグAND・検索とAND合成)
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] タグフィルタチップ表示
- [x] タグチップONで該当アプリだけに絞り込まれる
- [x] タグチップOFFで絞り込み解除
- [x] 複数タグONでAND条件になる
- [x] 「解除」で全タグフィルタが外れる
- [x] 件数表示が絞り込み結果に追従
- [x] 該当0件で「一致するアプリがありません」表示
- [x] アプリ名 / packageName 検索とタグ条件がANDで合成される
- [x] タグ割り当て変更時に絞り込み結果が自動更新される
- [x] タグ削除時に絞り込み選択が外れ、表示が崩れない
- [x] タグ作成・削除・割り当てが引き続き動作
- [x] アプリ行タップ起動が従来どおり動作
- [x] 検索欄・タグ操作エリアの固定表示が維持されている

### 判断

複数タグANDによるアプリ一覧絞り込みが実機で確認できた。
当初仕様の「複数タグを指定した一覧表示」に到達した。

### 次候補

- アプリ一覧行への付与済みタグ表示
- 絞り込み条件の保存

---

## 2026-06-28 アプリ一覧行への付与済みタグ名表示の実機確認

- **対象コミット**: `296c39c Show assigned tags in app list`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**: アプリ一覧行への付与済みタグ名表示 (例: #Google #仕事)
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] 付与済みタグがあるアプリ行に #タグ名 が表示される
- [x] タグ未付与のアプリ行にはタグ表示が出ない
- [x] 複数タグが付いたアプリで複数の #タグ名 が表示される
- [x] タグ付与すると一覧行のタグ表示が反映される
- [x] タグ解除すると一覧行のタグ表示が消える
- [x] タグ削除すると一覧行のタグ表示も消える
- [x] アプリ行タップ起動が従来どおり動作
- [x] 「タグ」ボタンが従来どおり動作
- [x] タグ絞り込みが従来どおり動作
- [x] 検索が従来どおり動作
- [x] タグ名が長い場合でも致命的に崩れない

### 判断

一覧上でタグ付与状態を確認できるようになった。
タグ付け・絞り込み・一覧確認のサイクルが実用的になった。

### 次候補

- 絞り込み条件の保存
- タグ表示の見た目改善
- 小規模なUX polish

---

## 2026-06-28 上部操作エリア圧縮・不要説明文削除・検索一発クリアの実機確認

- **対象コミット**: `11a13a4 Compact controls and add search clear`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**: 上部操作エリア圧縮・不要説明文削除・検索一発クリア
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] 不要な説明文が消えている
- [x] 上部ヘッダが低くなっている
- [x] アプリ表示領域が広がっている
- [x] 検索入力時だけ「✕」が表示される
- [x] 「✕」で検索文字列を一発クリアできる
- [x] クリア後に全件表示へ戻る
- [x] タグ作成・削除が従来どおり動作
- [x] アプリへのタグ付与/解除が従来どおり動作
- [x] タグ絞り込みが従来どおり動作
- [x] 一覧行の付与済みタグ表示が従来どおり動作
- [x] アプリ行タップ起動が従来どおり動作

### 判断

上部操作エリアが小さくなり、アプリ表示領域が改善した。
検索文字列の一発削除により操作性が改善した。
dogfooding FB の A「メニューが高すぎる」「不要説明文」、B「検索一発削除」に対応した。

### 次候補

- リスト / アイコン表示切替
- タグ名変更
- 初期タグフィルタ

---

## 2026-06-28 リスト表示 / アイコン表示切替の実機確認

- **対象コミット**: `160a830 Add app display mode toggle`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**: リスト表示 / アイコン表示切替 (4列グリッド)
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] 「リスト」「アイコン」切替表示
- [x] リスト表示が従来どおり動作
- [x] アイコン表示で4列グリッド表示
- [x] アイコン表示で従来より多くのアプリが見える
- [x] グリッドのアプリ名が1行省略で崩れない
- [x] グリッドセルタップでアプリ起動
- [x] グリッド内の「タグ」ボタンでタグ割り当てパネルが開く
- [x] 検索結果がリスト/アイコン両方に反映
- [x] タグ絞り込み結果がリスト/アイコン両方に反映
- [x] 固定エリアが残り、一覧部分だけスクロール
- [x] 既存のタグ作成/削除/付与/解除が壊れていない

### 判断

アプリ表示密度が改善し、常用性が上がった。
dogfooding FB の A「アプリがリスト表示なので表示できる数が少ない」に対応した。

### 次候補

- タグ名変更
- 初期タグフィルタ
- 表示モード永続化
- undo/redo

---

## 2026-06-28 タグ名変更UIの実機確認

- **対象コミット**: `5722e20 Add tag rename UI`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**: タグ名変更UI (タグ一覧でのインライン編集)
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] タグ行の「変更」ボタン表示
- [x] 「変更」で既存タグ名入りの入力欄が表示される
- [x] 「保存」でタグ名を変更できる
- [x] 「キャンセル」で編集状態を解除できる
- [x] 空文字保存でクラッシュせずメッセージ表示
- [x] 重複名保存でクラッシュせずメッセージ表示
- [x] タグ名変更後、タグ一覧に反映される
- [x] タグ名変更後、フィルタチップに反映される
- [x] タグ名変更後、アプリ一覧行の #タグ名 に反映される
- [x] タグ名変更後、選択中アプリのタグON/OFFパネルに反映される
- [x] リネーム後も tagId が維持され、既存のタグ付与状態・絞り込み状態が壊れない
- [x] 既存のタグ作成・削除・付与/解除が従来どおり動作
- [x] リスト / アイコン表示切替が従来どおり動作
- [x] 検索・検索一発クリアが従来どおり動作
- [x] アプリ行/グリッドセルタップ起動が従来どおり動作

### 判断

dogfooding FB-A「タグ名が変更できない」に対応した。
タグ運用中に名前を調整できるようになり、常用性が上がった。
DBスキーマ変更なしで既存 tagId と割り当てを維持できた。

### 次候補

- 初期タグフィルタ
- 表示モード永続化
- undo/redo

---

## 2026-06-28 タグ管理UI折りたたみの実機確認

- **対象コミット**: `83d3c74 Collapse tag management controls`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**: タグ管理UI折りたたみ (作成/変更/削除を必要時のみ表示)
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] 起動直後はタグ管理UIが折りたたまれている
- [x] 起動直後の上部固定エリアが低くなっている
- [x] アプリ一覧が以前より高い位置から始まる
- [x] 「タグ管理」ボタンでタグ作成・一覧・変更・削除・リネームUIが展開される
- [x] 「閉じる」ボタンでタグ管理UIを折りたためる
- [x] 折りたたみ中でも検索欄が使える
- [x] 折りたたみ中でも検索一発クリアが使える
- [x] 折りたたみ中でもタグ絞り込みチップが使える
- [x] 折りたたみ中でも件数表示が正しい
- [x] 折りたたみ中でもリスト / アイコン切替が使える
- [x] アプリ行/グリッドの「タグ」ボタンで選択中アプリのタグON/OFFパネルが従来どおり表示される
- [x] 展開時のタグ作成・削除・リネームが従来どおり動作
- [x] アプリへのタグ付与/解除が従来どおり動作
- [x] アプリ起動が従来どおり動作

### 判断

dogfooding FB-A「メニューの高さが高すぎる」への本命対応になった。
タグ管理操作を必要時だけ表示することで、常用時のアプリ表示領域が改善した。
検索・タグ絞り込み・表示切替などの常用操作は維持できた。

### 次候補

- 初期タグフィルタ
- 表示モード永続化
- 折りたたみ状態永続化
- undo/redo

---

## 2026-06-28 タグ管理ボタンのステータスバー被り修正の実機確認

- **対象コミット**: `cc6856d Fix tag management button overlapping status bar`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**: タグ管理ボタンのステータスバー被り修正 (statusBarsPadding)
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] 起動直後にタイトル行がステータスバーに被らない
- [x] 「タグ管理」ボタンがステータスバーに被らない
- [x] 「タグ管理」ボタンを確実にタップできる
- [x] 「タグ管理」でタグ管理UIを展開できる
- [x] 「閉じる」でタグ管理UIを折りたためる
- [x] 折りたたみ/展開の両状態で上部がステータスバーに被らない
- [x] 検索・検索一発クリアが従来どおり動作
- [x] タグ絞り込みが従来どおり動作
- [x] リスト / アイコン切替が従来どおり動作
- [x] タグ作成・削除・リネームが従来どおり動作
- [x] アプリへのタグ付与/解除が従来どおり動作
- [x] アプリ起動が従来どおり動作

### 判断

Android 15 / targetSdk 35 の edge-to-edge で上部固定エリアがステータスバーに被る問題を解消した。
`statusBarsPadding()` による最小修正で、タグ管理ボタンのタップ不能を解消した。
上部インセット対応のみで、下部ナビゲーションバー対応は別件として残した。

### 次候補

- 下端ナビゲーションバー被り確認
- 初期タグフィルタ
- 表示モード永続化
- undo/redo

---

## 2026-06-28 タグ編集モード (通常時のタグボタン非表示) の実機確認

- **対象コミット**: `b0d3bf3 Hide app tag buttons outside edit mode`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**: タグ編集モード、通常時のアプリごとのタグボタン非表示
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] 通常時、リスト表示の各アプリ行に「タグ」ボタンが表示されない
- [x] 通常時、グリッド表示の各アプリセルに「タグ」ボタンが表示されない
- [x] 通常時、行/セルタップでアプリ起動できる
- [x] 「タグ編集」ONでリスト表示の各アプリ行に「タグ」ボタンが表示される
- [x] 「タグ編集」ONでグリッド表示の各アプリセルに「タグ」ボタンが表示される
- [x] 「タグ」ボタンで選択中アプリのタグON/OFFパネルが開く
- [x] タグ付与/解除が従来どおり動作
- [x] 「タグ編集」OFFで「タグ」ボタンが消える
- [x] 「タグ編集」OFFで開いていたタグON/OFFパネルが閉じる
- [x] 検索・検索一発クリアが従来どおり動作
- [x] タグ絞り込みが従来どおり動作
- [x] リスト/アイコン表示切替が従来どおり動作
- [x] アプリ起動が従来どおり動作

### 判断

dogfooding FB「アプリごとのタグボタンは無駄」に対応した。
通常時のアプリ一覧がランチャー用途に近づき、表示密度が改善した。
タグ編集は必要時だけONにする導線へ整理できた。

### 次候補

- 初期タグフィルタ
- 表示モード永続化
- undo/redo

---

## 2026-06-28 リスト/グリッド下端のナビゲーションバー被り修正の実機確認

- **対象コミット**: `337a8af Avoid bottom navigation overlap in app lists`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**: リスト/グリッド下端のナビゲーションバー被り修正
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] リスト表示で最下部までスクロールしても最後のアプリ行がナビゲーションバーに隠れない
- [x] リスト表示で最後のアプリ名/packageNameがナビゲーションバーに重ならない
- [x] タグ編集ON時、リスト表示の最後の「タグ」ボタンが押しづらくない
- [x] グリッド表示で最下部までスクロールしても最後のセルがナビゲーションバーに隠れない
- [x] グリッド表示で最後のアプリ名がナビゲーションバーに重ならない
- [x] タグ編集ON時、グリッド表示の最後の「タグ」ボタンが押しづらくない
- [x] 上部のステータスバー被り修正が壊れていない
- [x] 検索・検索一発クリアが従来どおり動作
- [x] タグ絞り込みが従来どおり動作
- [x] リスト/アイコン表示切替が従来どおり動作
- [x] アプリ起動が従来どおり動作

### 判断

Android のナビゲーションバーにリスト/グリッド末尾が重なる問題を解消した。
WindowInsets.navigationBars に基づく下端余白で、固定エリアを押し上げず一覧末尾だけを安全領域から逃がせた。
上部 statusBarsPadding 対応と合わせて、edge-to-edge 環境での上下インセット問題が改善した。

### 次候補

- 初期タグフィルタ
- 表示モード永続化
- undo/redo

---

## 2026-06-28 表示状態とタグフィルタの永続化の実機確認

- **対象コミット**: `2cf3a66 Persist display and filter preferences`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**: 表示状態とタグフィルタの永続化 (SharedPreferences)
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] タグ絞り込みを選択した状態でアプリ完全終了→再起動すると、同じタグ絞り込みが復元される
- [x] 複数タグAND絞り込みも再起動後に復元される
- [x] タグ絞り込み解除後に再起動すると、解除状態で起動する
- [x] アイコン表示に切り替えた状態で再起動すると、アイコン表示で復元される
- [x] リスト表示に戻した状態で再起動すると、リスト表示で復元される
- [x] タグ管理を開いた状態で再起動すると、タグ管理が開いた状態で復元される
- [x] タグ管理を閉じた状態で再起動すると、閉じた状態で復元される
- [x] タグ編集モードは再起動後に必ずOFFになる
- [x] 検索欄は再起動後に空で起動する
- [x] 選択中アプリのタグON/OFFパネルは再起動後に開かない
- [x] 保存済みタグフィルタに削除済みタグIDが含まれてもクラッシュしない
- [x] 検索・検索一発クリアが従来どおり動作
- [x] タグ作成・削除・リネームが従来どおり動作
- [x] アプリへのタグ付与/解除が従来どおり動作
- [x] アプリ起動が従来どおり動作
- [x] 上下インセット対応が従来どおり動作

### 判断

dogfooding FB-A「タグを絞り込みした初期表示ができない」に対応した。
前回のタグ絞り込み、表示モード、タグ管理開閉状態を復元できるようになり、常用性が上がった。
タグ編集モード・検索文字列・選択中アプリは永続化せず、起動時の安全性を維持した。
DataStore/Gradle依存/DBスキーマ変更なしで最小実装できた。

### 次候補

- undo/redo
- タグ並び替え
- タグ色
- dogfooding継続

---

## 2026-06-28 アプリへのタグ付与/解除 Undo/Redo の実機確認

- **対象コミット**: `e932335 Add undo redo for app tag edits`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**: アプリへのタグ付与/解除に限定した Undo/Redo
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] タグ編集ONで選択中アプリのタグON/OFFパネルが表示される
- [x] 履歴がない時、「元に戻す」「やり直す」が無効
- [x] タグ付与後、「元に戻す」が有効になる
- [x] タグ付与→「元に戻す」でタグ解除される
- [x] タグ付与→「元に戻す」後、「やり直す」で再付与される
- [x] タグ解除後、「元に戻す」が有効になる
- [x] タグ解除→「元に戻す」で再付与される
- [x] タグ解除→「元に戻す」後、「やり直す」で再解除される
- [x] Checkbox状態がUndo/Redoに追従する
- [x] アプリ一覧行の #タグ名表示がUndo/Redoに追従する
- [x] タグ絞り込み結果がUndo/Redoに追従する
- [x] 複数回の連続操作で多段Undo/Redoが動作する
- [x] 新規タグ付与/解除後、Redo履歴が無効化される
- [x] アプリ再起動後、Undo/Redo履歴は復元されない
- [x] タグ作成・削除・リネームはUndo/Redo対象外のまま
- [x] 検索・検索一発クリアが従来どおり動作
- [x] リスト/アイコン表示切替が従来どおり動作
- [x] 表示状態永続化が従来どおり動作
- [x] 上下インセット対応が従来どおり動作
- [x] アプリ起動が従来どおり動作

### 判断

dogfooding FB-B「undo/redoができない」に、まずアプリへのタグ付与/解除限定で対応した。
誤ってタグを付けた/外した場合に戻せるようになり、タグ編集時の安心感が上がった。
Undo/Redo履歴はメモリ上のみで、再起動後に残さない安全な仕様とした。
タグ作成・削除・リネーム等へのUndo/Redo拡張は今後の別設計対象とした。

### 次候補

- Undo/Redo 対象拡張 (タグ作成/削除/リネーム)
- タグ並び替え
- タグ色
- dogfooding継続

---

## 2026-06-28 タグなしフィルタ・タグ管理一覧性改善・個別パネル非表示化の実機確認

- **対象コミット**: `1710867 Add untagged filter and improve tag management`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**:
  - タグなしフィルタ
  - タグ管理の一覧性改善
  - 個別タグ編集パネル非表示化
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] タグ管理を開いた時、タグ一覧が以前より広く表示される
- [x] タグが多い場合でも縦スクロールで全タグへ到達できる
- [x] タグ作成
- [x] タグ削除
- [x] タグ名変更
- [x] 個別「タグ」ボタンがアプリ行/グリッドセルに表示されない
- [x] 個別タグ編集パネルが表示されず、上部固定エリアを圧迫しない
- [x] 「タグなし」フィルタでタグ未付与アプリのみ表示される
- [x] 「タグなし」と通常タグフィルタが排他動作する
- [x] 検索文字列 + 「タグなし」がAND条件で動作する
- [x] 件数表示が「タグなし」絞り込み結果に追従する
- [x] List表示に「タグなし」フィルタが反映される
- [x] Grid表示に「タグなし」フィルタが反映される
- [x] 「タグなし」選択状態が再起動後に復元される
- [x] 「タグなし」で絞り込み→複数選択→一括付与で、付与済みアプリがタグなし一覧から外れる
- [x] 一括タグ付与/解除
- [x] 通常タグ絞り込み・複数タグAND
- [x] 検索・検索一発クリア
- [x] リスト/アイコン表示切替
- [x] 表示状態永続化
- [x] 上下インセット対応
- [x] 通常時のアプリ起動

### 判断

dogfooding FB「タグが多い時、タグ管理で表示されないタグがある」に対応した。
dogfooding FB「タグが付いていないアプリを絞り込みたい」に対応した。
追加FB「タグ管理のタグ一覧の高さが狭く、一覧性が悪い」に対応した。
追加FB「個別タグ編集パネルは不要で、タグが見切れて選べない」に対応した。
タグ付けの主導線を、個別編集ではなく「タグなし絞り込み + 一括タグ編集」中心に整理した。
初期タグ整理の実用性が向上した。

### 次候補

- タグ並び替え
- タグ色
- Undo/Redo 対象拡張
- dogfooding継続

---

## 2026-06-28 複数アプリ一括タグ付与/解除の実機確認 (後追い記録)

- **対象コミット**: `529b692 Add compact bulk app tag editing`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**:
  - 複数アプリへの一括タグ付与/解除
  - コンパクトな一括タグ付けUI
- **結果**: test ok
- **備考**: 529b692 時点で記録漏れだったため後追いで追記。機能はその後の実機確認でも動作確認済み。

### 確認項目

- [x] APK更新インストール
- [x] アプリ起動
- [x] タグ編集ONで一括選択UIが表示される
- [x] List表示で複数アプリを選択できる
- [x] Grid表示で複数アプリを選択できる
- [x] List表示で選択状態がCheckboxで分かる
- [x] Grid表示で選択状態が背景ハイライトで分かる
- [x] 一括UIが2行程度に収まり、アプリ一覧が十分見える
- [x] 「付与」「解除」「クリア」ボタンが見切れずタップできる
- [x] タグチップが横スクロールで選べる
- [x] 複数アプリ選択→対象タグ選択→「付与」で選択アプリへタグ付与できる
- [x] 一括付与後、#タグ名表示が反映される
- [x] 一括付与後、タグ絞り込み結果に反映される
- [x] 複数アプリ選択→対象タグ選択→「解除」で選択アプリからタグ解除できる
- [x] 未付与アプリが混ざっていても一括解除でクラッシュしない
- [x] 「クリア」で一括選択が解除される
- [x] タグ編集OFFで一括選択と対象タグ選択がクリアされる
- [x] 一括操作はUndo/Redo履歴に入らない
- [x] 通常時は行/セルタップでアプリ起動できる
- [x] 検索・検索一発クリアが従来どおり動作
- [x] タグ絞り込み・複数タグANDが従来どおり動作
- [x] リスト/アイコン表示切替が従来どおり動作
- [x] 表示状態永続化が従来どおり動作
- [x] 上下インセット対応が従来どおり動作

### 判断

dogfooding FB「複数のアプリに、一度にタグを付けたい」に対応した。
初期タグ付け作業の手間が大きく減った。
一括UIをコンパクト化し、ボタン見切れと上部過密を改善した。
一括操作はUndo/Redo対象外とし、個別タグ編集のUndo/Redoとは分離した。
その後の 1710867 / 562676f で、タグ付け主導線は「タグなし絞り込み + 一括タグ編集」中心に整理された。

### 次候補

- タグ並び替え
- タグ色
- Undo/Redo 対象拡張
- dogfooding継続

---

## 2026-06-28 タグ指定起動 Intent 対応の実機確認

- **対象コミット**: `bfc35e5 Add launch filter intent support`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由 + adb 起動確認
- **対象機能**:
  - tagId指定起動
  - タグなし指定起動
  - 将来の Pinned Shortcut 用 Intent 土台
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] 通常起動
- [x] 保存済みフィルタ復元
- [x] tagId指定起動で対象タグ絞り込み状態になる
- [x] タグなし指定起動で未付与アプリのみ表示される
- [x] tagId指定とタグなし指定の両方指定時、タグなしが優先される
- [x] 不正tagIdでクラッシュしない
- [x] 不正tagId時は実質フィルタなし相当になる
- [x] 起動指定後に画面上でフィルタ変更できる
- [x] 検索・検索一発クリアが従来どおり動作
- [x] 一括タグ付与/解除が従来どおり動作
- [x] タグ管理が従来どおり動作
- [x] リスト/アイコン表示切替が従来どおり動作
- [x] 上下インセット対応が従来どおり動作
- [x] アプリ起動が従来どおり動作

### 確認用 adb コマンド

```
# 通常起動
adb shell am start -n com.iwadjp.pixeltagdrawer/.MainActivity
# タグID指定起動 (例: tagId=1)
adb shell am start -n com.iwadjp.pixeltagdrawer/.MainActivity --el com.iwadjp.pixeltagdrawer.extra.FILTER_TAG_ID 1
# タグなし指定起動
adb shell am start -n com.iwadjp.pixeltagdrawer/.MainActivity --ez com.iwadjp.pixeltagdrawer.extra.SHOW_UNTAGGED_ONLY true
# 両方指定 (タグなし優先)
adb shell am start -n com.iwadjp.pixeltagdrawer/.MainActivity --el com.iwadjp.pixeltagdrawer.extra.FILTER_TAG_ID 1 --ez com.iwadjp.pixeltagdrawer.extra.SHOW_UNTAGGED_ONLY true
# 不正tagId (クラッシュせずフィルタなし相当)
adb shell am start -n com.iwadjp.pixeltagdrawer/.MainActivity --el com.iwadjp.pixeltagdrawer.extra.FILTER_TAG_ID 999999
```

### 判断

Pinned Shortcut 実装前の土台として、外部Intentから指定タグ/タグなしで開けるようになった。
通常起動時の保存済みフィルタ復元は維持した。
起動指定がある場合は保存済みフィルタより起動指定を優先する仕様とした。
次はこの Intent 土台を使って Pinned Shortcut 作成UIへ進める。

### 次候補

- Pinned Shortcut 作成UI
- タグ並び替え
- タグ色
- dogfooding継続

---

## 2026-06-29 タグ別 / タグなし Pinned Shortcut 作成UIの実機確認

- **対象コミット**: `87af78e Add pinned shortcuts for tag filters`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**:
  - タグ別 Pinned Shortcut 作成UI
  - タグなし Pinned Shortcut 作成UI
  - Pixel Launcher ホーム画面から指定タグ/タグなしで起動する導線
- **結果**: test ok
- **訂正注記 (後日判明)**: この `test ok` 記録の後、実運用で「ホーム」押下でホーム画面に追加されない / 「タグなしを追加」後に #Admin の「ホーム」押下でアプリ終了、の不具合が判明した。原因は ShortcutInfo の icon 未設定と推定。`8568dae Fix pinned shortcut creation` で generated icon 付与と requestPin 堅牢化を実施し、再確認は後続セクション (2026-06-29 Pinned Shortcut 作成失敗/クラッシュ修正の再確認) を参照。本節の test ok は訂正対象。

### 確認項目

- [x] APK更新インストール
- [x] 通常起動
- [x] タグ管理を開くと各タグ行に「ホーム」ボタンが表示される
- [x] タグ管理内に「タグなしを追加」ボタンが表示される
- [x] タグ別「ホーム」押下で Pixel Launcher のホーム追加確認が出る
- [x] タグ別ショートカットをホーム画面に追加できる
- [x] 追加したタグ別ショートカットをタップすると対象タグで絞り込まれた状態で開く
- [x] 「タグなしを追加」押下で Pixel Launcher のホーム追加確認が出る
- [x] タグなしショートカットをホーム画面に追加できる
- [x] タグなしショートカットをタップするとタグ未付与アプリのみ表示される
- [x] 通常アプリアイコン起動では保存済みフィルタ復元が維持される
- [x] ショートカット起動後も画面上でフィルタ変更できる
- [x] タグ削除後の古いタグ別ショートカットでもクラッシュしない
- [x] タグ削除後の古いタグ別ショートカットは実質フィルタなし相当で開く
- [x] タグ名変更後、既存ショートカット名は自動更新されない
- [x] 検索・検索一発クリアが従来どおり動作
- [x] 一括タグ付与/解除が従来どおり動作
- [x] タグ管理が従来どおり動作
- [x] リスト/アイコン表示切替が従来どおり動作
- [x] 上下インセット対応が従来どおり動作
- [x] アプリ起動が従来どおり動作

### 判断

bfc35e5 の起動 Intent 土台を利用して、Pixel Launcher ホーム画面からタグ別/タグなしで直接開けるようになった。
Pixel Launcher 補助ランチャーとしての運用導線が大きく改善した。
タグ別/タグなしショートカットは最小実装として受け入れ。
タグ色付きアイコン、ショートカット名の自動更新、ショートカット一覧管理/削除UIは今後の別設計対象。
タグ行に「ホーム」「変更」「削除」が並ぶため、タグ名が長い場合の窮屈さは今後のdogfoodingで確認する。

### 次候補

- タグ色付きアイコン
- ショートカット名の自動更新 / 一覧管理
- タグ並び替え
- dogfooding継続

---

## 2026-06-29 Pinned Shortcut 作成失敗/クラッシュ修正の再確認

### 訂正注記 (b46760e について)

- `b46760e Record pinned shortcut device test` (対象 `87af78e`) の `test ok` 記録の後、実運用で以下が判明した:
  - タグ管理の「ホーム」押下でホーム画面に何も追加されない
  - 「タグなしを追加」押下後に #Admin の「ホーム」押下でアプリが終了した
- 原因は ShortcutInfo の icon 未設定と推定。
- `8568dae Fix pinned shortcut creation` で generated icon 付与と requestPin の堅牢化 (null/非対応/構築・リクエスト例外/false戻りを全て安全化) を実施した。
- b46760e の `test ok` 記録は、後続の不具合判明により訂正対象とする (上記 b46760e 節にも訂正注記を追記済み)。

### 再確認記録

- **対象コミット**: `8568dae Fix pinned shortcut creation`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**:
  - generated icon 付き Pinned Shortcut 作成
  - タグ別ショートカット作成
  - タグなしショートカット作成
  - requestPinShortcut 失敗/例外時のクラッシュ回避
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] 通常起動
- [x] タグ管理表示
- [x] 「タグなしを追加」押下で Pixel Launcher のホーム追加確認UIが出る
- [x] タグなしショートカットをホーム画面へ追加できる
- [x] タグなしショートカットに generated icon が表示される
- [x] タグなしショートカットタップでタグ未付与アプリのみ表示される
- [x] 「タグなしを追加」後に #Admin の「ホーム」を押してもアプリが終了しない
- [x] #Admin の「ホーム」押下で Pixel Launcher のホーム追加確認UIが出る
- [x] #Admin ショートカットをホーム画面へ追加できる
- [x] #Admin ショートカットに generated icon が表示される
- [x] #Admin ショートカットタップで #Admin タグ絞り込み状態で開く
- [x] 同じタグの「ホーム」を複数回押してもクラッシュしない
- [x] 「タグなしを追加」と別タグの「ホーム」を連続操作してもクラッシュしない
- [x] 通常アプリアイコン起動では保存済みフィルタ復元が維持される
- [x] 検索・検索一発クリアが従来どおり動作
- [x] 一括タグ付与/解除が従来どおり動作
- [x] タグ管理が従来どおり動作
- [x] リスト/アイコン表示切替が従来どおり動作
- [x] アプリ起動が従来どおり動作
- [ ] logcat で AndroidRuntime 例外なしを確認 (adb 未使用のため未実施)

### 判断

87af78e の Pinned Shortcut 最小実装は icon 未設定により実運用で不具合があった。
8568dae により generated icon を必ず付与し、Pixel Launcher でホーム追加確認UIが出る状態に改善した。
「タグなしを追加」後に #Admin の「ホーム」を押す連続操作でもアプリ終了しなくなった。
タグ別/タグなしショートカット導線を再度 dogfooding 可能な状態に戻した。
タグ色付きアイコン、ショートカット名自動更新、ショートカット一覧管理は今後の別設計対象。

### 次候補

- タグ色付きアイコン
- ショートカット名の自動更新 / 一覧管理
- タグ並び替え
- dogfooding継続

---

## 2026-06-29 タグフィルタ操作・ショートカット起動時UI・一括クリア仕様の実機確認

- **対象コミット**: `4672961 Improve tag filter and shortcut launch UI`
- **対象端末**: Google Pixel 10a
- **配布方法**: SafeDrop APK List 経由で debug APK を配布
- **対象機能**:
  - タグフィルタ通常クリックの単一切替
  - 複数選択モードによる複数タグAND
  - ショートカット/外部フィルタ起動時の簡素表示
  - 「編集」→通常UI→「一覧に戻る」の往復導線
  - 一括タグ編集「クリア」で対象タグも解除
- **結果**: test ok

### 確認項目

- [x] APK更新インストール
- [x] 通常起動
- [x] 複数選択OFFでタグクリックが単一選択になる
- [x] 別タグクリックで選択タグが切り替わる
- [x] 同じタグ再クリックで解除される
- [x] 「タグなし」と通常タグが排他動作する
- [x] 「複数選択」ONで複数タグを同時選択できる
- [x] 複数タグANDで絞り込みされる
- [x] 「複数選択」OFFで複数選択が解除され、以後単一切替になる
- [x] 再起動後、不意に複数ANDにならない
- [x] タグ別ショートカット起動で簡素表示になる
- [x] タグ別ショートカット起動時、編集系UIが初期表示されない
- [x] タグ別ショートカット起動時、「編集」で通常UIへ入れる
- [x] 「一覧に戻る」で簡素表示へ戻れる
- [x] 「一覧に戻る」後も対象タグ絞り込み条件が維持される
- [x] タグなしショートカット起動で簡素表示になる
- [x] タグなしショートカット起動時、「編集」→通常UI→「一覧に戻る」ができる
- [x] 通常アプリアイコン起動では「一覧に戻る」が表示されない
- [x] 一括タグ編集「クリア」で選択アプリと対象タグの両方が解除される
- [x] 「クリア」後、「付与」「解除」がdisabledになる
- [x] Pinned Shortcut作成が従来どおり動作
- [x] generated icon付きショートカットが従来どおり動作
- [x] 検索・検索一発クリアが従来どおり動作
- [x] タグ作成・削除・リネームが従来どおり動作
- [x] 一括タグ付与/解除が従来どおり動作
- [x] リスト/アイコン表示切替が従来どおり動作
- [x] 上下インセット対応が従来どおり動作
- [x] アプリ起動が従来どおり動作

### 判断

通常のタグ絞り込みは1クリックで単一タグへ切り替わる自然な操作になった。
複数タグANDは「複数選択」ON時の明示操作として残した。
タグ別/タグなしショートカット起動時は、初期表示が検索と絞り込み済み一覧中心になり、Pixel Launcher補助ランチャーとして使いやすくなった。
「編集」から通常UIへ入り、「一覧に戻る」で簡素表示へ戻れるようになった。
一括タグ編集の「クリア」で付与対象タグも解除され、仕様が明確になった。
`一覧に戻る` が `showTagManagement=false` を保存する留意点はあるが、既定が閉であり現時点では許容とした。
必要なら後続で、表示ゲートのみの方式にしてプリファレンス副作用を避ける。

### 次候補

- 「一覧に戻る」の showTagManagement 副作用回避
- タグ色付きアイコン
- タグ並び替え
- dogfooding継続

---

## 2026-06-29 初期表示速度の計測結果 (アプリ内診断)

- **対象コミット**: `8ed3460 Add in-app performance diagnostics` / `6467f56 Add startup performance tracing`
- **対象端末**: Google Pixel 10a
- **配布/確認方法**: SafeDrop APK List 経由でインストール → アプリ内「診断」から `report()` をコピー (adb/logcat 不使用)
- **透明性**: 計測値は**利用者が Pixel 10a 上で取得した report()** に基づく。agent (Claude Code) 環境には Pixel 10a 実機も adb も無く、**agent 自身は実機計測を行っていない**。

### 計測結果

共通: app count raw=186 / loaded=186、tags count=17、app-tag rows=213。

| 区間 | 通常起動 | タグ別ショートカット (Tag tagId=14) | タグなしショートカット (Untagged) |
|---|---|---|---|
| onCreate→firstList | 1817ms | 2236ms | n/a (※下記) |
| PM query | 13ms | 15ms | 17ms |
| query→loaded(label+icon) | **1412ms** | **2047ms** | **1345ms** |
| loaded→uiState | 1ms | 1ms | 1ms |
| db sync | 258ms | 154ms | 62ms |
| applyLaunch→applied | - | 0ms | 0ms |
| applied→firstList | - | 2178ms | n/a |

- タグ別は filtered list count=16、タグなしは該当0件で first visible が記録されず n/a になった。

### 判断

- 初期表示遅延の主因は **PackageManager query ではなく、全アプリ (186件) の label/icon 同期ロード** (`query→loaded(label+icon)` が 1345〜2047ms と支配的)。
- PM query は 13〜17ms と高速。DB sync (62〜258ms) と タグ/app-tag Flow 初回受信は主因ではない (DB sync は表示と独立)。
- 絞り込み結果が 16件でも、初期表示前に 186件全ての label/icon を同期ロードしているため遅い。
- **次の速度改善本命は「アイコン遅延ロード」** (一覧は label 先行表示、icon は非同期/オンデマンド)。ただし本記録時点では未実装。

### 既知の診断ログ不足 (本コミットで小修正)

- タグなしショートカット (該当0件) で `first visible filtered list` が n/a になった。原因は filteredApps が空のとき first visible ログを出していなかったため。→ **0件でも記録**するよう修正 (apps 読込完了をトリガに変更)。
- `LaunchFilter.Untagged` が `...$Untagged@hash` と読みにくかった。→ `formatLaunchFilter` で `Untagged` / `Tag(tagId=14)` 表記に改善。

### 次アクション

- 診断ログ小修正の実機再確認 (タグなしで count=0 が出ること、表示が読みやすいこと)。
- その後、アイコン遅延ロードの最小実装を検討 (本命の B-2 速度改善)。

### 次候補

- アイコン遅延ロード (B-2 本命)
- 「一覧に戻る」の showTagManagement 副作用回避
- 診断UIの DEBUG ガード化
- dogfooding継続

---

## 2026-06-29 アイコン遅延ロード後の初期表示速度計測 (改善前後比較)

- **対象コミット**: `93f37b8 Load app icons asynchronously` (改善)
- **比較元**: `24a9972 Record performance diagnostics and improve trace output` (改善前)
- **対象端末**: Google Pixel 10a
- **配布/確認方法**: SafeDrop APK List 経由でインストール → アプリ内「診断」から `report()` をコピー (adb/logcat 不使用)
- **透明性**: 計測値は**利用者が Pixel 10a 上で取得した report()** に基づく。agent (Claude Code) 環境には Pixel 10a 実機も adb も無く、**agent 自身は実機計測を行っていない**。

### 改善前後の比較 (onCreate→firstList)

| 起動パターン | 改善前 (24a9972) | 改善後 (93f37b8) |
|---|---|---|
| 通常起動 | 1817ms | **1474ms** |
| タグ別ショートカット | 2225ms | **738ms** |
| タグなしショートカット | 1676ms | **408ms** |

- applied→firstList (ショートカット): タグ別 約1993〜2178ms → **529ms**、タグなし 1471ms → **188ms**。

### 改善後の主要区間

| 区間 | 通常起動 | タグ別ショートカット | タグなしショートカット |
|---|---|---|---|
| onCreate→firstList | 1474ms | 738ms | 408ms |
| PM query | 14ms | 17ms | 20ms |
| query→loaded(label only) | 1075ms | 395ms | 185ms |
| loaded→uiState | 1ms | 0ms | 0ms |
| db sync | 233ms | 186ms | 84ms |
| icon lazy load | n/a (※コピー時点で end 未記録) | 1699ms | 1026ms |
| applyLaunch→applied | - | 0ms | 1ms |
| applied→firstList | - | 529ms | 188ms |

- icon lazy load の結果: タグ別/タグなしとも `loaded=186 failed=0`。
- 通常起動の備考: icon lazy load start +1443ms、first visible (count=186) +1474ms、first icon +1482ms、icon lazy load end はコピー時点で未記録。

### 判断

- アイコン遅延ロードは、特に**ショートカット起動で明確に効果あり** (タグ別 2225→738ms、タグなし 1676→408ms)。`applied→firstList` も大幅短縮 (タグ別 ~2000→529ms、タグなし 1471→188ms)。
- 「一覧を先に出し、icon を後から埋める」設計は成立。icon lazy load は後段で動作し loaded=186 failed=0。
- 通常起動は 1817→1474ms と改善が小さめ。`query→loaded(label only)` が 1075ms 残り、**label 取得自体のコスト or 端末状態の揺れ**が残っている可能性。
- B-2 は、ショートカット起動導線については成功寄り。通常起動はまだ改善余地あり。
- 追加最適化候補 (今回は実装せず、まず dogfooding で体感確認): label キャッシュ / DB先行表示、表示対象優先の icon load、icon バッチ数調整。

### 次候補

- dogfooding で体感確認 (まず追加実装しない)
- 通常起動の label 取得コスト調査 (label キャッシュ / DB先行表示)
- 表示対象優先の icon load
- icon バッチ数調整

---

## 2026-06-29 ショートカット中継 Activity (ShortcutEntryActivity) 実機確認

- **対象コミット**: `406dfda Route pinned shortcuts through entry activity`
- **対象端末**: Google Pixel 10a
- **配布/確認方法**: 新ビルド APK をインストール → **ホーム上の既存ショートカットを削除し、本ビルドでタグ別/タグなしショートカットを作り直して**確認 → アプリ内「診断」`== launch/ui trace ==` を参照 (adb/logcat 不使用)
- **透明性**: 確認結果は**利用者が Pixel 10a 上で取得した test ok 報告**に基づく。agent (Claude Code) 環境には Pixel 10a 実機も adb も無く、**agent 自身は実機確認を行っていない**。

### 課題

- Pinned Shortcut が `MainActivity` を直接 `ACTION_VIEW` 起動していたため、ショートカット起動状態の MainActivity が**通常アプリと同じ task/affinity の root** になっていた。
- その状態でホームの通常アイコンをタップしても MAIN/LAUNCHER Intent が届かず、既存 Activity が **onResume だけで純resume** するケースがあった。
- このとき `normalLauncher` 判定も `restoreManualFilters()` も発火できず、Simplified + ショートカット由来フィルタが残ったまま通常モードへ戻れなかった。

### これまでの経緯

- `d2c3598` UI mode を None / Simplified / Editing に整理。
- `dcea6ec` `onNewIntent(filter=null)` による通常起動リセットを試みたが、通常アイコンタップが onNewIntent として届かないケースがあり不十分。
- `bb5b0d5` Launch/UI mode 診断 (`[LM]` / `== launch/ui trace ==`) を追加。
- `088328b` 初期 onCreate の MAIN/LAUNCHER を通常ランチャー起動として扱い reset したが、**通常モードの手動フィルタまで消してしまう**問題が発覚。
- `327b7f6` ショートカット由来フィルタを transient 化 (非永続) し、通常起動時は `clearFilterTags()` ではなく `restoreManualFilters()` へ変更。
- それでも**古いショートカット経由では通常アイコンタップが純resumeになり** `restoreManualFilters()` が発火しないケースが残った (本コミットの対象)。

### 406dfda の修正

- `ShortcutEntryActivity` (NoDisplay の薄い中継 Activity) を追加。
- Pinned Shortcut の起動先を `MainActivity` → `ShortcutEntryActivity` に変更。
- 中継は `taskAffinity="" / excludeFromRecents="true"` を指定し、通常ランチャー task と分離。
- 受け取った `FILTER_TAG_ID` / `SHOW_UNTAGGED_ONLY` を MainActivity へ `ACTION_VIEW` で転送 (NEW_TASK は付けず、中継 task 内へ MainActivity を載せる) し finish。
- これにより通常アイコンタップは別 affinity の通常 task を起こし、MainActivity が MAIN/LAUNCHER の onCreate を受けて `normalLauncher=true → restoreManualFilters` に入れる。
- `HOME` / `QUERY_ALL_PACKAGES` / `singleTask` / `launchMode` は追加していない。`onResume` 推定リセットも行っていない。
- MainActivity の `launchFilter` / `applyShortcut*` / `restoreManualFilters` の既存分岐は維持 (変更は pinned shortcut の起動先2箇所のみ)。

### 既存ショートカットの扱い

- 既存 Pinned Shortcut は**自動更新されない** (古い MainActivity 直起動のまま)。
- 実機確認時は**ホーム上の既存タグ別/タグなしショートカットを削除し、本ビルドで作り直す**必要があった。古いショートカットのままでは本修正の効果は出ない。

### Pixel 10a 実機確認 (利用者報告: test ok)

- 新しく作り直したショートカットで確認。
- ショートカット起動は **Simplified / transient** として動作 (対象タグ・タグなしで絞り込み、「編集」のみ)。
- **ショートカット起動 → ホームへ戻る → 通常アイコンタップで通常モードへ戻る**ことを確認 (純resume に張り付かない)。
- **通常モードの手動フィルタは維持**され、**ショートカット由来フィルタは通常アイコン起動へ持ち越されない**方針が成立。

### 判断

- 「通常/ショートカット起動と UIモード」「フィルタの由来別永続」の課題は**収束扱い**。
- `onResume` 推定リセットや `singleTask` への変更に進まずに、task 分離 (中継 Activity + `taskAffinity=""`) で解決できた。

### 残る注意

- `ShortcutEntryActivity` 追加により Activity が1つ増えた (exported)。受け取る extra は `FILTER_TAG_ID` / `SHOW_UNTAGGED_ONLY` に限定。
- 今後ショートカット用 extra を増やす場合は **ShortcutEntryActivity の転送処理も更新**が必要。
- 既存ショートカットは**削除・再作成が必要** (移行コードは無し)。
- ショートカット表示用 MainActivity が別 task として recents に残る可能性 (中継自体は excludeFromRecents)。
- Launch/UI 診断 (`[LM]`) は当面残してよいが、将来 **DEBUG 限定化 / 整理候補**。

### 次候補

- dogfooding で体感確認を継続 (収束した起動/フィルタ挙動の定着確認)。
- Launch/UI 診断の DEBUG ガード化 / 整理。
- (別軸) 通常起動の label 取得コスト調査・表示順改善・DB 系は、指示があれば別タスクで着手。

---

## 2026-06-30 Phase 1-A アプリ並び替え改善の受け入れ

- **対象コミット**:
  - `355cc74 Add app sort modes by launch history`
  - `56236ae Apply app sort mode to displayed list`
  - `16ec7f2 Fix launch history sorting state`
  - `0ccbdd8 Use UsageStats for app sort history`
  - `a4a3d2c Compact app sort controls`
  - `08dac37 Polish compact app list controls`
- **対象端末**: Google Pixel 10a
- **確認結果**: test ok
- **透明性**: 確認結果は利用者の Pixel 10a 実機確認報告に基づく。agent 環境では adb / 実機確認を行っていない。

### 経緯

- 当初は pixel-tag-drawer 内から起動した履歴 (`launchCount` / `lastLaunchedAt`) を使って「最近起動」「起動回数」を並び替える方針だった。
- しかし利用者期待は「どこから起動しても反映される最近起動順 / 起動回数順」だった。
- アプリ内起動履歴だけでは、他のランチャーやホーム画面から起動した利用実態が反映されず、実機上では「並び替えボタンを押しても効かない」ように見えた。
- このため、Phase 1-A の recent/count sort は Android 端末全体の使用履歴に基づく UsageStats 方式へ方針転換した。

### UsageStats 方式

- `PACKAGE_USAGE_STATS` を追加。
- `UsageStatsManager` で過去30日分の使用履歴を取得。
- 集計単位は `packageName`。
- `recent` は最新使用時刻で並び替える。
- `count` は foreground / resumed 相当イベント数で並び替える。
- 使用状況アクセスが未許可の場合は recent/count を利用不可にし、名前順へ fallback する。
- 使用状況アクセス設定への導線を UI に追加した。

### 制約

- 使用状況アクセス許可が必要。通常のランタイム権限ダイアログではなく、Android 設定で利用者が手動許可する必要がある。
- UsageStats は基本的に `packageName` 単位のため、同一 package 内の複数 launcher activity は同じ統計を共有する。
- `count` は OS イベント由来の foreground / resumed 相当回数であり、厳密な「アプリ起動回数」ではない。

### UI 改善

- sortMode 切替時に List / Grid を `scrollToItem(0)` で先頭へ戻すようにした。
- ソート UI を常時表示の FilterChip から `DropdownMenu` 方式へ変更した。
- 通常画面上部 UI を圧縮した。
- 件数 / 表示切替 / 並び替え / `⋯` を1行に整理した。
- 右端 `⋯` が潰れないよう `IconButton` + `40.dp` 固定サイズへ調整した。
- タグチップと検索欄は通常画面で常時使える状態を維持した。

### Pixel 10a 実機確認

- ソートが効くことを確認。
- UsageStats 方式により、pixel-tag-drawer 以外から起動したアプリも recent/count に反映されることを確認。
- sortMode 切替後に List / Grid が先頭へ戻ることを確認。
- 通常画面上部 UI の圧迫感が改善したことを確認。
- `⋯` メニュー表示が潰れず、操作できることを確認。

### 変更しなかったもの

- `QUERY_ALL_PACKAGES` 追加なし。
- HOME ランチャー宣言なし。
- DB schema / DB version 変更なし。
- タグ並び替え、D&D、任意順、更新順は未対応。

### 判断

Phase 1-A のアプリ並び替え改善は受け入れ。
当初の in-app launch history 方式は利用者期待とズレていたため、UsageStats 方式へ切り替えた判断は妥当。
recent/count sort、未許可時の fallback、設定導線、ソート後の先頭スクロール、通常画面上部 UI の圧縮まで含めて、Pixel 10a dogfooding の test ok を確認した。

### 次候補

- タグ並び替え
- D&D / 任意順
- 更新順
- UsageStats UI 文言の追加 polish
- dogfooding 継続

---

## タグ解除FB取り下げ・右端解除ボタン復元

### 経緯

タグ絞り込み中の一括解除ボタンを、タグチップ行 (`TagFilterSection`) の右端インライン表示から、
上部操作行の `×` アイコンボタンへ移動する変更が入っていた (`db60872` → `0f07340` で表示スタイルを調整)。

このFBはユーザーにより取り下げられた。

- 選択中タグの再タップによる単一タグ解除 (`toggleFilterTag`) はこのFB以前から既に存在し、変更されていない
- 一括解除ボタン (`clearFilterTags` 呼び出し) の解除対象はどの版でも「選択中タグすべて」のままで、
  「直近タグだけ解除」という新仕様には変わっていなかった
- 変更されていたのは表示位置とスタイルのみ (インラインTextButton → 上部行IconButton)
- ユーザーは元の配置 (タグチップ行右端) を希望したため、配置を復元した

### 復元内容

`MainActivity.kt`:

- 上部操作行の `×` IconButton (タグ絞り込み解除) を削除
- `TagFilterSection` に `onClear: () -> Unit` パラメータを復元し、呼び出し側で `tagViewModel::clearFilterTags` を再度渡す
- タグチップ `Row` の末尾に `TextButton("解除")` を復元 (選択中タグまたは「タグなし」絞り込みが効いている時のみ表示)

### 変更しなかったもの

- `toggleFilterTag` (単一タグの再タップ解除) の挙動
- `clearFilterTags` (一括解除) のロジック自体
- 複数選択モード、検索、並び順、最近順、アイコン/リスト切替
- `⋯` メニューの `TopActionOverflowDots` 描画方式

### ビルド確認

- `compileDebugKotlin` 成功
- `assembleDebug` 成功

### 実機確認

- 未実施 (今回はビルド確認のみ)

### 判断

- 直前FB (上部行への移動) は取り下げ、タグチップ行右端の一括解除ボタンに復元した。
- 実機確認は次回。

---

## 2026-07-05 ソート安定化・アイコン読込順・タグナビ配置 accepted (2026-07-07)

### 経緯

上記「タグ解除FB取り下げ・右端解除ボタン復元」以降、同日中に以下の関連コミットが続けて入ったが、
これまでPROGRESS.mdに記録されていなかった。今回はコード変更を行わず、これらを1つのまとまりとして
記録し、実機確認の準備をする。

### 対象commit

- `f0b63d4` Reset app list scroll on sort change
- `af066e7` Avoid resorting app list during icon load
- `6c51af9` Delay recent sort display until usage stats load
- `b21b85b` Rename diagnostics refresh button
- `1ae7267` Skip duplicate usage refresh at startup
- `9d8f084` Load icons in current sort order
- `49e3bea` Move tag navigation buttons to left of search box

### 変更内容 (commit別)

- **f0b63d4 Reset app list scroll on sort change**
  - ソートモード変更時、表示中の List/Grid を先頭へ戻す処理を、表示側 state を先に確定させる順序に調整。
  - Recent/Count では、並び (`appOrderKey`) が変化した時に加え、初回composeでも先頭へ貼り直す処理を追加
    (Activity再生成時に`rememberLazyListState`が復元したスクロール位置へ戻ってしまう対策)。
  - 名前順ではこの貼り直しを行わない。手動スクロール中は割り込まない。
  - デバッグ用の`PerfLog`出力を追加。

- **af066e7 Avoid resorting app list during icon load**
  - アイコンの後追い読み込みのたびに`sortedApps`のインスタンスが変わり不要な再ソートが走っていたのを抑制。
  - ソートに影響する入力 (mode / 対象集合 / label / usage値) の署名が前回と同じ場合は、
    アイコン差し替え済みの新インスタンスを既存順のまま並べ直すだけにする。
  - 署名不一致 (対象集合が食い違う等) の場合は安全側で通常の再ソートにフォールバック。

- **6c51af9 Delay recent sort display until usage stats load**
  - Recent/Count起動時、usage stats反映前の実質名前順を一瞬表示してから並び替わる二段階表示を避けるため、
    `initialSortSettling`状態を追加。
  - settling中は「アプリ一覧を読み込んでいます...」を表示し、一覧描画を保留。
  - usage merge完了 / 名前順へのフォールバック / タイムアウト (`SETTLING_TIMEOUT_MS`) のいずれかで解除。

- **b21b85b Rename diagnostics refresh button**
  - 診断ダイアログの「更新」ボタンを「ログ再読込」に変更 (押下対象・挙動は変更なし、ラベルのみ)。

- **1ae7267 Skip duplicate usage refresh at startup**
  - 冷間起動時、`ON_RESUME`由来の`refreshUsageStats()`と本体側の usage merge が二重に走るケースを抑制。
  - `usageMergeInProgress` (実行中フラグ) と直近merge完了時刻・反映件数を保持し、
    「先に始まった方を優先」「直近2秒以内かつ反映件数>0なら重複スキップ」のルールでどちらか一方のみ実行。
  - 実際に一覧が見え始めた最初のタイミングを1回だけ記録する計測ログも追加。

- **9d8f084 Load icons in current sort order**
  - アイコンの後追い読み込み順を、読み込み開始時点の元リスト順ではなく現在の表示ソート順
    (Recent/Countはusage反映後の順) に変更。
  - Recent/Countではusage mergeの完了を`ICON_ORDER_WAIT_TIMEOUT_MS` (500ms) まで待ってから読み込み順を確定。
  - 名前順は待たずに読み込み開始。読み込み順のみに影響し、UIの並び順・batch flush動作は変えない。

- **49e3bea Move tag navigation buttons to left of search box**
  - 単一タグ選択中に表示される前/次タグの◀▶ナビゲーションボタンを、検索ボックスの右側から左側へ移動。
  - 検索ボックス自体の見た目・クリアボタン・レイアウト幅の扱いは変更なし。

### 変更しなかったもの

- DB schema / DB version
- タグ付与・複数選択モード・検索ロジック本体
- `QUERY_ALL_PACKAGES` 追加なし、HOMEランチャー宣言なし
- 直前に復元したタグ絞り込み一括解除ボタン (`TagFilterSection`右端の「解除」) のロジック・配置

### ビルド確認

- `./gradlew compileDebugKotlin` 成功 (SDK XMLバージョンに関する警告のみ、実装への影響なし)

### 実機確認チェックリスト (Pixel 10a / 確認済み)

- [x] タグ絞り込み時、右端の解除ボタンが期待どおり使える
- [x] タグナビゲーションボタンが検索ボックス左側にあり、操作しやすい
- [x] ソート変更時にリストが先頭へ戻る
- [x] recent sort / count sort の表示が UsageStats 読み込み前に誤表示されない
- [x] アイコン読み込みで並び順が途中で乱れない
- [x] 起動直後に usage refresh が重複して走っていない
- [x] 診断/更新ボタンのラベルが意図どおり分かりやすい
- [x] compileDebugKotlin が成功している (確認済み、上記参照)

### 判断

- accepted (2026-07-07)。Pixel 10a 実機確認チェックリストが全て OK となったため受け入れ済みとする。

---

## 2026-07-11 「おすすめ」ソート (第1段階) 追加 — 未受容・実機確認待ち

### 目的

既存の最近順・回数順は「過去によく使ったアプリ」を上位にするだけで、
「ユーザーがこれから起動したい可能性が高いアプリ」を近似できていなかった
(起動した覚えがないアプリの上位表示、Calendar等バックグラウンド処理の影響、
常用アプリの上位固定、たまたま一度開いたアプリの過大評価)。
第1段階として、UsageEventsから妥当な利用セッションを生成し、
直近性 (recency) と短期間の反復利用 (frequency) だけで順位を計算する
「おすすめ」ソートを追加した。時間帯バケット・USER_INTERACTION信頼度・
タグ加点・機械学習等は今回のスコープ外 (下記「除外した機能」参照)。

### 採用したセッション定義

UsageEventsのフォアグラウンド遷移イベントを、そのまま起動回数として数えず、
packageName単位の利用セッションへ変換した。

- Activityクラス単位で前景状態を管理し (`Set<className>`)、package内に前景Activityが
  1つ以上存在する期間を1つの候補セッションとして扱う。同一package内のActivity遷移
  (画面遷移) だけでは複数起動として数えない。
- 実機のSDK定数を確認した結果、`ACTIVITY_RESUMED` は `MOVE_TO_FOREGROUND` と、
  `ACTIVITY_PAUSED` は `MOVE_TO_BACKGROUND` と同一の整数値のエイリアスであり、
  別イベントとして二重に届くことはないと判明した (当初の設計は別値である前提だったため、
  実装中に発見し設計を修正した)。API29+限定の追加終了シグナルである `ACTIVITY_STOPPED` のみ
  APIレベルで含めるかどうかを切り替える。`ACTIVITY_PAUSED` の後に `ACTIVITY_STOPPED` が
  重ねて届いても、前景集合からの除去がidempotentなため二重計上しない。
- 対応しないEND (前景集合にないclassNameのEND) は無視し、重複START (既に前景集合にある
  classNameのSTART) も無視する。クラッシュ・負数状態にはならない。
- 観測期間終端で前景Activityが残っている場合は、そこでセッションを閉じる (windowEndで打ち切り)。
- 近接セッションの統合 (MERGE_GAP_MS以内) を先に行い、そのあとで短時間セッションの除外
  (MIN_SESSION_MS未満) を行う。画面回転等による瞬間的な前景断絶を1つの利用として救いつつ、
  統合後もなお短いセッションだけを除外する。

### 定数

- OBSERVATION_DAYS = 7日 (`AppRepository.RECOMMENDED_OBSERVATION_DAYS`)。
  既存の最近順・回数順が使う `USAGE_STATS_DAYS = 30` とは独立しており、そちらの挙動は変えていない。
  queryEventsが実際に返した範囲のイベントだけを使う (7日分保持されている前提は置かない)。
- MIN_SESSION_MS = 2,000ms (2秒未満は除外、2秒ちょうどは採用)。
- MERGE_GAP_MS = 30,000ms (30秒以内の再開を統合、境界値30秒ちょうども統合対象)。

### スコア式

```
recency(a)   = 2 ^ (-age(a) / 24時間)          (sessionCount=0なら0、ageが負なら0として扱う)
frequency(a) = ln(1 + count(a)) / ln(1 + 8)     (最大1.0にclamp、表示対象集合では正規化しない)
score(a)     = 0.65 * recency(a) + 0.35 * frequency(a)
```

同点処理は「スコア降順 → 最終セッション時刻降順 → アプリ名昇順 → packageName昇順 →
className昇順」の完全決定的な比較。履歴なしアプリ (採用セッション0件) は履歴があるアプリの
後方に配置され、履歴なしアプリ同士は名前順 (新規インストール補正なし)。
UsageStats権限がない場合は既存の最近順・回数順と同じ規則で名前順へフォールバックする
(権限付与・解除後の再集計経路は変更していない)。

### 今回除外した機能 (第1段階のスコープ外)

平日/休日区別、時間帯バケット、利用間隔の中央値/周期性、USER_INTERACTIONによるconfidence、
pixel-tag-drawer内launchCount/lastLaunchedAtとの統合、新規インストール補正、
totalTimeInForegroundによる加点、アプリ固有の除外リスト、DBスキーマ変更、常駐監視、
タグによるスコア加点、手動ピン留め、機械学習。

### 実装範囲

新規: `data/UsageSession.kt` (セッション生成、Android非依存の純粋関数)、
`ui/RecommendedScore.kt` (スコア計算、純粋関数)。
変更: `data/AppRepository.kt` (`loadRecommendedUsage()`)、`model/LauncherApp.kt`
(`recommendedSessionCount`/`recommendedLastSessionAt`追加、DBスキーマ変更なし)、
`ui/AppSortMode.kt` (`Recommended`モード追加)、`ui/AppListViewModel.kt`
(usage mergeと同じタイミングでrecommended統計も取得、上位10件の診断ログ)、
`MainActivity.kt` (ソートメニューに「おすすめ」追加、SortOrderCache署名にrecommended値を追加)。

### 単体テスト結果

今回が本プロジェクト初のユニットテスト導入 (`app/src/test`、JUnit4追加、Robolectric不使用)。
セッション生成17件・スコア計算12件・ソート順/同点処理7件の計36件、全て成功。

```
UsageSessionTest:            17 tests, 0 failed
RecommendedScoreTest:        12 tests, 0 failed
AppSortModeRecommendedTest:   7 tests, 0 failed
```

### ビルド結果

- `./gradlew testDebugUnitTest` : BUILD SUCCESSFUL (36 tests, 0 failed)
- `./gradlew assembleDebug` : BUILD SUCCESSFUL
- `git diff --check` : 該当なし (CRLF/LF警告のみ、コミット対象の空白エラーなし)
- `./gradlew lintDebug` : 既定のJVMヒープでは `Metaspace` 不足で失敗した
  (このリポジトリの `org.gradle.jvmargs` がlint workerには不足気味という環境要因、
  今回の変更が原因ではない)。ヒープを一時的に引き上げて再実行したところ完走し、
  1 error / 51 warnings / 1 hint。唯一のerrorは `MainActivity.kt:310` の
  `dynamicLightColorScheme` (API31必須、NewApi) で、今回のdiff範囲外の既存コード。
  今回追加・変更したファイル (UsageSession.kt, RecommendedScore.kt, AppSortMode.kt,
  AppListViewModel.kt, LauncherApp.kt) に新規lint指摘は無し。`AppRepository.kt`の
  warning 1件 (`UseKtx`) も既存コード (`loadIconBitmap`) が行番号シフトで再掲されたもの。

### 実機確認待ち

未実施。次回、Pixel 10aで以下を重点確認する。

- **Calendarなどの誤検出が改善するか** (最重要): 「おすすめ」上位に開いた覚えのないアプリが
  来ないか。診断ダイアログの `[RECO]` ログで上位10件のscore/sessionCount/lastSessionTime/
  recency/frequencyを確認する。
- 通常の1日を通して朝/日中/夜でトップ数件が体感と一致するか。
- 常用アプリ (LINE/ブラウザ等) が常に上位固定にならないか。
- 新規インストールアプリが下部 (名前順) に収まり、数日後に順位が上がってくるか。
- UsageStats権限剥奪時に名前順へ正しくフォールバックするか (既存経路の回帰確認)。
- 名前順・最近順・回数順・タグANDフィルタ・タグなしフィルタ・検索・List/Grid切替・
  タグ編集・アプリ起動・ソートモードの保存復元・アイコン遅延ロード時の並び順安定化に
  回帰がないか。

### 既知の制約

- 重み (0.65/0.35)、半減期 (24時間)、飽和セッション数 (8)、MIN_SESSION_MS、MERGE_GAP_MS、
  OBSERVATION_DAYSはいずれも実測データに基づかない初期仮定値であり、実機dogfoodingでの
  調整対象。
- `loadRecommendedUsage()` は既存のusage merge (最近順・回数順用、30日) と同じタイミングで
  常に取得する設計とした (どのソートモードでもモード切替が即座に効く既存体験を維持するため)。
  そのぶんmerge処理あたりのUsageStatsManager呼び出しが1回増える。実機での初期表示速度への
  影響は未計測。

---

## 2026-07-11 「おすすめ」ソート 初回実機診断ログと調整 — 未受容・実機確認待ち

### 初回実機評価の結果 (調整前)

- 起動性能: `onCreate→firstList` 1307ms で問題なし (前回計測のベースラインと同水準)。
- **Calendarは上位10件に現れず、誤検出は改善傾向**だった (継続時間フィルタ・セッション統合の
  意図どおりの効果と見られる)。
- 一方で2つの問題を確認した。
  1. **自己パッケージ (Pixel Tag Drawer自身) が必然的に上位 (1位) になる**。Drawerを開くたびに
     直近利用として自己観測されるため、「次に起動したいアプリ」の予測信号にならない。
  2. **飽和回数8では上位アプリのfrequencyが軒並み1.0**になり、頻度項が上位内の順位差を
     作れていなかった (実機ログ例: Pixel Tag Drawer sessions=138, Play ストア sessions=48,
     Chrome sessions=151, Musicolet sessions=78, WolLight sessions=37, Link sessions=10,
     マップ sessions=8, Feedly sessions=20, Gmail sessions=79, Google sessions=70 が
     いずれも frequency=1.0)。上位が実質的に最近順に潰れていた。

### 対応した修正 (今回)

- **自己パッケージ除外**: 「おすすめ」ソートのみ、Pixel Tag Drawer自身
  (`BuildConfig.APPLICATION_ID`、ハードコード文字列ではない) を履歴なし相当としてランキング後方へ
  配置するよう `sortApps()` を修正した (`ui/AppSortMode.kt`)。アプリ一覧そのものからは削除せず、
  モデル上の `recommendedSessionCount`/`recommendedLastSessionAt` も保持したまま。
  名前順・最近順・回数順・診断ログの上位10件抽出も同じ `sortApps()` を経由するため、
  自己パッケージ除外判定はこの1箇所にのみ実装している。
- **頻度飽和回数を8→64へ変更**: `RECOMMENDED_FREQUENCY_SATURATION_COUNT` のみ変更し、
  score式・各重み・半減期・OBSERVATION_DAYS・MIN_SESSION_MS・MERGE_GAP_MS・セッション生成方法は
  変更していない。

### 修正後の位置づけ

自己パッケージ除外と64回飽和への修正は反映したが、**実機での再評価はまだ行っていない**。
Pixel Tag Drawer自身がtop10から外れること、上位アプリのfrequencyに差が出ること
(diagnostics `[RECO]` ログで確認可能) を次回のPixel 10a dogfoodingで確認するまで、
本節も含めて accepted とはしない。

---

## 2026-07-11 「おすすめ」表示時の上部操作行レイアウト崩れ修正 — 未受容・実機確認待ち

### 実機確認で見つかった不具合

Pixel 10aのアイコン表示で、ソートを「おすすめ」にすると、操作行の横幅が不足し、
右端の編集用「…」ボタンが画面外へ見切れた。「最近」「回数」より「おすすめ」の表示幅が
広く、行全体が画面幅を超えていたことが原因。

### 修正内容

`MainActivity.kt` の該当Row (件数表示・List/Aicon切替・ソート選択・編集用「…」を
横一列に並べる箇所) を、右端の「…」を常に固定サイズで確保する構造へ変更した。

- 編集用「…」の `Box` を、リスト/アイコン切替とソート選択を包んでいた内側の重み付き
  `Row` の外へ出し、外側の `Row` の直接の最後の子要素にした (固定サイズのまま、
  重み付きの兄弟が縮んでも押し出されない)。
- ソート選択の `Box` に `Modifier.weight(1f)` を追加し、残り幅を使うようにした
  (件数表示・FilterChip 2つ・編集用「…」は従来どおり固定幅)。
- ソートラベルの `Text` に `maxLines = 1` / `overflow = TextOverflow.Ellipsis`
  を追加した (安全策。「名前順」「最近」「回数」「おすすめ」はいずれも短く、
  通常幅では発動しない想定)。
- 個々の要素へ固定幅を追加する場当たり的対応ではなく、Row全体のスクロール化でもなく、
  「おすすめ」だけの文字短縮でもない。

### 変更しなかったもの

おすすめ順の集計・スコア、ソートモードの保存・復元、件数表示、List/Grid切替機能、
編集メニューの内容、タグ表示行、検索欄、List/Gridのアプリ表示、通常モード復元、
UsageStats関連処理。

### テスト・ビルド結果

- `./gradlew testDebugUnitTest`: BUILD SUCCESSFUL (既存46件、レイアウトのみの変更のため
  おすすめスコア関連テストは無変更)
- `./gradlew assembleDebug`: BUILD SUCCESSFUL
- `git diff --check`: 問題なし

### 実機確認待ち

agent環境にはPixel 10a実機もadbも無く、レイアウト崩れの修正自体はコンパイル成功と
Composeのweight配置ロジックの検討のみに基づく (実機描画未確認)。次回、以下を確認する。

- 「名前」「最近」「回数」「おすすめ」いずれでも右端の「…」が完全表示され、タップで
  既存の編集メニューが開くこと
- 件数表示・List/Aicon切替が見切れないこと
- Pixel 10a相当の画面幅で横方向のオーバーフローがないこと
- 文字サイズを大きくした場合の右端操作の維持
- List表示・Grid表示の双方で問題がないこと

おすすめ順自体 (集計・スコア・自己パッケージ除外・飽和回数64) は引き続き実機評価中であり、
本節・前節ともまだ accepted にはしない。

---

## 2026-07-27 おすすめ順・sort操作行・タグ管理・Usage Access の実機受入

- **対象commit**:
  - `fc9114f Add recommended app sorting`
  - `9b94997 Tune recommended app frequency`
  - `32e1971 Fix sort controls overflow`
  - `e90d179 Keep tag management controls reachable`
- **確認方法**: 利用者による実機確認
- **結果**: 短時間の未確認実機FB 4件は全て確認完了

### 実機確認結果

#### 1. おすすめ順の調整後ランキング — ACCEPTED

- おすすめ順の実機表示は実用上問題なし。
- Pixel Tag Drawer自身が不自然に上位へ来る問題は確認されなかった。
- `9b94997` の自己パッケージ除外と頻度飽和回数8→64の調整を受け入れ。
- HOMEへ移動して通常アイコンから戻った後も「おすすめ」sortが維持された。
- `fc9114f` / `9b94997` の短時間実機確認は完了扱いとする。

#### 2. sort操作行の見切れ修正 — ACCEPTED FOR NOW

- 右端の編集用「…」は欠けずに表示され、正常に押下できた。
- List / Grid切替とsort操作に実用上の問題は確認されなかった。
- 「おすすめ」が「おす...」と省略表示される点は残るが、操作性は確保されているため
  現時点では一旦受け入れ、追加修正は行わない。
- `32e1971` は完全な表示改善と断定せず、現状許容の `ACCEPTED FOR NOW` とする。

#### 3. タグ管理パネルと終了操作の到達性 — ACCEPTED

- タグ管理パネルは実用上問題なく表示された。
- タグ一覧を最後までスクロールでき、パネルが潰れる問題は確認されなかった。
- 「タグ管理を終了」を押下でき、終了操作へ到達できた。
- `e90d179` のタグ管理パネル高さ制御、内部スクロール、固定終了操作を受け入れ。

#### 4. Usage AccessのOFF / ON切替 — ACCEPTED

- Usage AccessをOFFにした後、名前順へのfallbackが正常に動作した。
- OFF時は最近起動・起動回数・おすすめのUsageStats系sortが選択不可になった。
- Usage AccessをONへ戻した後、UsageStats系sortを再度選択できた。
- staleなおすすめ順、クラッシュ、再許可後の選択不能は確認されなかった。
- 再許可後に「おすすめ」へ自動復帰せず、名前順を維持する現行仕様も受け入れ。
- Recommended追加後のUsage Access回帰確認は完了扱いとする。

### 長期dogfooding継続

おすすめ順の短時間実機確認は受入済みとし、以下だけを日常利用で継続確認する。

- 朝・日中・夜の利用状況に対して、おすすめ上位が自然か。
- Calendar等の明示的に利用していないアプリが不自然に上位へ戻らないか。
- 常用アプリだけで順位が固定されすぎないか。

これらは現時点の未完了不具合や公開BLOCKERとは扱わない。新しい具体的な操作上の不便が
発生した場合は、再現条件と実使用への影響を記録したうえで、完了・修正・保留を改めて判断する。

### 判断

- おすすめ順の調整後ランキング: `ACCEPTED`。
- sort操作行の見切れ修正: `ACCEPTED FOR NOW`。
- タグ管理パネルと終了操作の到達性: `ACCEPTED`。
- Recommended追加後のUsage Access OFF / ON回帰確認: `ACCEPTED`。
- 2026-07-27時点で、短時間の未確認実機FB 4件は全て完了。
- 現時点で直ちに対応すべき明確な実機FBはなく、長期dogfooding継続・新規FB待ちとする。

---

## 2026-08-21 Export/Import バックアップ機能とorphan app_tags修正の実機確認

- **対象コミット**: `Add backup restore and repair tag assignments`
- **対象端末**: Google Pixel 10a
- **配布方法**: debug APK 更新インストール
- **対象機能**:
  - versioned JSON backup (Export / Import)
  - Room 3テーブル (tags / launcher_apps / app_tags) + preferences 7項目の書き出し/復元
  - SAF (CreateDocument / OpenDocument) 経由のファイル選択
  - Import前の置き換え確認ダイアログ
  - validation失敗時は非破壊 (DB/prefs変更なし)
  - タグ削除時の app_tags cleanup (orphan行の再発防止)
  - DB v3→v4、MIGRATION_3_4 による既存orphan app_tagsの一括削除
- **結果**: test ok

### 確認項目

- [x] debug APK 上書きインストール成功
- [x] DB migration 3→4 成功
- [x] 既存データ (タグ・アプリ・割り当て) 保持
- [x] Export/Import メニューUIに問題なし
- [x] 新規バックアップの書き出し成功
- [x] 書き出したバックアップの再取得値: tags 17 / launcherApps 207 / appTags 247 / orphan 0
- [x] バックアップが RESTORE_READY 状態であることを確認

### 判断

Export/Import によるバックアップ復元機能を実機で受け入れた。
旧 `TagRepository.deleteTag` がタグ削除時に対応する app_tags を消していなかった問題を修正し、
MIGRATION_3_4 で既存インストールのorphan行も一括削除できることを実機で確認した。
release signing への移行は次段階として別途扱う。debug→release実機切替はまだ未実施。

### 次候補

- release-signed APK のビルドと検証 (別段階)
- release版へのimport実運用確認 (Human承認後)
- dogfooding継続

---

## 2026-08-21 Pixel 10a debug署名版 → release署名版 実移行の実機確認

- **対象コミット**: `9fa313f Add backup restore and repair tag assignments`
- **対象端末**: Google Pixel 10a (dailyuse機)
- **配布方法**: SafeDrop 経由で release署名APK (`pixel-tag-drawer-v0.1.0-android.apk`) を配布
- **対象**: debug署名版のアンインストール → release署名版インストール → Export/Importバックアップによるデータ移行
- **結果**: test all ok

### 確認項目

- [x] debug署名版アンインストール成功
- [x] release署名版インストール成功
- [x] 初回起動成功 (fresh installとしてtagsが空の状態を確認)
- [x] バックアップ復元 (`pixel-tag-drawer-backup-2026-08-21 (1).json`) 成功
- [x] 復元後の再起動で状態反映を確認
- [x] タグ一覧の正常復元
- [x] アプリへのタグ割り当ての正常復元
- [x] preferences (表示モード・フィルタ・sort設定等) の正常復元
- [x] Usage Access の手動再許可成功
- [x] 再許可後、最近使用・起動回数・おすすめ sort が正常動作
- [x] その他実使用上の問題なし

### 判断

release署名版への実移行を実機で完了・受け入れた。
旧backup (`pixel-tag-drawer-backup-2026-08-21.json`、orphan 1件を含みinvalid) は使用せず、
MIGRATION_3_4適用後に再書き出しした新backup (`tags 17 / launcherApps 207 / appTags 247 / orphan 0`) で移行した。
これにより、Pixel 10aのdaily-use環境は正式なrelease署名鍵で運用される状態になった。
rollback (debug版再インストール) は不要だった。
GitHub公開 (push / tag / GitHub Release) はこの記録時点でまだ未実施。

### 次候補

- GitHub公開 (push / tag / GitHub pre-release) — Human承認後
- pinned shortcutの必要に応じた再作成
- dogfooding継続
