# TODO

## 全体コードレビュー(2026-09-15、2回目)の対応結果

差分ではなくコードベース全体を対象にレビューを実施し、指摘事項をすべて対応済み(コミット `79a67ed` ほか)。
対応内容の詳細は`進捗ログ.md`および当該コミットメッセージを参照。

**対応済み**
- Service層(`PlayerService`/`ClanService`)と`domain`パッケージを新設し、Controllerから業務ロジックを分離
- ViewModel(`web/view`)+`ViewMapper`導入により、テンプレートから`T(...)`によるstaticメソッド呼び出し(7箇所)と勝敗判定を排除
- ラベル3クラス(`CardNameLabels`/`GameModeLabels`/`RoleLabels`)を廃止し`messages*.properties`に集約。固定文言含め全面多言語化(`?lang=ja`/`?lang=en`)
- URLをパス形式(`/player/{tag}`、`/clan/{tag}`)へ変更。検索は`/player/search?q=`、`/clan/search?q=`に分離
- 対戦詳細をindex参照からbattleTime参照に変更(黙って別の対戦が表示される問題を解消)
- 得意カード/苦手カードの重複表示を修正。Wilson score intervalで試行回数を加味した順位付けに変更
- 2v2で相手2人目のデッキが集計から漏れていた問題を修正
- client層で独自例外に変換し、`@ControllerAdvice`でエラー画面を集約(旧TODO 3・5・6が同時に解決)
- Spring Boot 3.3.4 → 4.1.1(3.3系・3.5系ともOSSサポート終了のため)
- APIキーをjarから排除(ローカルは`./config/`、本番は環境変数`CLASHROYALE_API_TOKEN`)
- `@NotBlank`によるfail-fast、デフォルトprofileから`local`を除去
- `RestClient.Builder`のDIとタイムアウト設定(connect 3s / read 8s)
- Caffeineによる2分TTLキャッシュ(レート制限・低スペックVM対策。実測で11倍高速化)
- テンプレートのフラグメント化、`lang`属性、フォームの`required`、ダークモード対応、`deck-grid`のauto-fit化
- テストを純粋ロジック+Mockitoの範囲で再構成(36件。MockMvc/MockRestServiceServerは引き続き不使用)

**今回対応しなかったもの(継続TODO)**
- 可観測性(Actuator未導入、ヘルスチェック・死活監視なし)。2026-09-15にVM無応答が2回発生しており優先度は高い
- ログのローテーション設定
- JVMのメモリ上限指定(`-Xmx`等)。VMフリーズ対策として検討の余地あり(今回はOOMの痕跡が無かったため見送り)
- HTTPS未対応(下記「ページビュー向上に関するTODO」の1番で対応)

## ページビュー向上に関するTODO(2026-09-14、別セッション経由でユーザーから記録依頼)

着手時期は未定。土台整備を先に済ませてから集客施策に進む想定。

**土台として先に整えるべきもの**
1. 独自ドメイン取得 + HTTPS化(SNS等でリンク共有しても怪しく見えないようにするため。旧「ドメイン取得・HTTPS化」項目と統合)
2. OGP/メタタグ設定(X/Discordでリンクをシェアしたときにタイトル・説明・画像が表示されるように)
3. 低スペックVM(Always Free枠)でのアクセス急増に対する耐性確認(過去にVMフリーズが発生した実績あり。[[project-oci-vm-spec]]参照)

**集客・再訪施策**
4. 差別化ポイント(日本語での戦績・カード名表示)を訴求する形にサイト説明文・タイトルを調整
5. X(旧Twitter)・Discordなど日本語クラロワコミュニティへの直接告知(検索流入だけに頼らない)
6. 再訪動機になるコンテンツ(クイズ機能など、既存の「フェーズ2」構想と紐付け)
7. アクセス解析の導入(施策効果測定のため、本格的な集客施策より先に仕込んでおくと良い)
