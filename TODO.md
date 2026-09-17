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

## 静的リソース(CSS/JS)のキャッシュ対策(2026-09-16に判明、未着手)

**問題**: CSSを変更してデプロイしても、ブラウザが古いCSSをキャッシュから使い続けて変更が反映されない。2026-09-16の対戦詳細画面のタワーユニット表示変更で実際に発生(詳細と実測値は`進捗ログ.md`参照)。Spring Bootの静的リソース配信がデフォルトで`Cache-Control`も`ETag`も返さないため、ブラウザがヒューリスティックキャッシュを適用するのが原因。CSS/JSを変更するたびに毎回起きる構造的な問題。

**対応案(ユーザー未決定)**
1. **コンテンツハッシュ付きURL(推奨)**: `spring.web.resources.chain.strategy.content.enabled=true`を設定する。テンプレート側は`fragments/layout.html`ですでに`th:href="@{/css/style.css}"`(Thymeleaf経由)になっているため、設定の追加だけで効く。中身が変わるとURL自体が変わるためキャッシュ事故が原理的に起きない。
2. **常に再検証**: `spring.web.resources.cache.cachecontrol.no-cache=true`。設定1行で済むが毎回サーバーへ問い合わせが発生する(低スペックVMには不利)。
3. 手動でクエリ`?v=2`を付与。更新忘れが起きるため非推奨。

**暫定回避**: 表示が変わらない場合は`Ctrl + Shift + R`(スーパーリロード)で確認する。デプロイ後の動作確認時も同様。

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

## 画面デザインの改善候補(2026-09-16に洗い出し、未着手)

ユーザーの依頼で全画面のテンプレート・CSSを読んで洗い出した候補のうち、このとき着手しなかったもの(着手したのは対戦詳細の2カラム化とモバイル対応の2件。詳細は`進捗ログ.md`のフェーズ1.8)。実際の描画ではなくコードから読み取った指摘のため、着手時に現物を再確認すること。

**アクセシビリティ・コントラスト**
- **ダークモードのヘッダーが読みにくい**(実害あり)。`.site-header`のグラデーションがダーク時に`#7b95ff → #a8bcff`と明るくなり、白文字とのコントラスト比が約2.6:1しかない(WCAG AAの大きめ文字基準3:1も下回る)。言語切替リンクは`opacity: 0.7`のためさらに悪化する。ダーク時だけヘッダー背景を暗く保つ対応が要る。
- 装飾目的の絵文字(`player.html`・`clan.html`のstat-grid内の⭐🏆🥇✅❌👑🛡️👥)に`aria-hidden="true"`が無く、スクリーンリーダーが「星 経験値レベル」のように読み上げてしまう。
- フォーカスリングが`.search-form input`にしか定義されていない。ボタン・リンク・ソート用ヘッダも`:focus-visible`で共通化したい。

**情報設計・表示**
- `player.html`の得意/苦手カードが`deckGrid`フラグメントを使わず同じHTMLを手書きしており、`card-level`クラスに勝率を入れている(クラス名と中身の不一致)。勝率も数値の羅列で強弱が伝わらないため、色分けかバー表示の検討余地あり。
- カード画像に`width`/`height`属性が無く、読み込み時にレイアウトがずれる(CLS)。`aspect-ratio`か属性指定で回避できる。
- 「検索 → プレイヤー → 対戦詳細」と3階層あるがパンくずが無く、`back-link`で1階層戻れるだけ。
- 空状態(`.empty-state`)が文字色を薄くするだけで素っ気ない。

**モバイル対応で残った課題**
- 表をカード化する際に`display: block`を使っているため、表としてのセマンティクスが失われる(スクリーンリーダーが表として読まない)。厳密に対応するなら`role="table"`等の明示が必要。
- クランメンバー一覧はモバイルで1人あたり5行になるため、50人だと縦に非常に長い。列の絞り込みや2列化の検討余地あり(480px前後の実機では2列化しても幅が足りないため、今回は見送った)。

**その他**
- ダークモードの`--color-primary-dark`が実際には明るい色で、変数名と実体がずれている(バグではないが今後触るときの誤解の元)。

## 多言語化(i18n)の改善候補(2026-09-17に洗い出し、未着手)

ユーザーの依頼で洗い出した候補。コードの読み取りに加えて、次の方法で裏付けを取った(数値はこの日時点のもの)。

- 公式APIの実データ: `/cards`、日本上位クランのメンバーなど9人分の対戦履歴(計270戦)
- ローカル起動での実測: コミット`ce63eaf`の状態をポート18080で起動し、curlで確認

### 実害が大きいもの(実データ・実測で確認済み)

**1. ゲームモード名の訳が実データの65%で欠けており、内部IDがそのまま表示される**
- 270戦の内訳:

  | gameMode.name | type | 件数 | 訳 |
  |---|---|---|---|
  | `Ladder` | `trail` | 95 | あり |
  | `Ranked1v1_NewArena2` | `pathOfLegend` | 79 | なし |
  | `Challenge_AllCards_EventDeck_NoSet` | `trail` | 50 | なし |
  | `TeamVsTeam` | `trail` | 30 | なし |
  | `PickMode` | `trail` | 16 | なし |

- `gameMode.name`はイベントやアリーナごとに接尾辞付きのIDが増えるため(`Ranked1v1_NewArena2`など)、完全一致の辞書では追いつかない。`type`の方が分類として安定しているので、「`type`で大分類を表示し、既知のIDだけ詳しい名前にする」二段構えを検討する。
- 英語側(`messages.properties`)にはゲームモードの定義が1件も無い。そのため英語UIでは、訳を用意したモードでも`PathOfLegend`のような内部IDが出る。
- 日本語ラベルに「ランク戦 (Ladder)」のように内部IDを併記しているのは、旧`GameModeLabels`時代の「日本語 (英語名)」形式の名残。
- `Ladder`が「ランク戦」、`Ranked1v1`が「ランク戦(1vs1)」と、別モードがほぼ同じ名前になっている。実データでは`Ladder`は`type=trail`(トロフィーロード側)。訳語は公式情報で裏取りしてから直す。

**2. カード名の訳の抜け6枚と、キーの綴り違い1件**
- 公式API `/cards`(通常123枚+タワーユニット4枚)と`messages_ja.properties`を突き合わせた。
- 訳が無いカードと、270戦中の出現回数:

  | カード | 出現回数 |
  |---|---|
  | `Spirit Empress` | 23 |
  | `Vines` | 21 |
  | `Ronin` | 16 |
  | `Boss Bandit` | 9 |
  | `Goblin Curse` | 8 |
  | `Goblin Demolisher` | 2 |

  日本語UIでも、これらは英語名のまま表示される。
- `Vines`は、辞書側のキーが`card.Vine`(末尾のsが無い)になっている。訳を用意してあるのに効いていない。
- 新カードの追加に気付く仕組みが無い。`LabelResolver`が辞書に無いキーに当たったらWARNログを出す、などで検知できるようにしたい。

**3. 不正な`?lang=`でHTTP 500になる**
- 実測: `?lang=a.b`で500。
- 原因: `LocaleChangeInterceptor`の`ignoreInvalidLocale`は既定で`false`。そのため、`StringUtils.parseLocale`が投げる`IllegalArgumentException`が、そのまま500になる(spring-webmvc 7.0.9のバイトコードで確認)。
- さらに、エラー画面を表示するための転送先(`/error`)でもインターセプタが同じ例外を投げる。そのためアプリのエラー画面は出ず、**Tomcat既定の素のエラーページ**(`HTTP Status 500 – Internal Server Error`、英語固定・サイトのデザイン無し)が返る(2026-09-17に実測、ログにも例外が2回出る)。
- URLを書き換えるだけで500を出せるので、ログや監視のノイズになる。`WebConfig`で`setIgnoreInvalidLocale(true)`にする。

**4. 対応していない言語の扱い**

| リクエスト | `<html lang>` | 本文 | 問題 |
|---|---|---|---|
| `Accept-Language: fr` | `fr` | 英語 | 属性と本文の言語が食い違う |
| `Accept-Language: fr,ja;q=0.9` | `fr` | 英語 | 第2候補の日本語が読めるのに英語になる |
| `Accept-Language: zh-TW,ja;q=0.8` | `zh` | 英語 | 同上 |
| `?lang=fr` | `fr` | 英語 | 対応していない`fr`をCookieに保存してしまう |

- 原因: `request.getLocale()`は最優先の1言語しか見ない。
- 対応案: 対応言語(ja/en)のリストを持ち、Accept-Languageのq値の順に、最初に一致した対応言語を選ぶ(`Locale.lookup`、または`AcceptHeaderLocaleResolver#setSupportedLocales`と同じ考え方)。`?lang=`も対応言語以外は無視する。これで`<html lang>`も実際の表示言語と一致する。
- 国別ランキングの`CountryPreference`も、Accept-Languageを自前でq値の順に解釈している。言語の判定も同じ考え方に揃えられる。

### 表示の自然さ

**5. 対戦日時が`20260917T030143.000Z`のまま、UTCで表示される**
- 対象: `player.html`の対戦履歴一覧、`battle-detail.html`の見出しの下。読みにくいうえに、利用者のタイムゾーンと合っていない。
- ロケールに応じた書式で表示する(例: 日本語`2026/09/17 12:01`、英語`Sep 17, 2026, 3:01 AM`)。
- タイムゾーンはAccept-Languageからは決められない。「言語がjaならAsia/Tokyo」と割り切るか、ブラウザ側のJS(`Intl.DateTimeFormat`)で変換するかを決める必要がある。URLのキーとしての`battleTime`は今のまま使う。
- 関連1: 最終アクセスの「今日/1日前」は、UTC基準の24時間単位の経過なので、利用者の暦日とずれることがある。
- 関連2: 寄付数リセットの注記「毎週日曜0時(GMT)」は、日本語なら「日曜9時(日本時間)」の方が親切。

**6. 英語の単数・複数(`1 crowns`など)**
- 単数でも複数形のまま出るメッセージ:
  - `battle.crowns={0} crowns`(対戦詳細で「1 crowns」になる)
  - `stats.draws=({0} draws)`
  - `stats.summary=... in the last {0} battles`
  - `clanSearch.members={0} members`
- `clan.lastSeen.day`/`days`はテンプレートの`th:switch`で分岐している。英語の「1とそれ以外」という単複ルールを前提にしたロジックがテンプレートにある状態で、複数形が3種類以上ある言語(ロシア語など)を足すと破綻する。
- ICU4Jの`MessageFormat`(`{0, plural, one{# crown} other{# crowns}}`)を`MessageSource`に組み込めば、単複を言語ごとのメッセージ側で表現でき、テンプレートの分岐も消せる。依存ライブラリが1つ増える。Java標準の`ChoiceFormat`は、`#`の衝突で過去にハマっている(`進捗ログ.md`参照)。

**7. 日本語の記号がテンプレートに直書きされている**
- 注記の先頭の`※`(`player.html`に3箇所、`clan.html`に2箇所、`ranking.html`に1箇所)が、英語UIにも出る。
- `battle-detail.html`のデッキ見出しの全角括弧が、英語UIでも使われる(`Your deck(Name)`)。
- 記号や括弧もメッセージ側に含める(例: `battle.deck.team=自分のデッキ({0})` / `Your deck ({0})`)。

**8. 数値の桁区切り**
- トロフィーやクランスコアは、テンプレートで数値をそのまま出しており、`85000`のように区切りが無い。
- 一方、メッセージの`{0}`は`MessageFormat`が区切りを入れるため、画面内で表記が混ざりうる。
- `#numbers.formatInteger(x, 1, 'DEFAULT')`などで、ロケールに応じた区切りに揃える。

### 運用・構造

**9. 言語選択のCookieが、ブラウザを閉じると消える**
- 実測: `Set-Cookie: lang=en; Path=/; SameSite=Lax`で、Max-Ageが無い(`CookieLocaleResolver`の既定)。
- `setCookieMaxAge`で有効期限を延ばす。国選択のCookieは365日にしているので、それに揃える。

**10. エラー画面の言語切替リンクの行き先が`/error?lang=...`になる**
- 実測: 存在しないURLなど、サーブレットコンテナのエラー処理を経由した場合に起きる。`GlobalModelAttributes`が`request.getRequestURI()`を使っており、エラー処理の中では`/error`になるため。
- 元のURLが入っているリクエスト属性`RequestDispatcher.ERROR_REQUEST_URI`を使う。

**11. 日英のメッセージキーの一致がテストで担保されていない**
- 2026-09-17の確認では、`card.*`/`gamemode.*`以外のキーは日英で一致していた。ただ、キーを追加したときの片方の漏れは検知できない。
- 2つの`.properties`を読んでキーの集合を比べるテストは、純粋ロジックのテストの範囲で書ける。

**12. 検索エンジンやキャッシュへの言語の伝え方**(上の「ページビュー向上に関するTODO」と関連)
- 同じURLでも、CookieやAccept-Languageによって返す言語が変わる。それなのに`Vary`ヘッダが無い(`Content-Language`は付いている)。将来CDNやリバースプロキシを挟むと、別の言語のページがキャッシュから返る恐れがある。
- 検索エンジンのクローラーは基本的にAccept-Languageを送らないため、英語ページはインデックスされにくい。`?lang=en`付きのURLを`<link rel="alternate" hreflang>`で示すと、英語圏からの流入を狙える。
