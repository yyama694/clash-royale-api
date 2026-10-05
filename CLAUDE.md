# プロジェクト設定: Clash Royale API + Spring Boot + OCI デプロイ

このファイルは本プロジェクト(Clash Royale公式APIを使ったWebアプリのOCIデプロイ)専用の作業方針です。
共通の作業方針はグローバルのCLAUDE.mdを参照。個別ルールが競合する場合は本ファイルを優先する。
毎セッション読み込まれるので、ここには**守るルールと、どこに何があるかの道案内**だけを置く。経緯は`進捗ログ.md`、本番環境の詳細は`運用.md`、ハマりどころは`ハマりどころ.md`。

## 進め方

- ユーザーはhello-worldプロジェクト(`C:\dev\hello-world`)でSpring Boot + OCIデプロイを一通り経験済み。1作業ごとの事前説明+確認はしない。通常の粒度で進めてよい。
- ただし次の大きな判断・不可逆な操作は事前に確認する: OCIインフラの作成・削除・変更、GitHubリポジトリの作成、課金が発生しうる操作、既存データ・設定の削除や上書き。
- 確認は自由入力ではなく選択肢形式(AskUserQuestion)で行う。専門用語には簡単な補足を添える(グローバル方針)。
- **例外**: `.md`ファイルへの進捗・記録の記入・更新は、確認なしで行ってよい(2026-09-13にユーザー指示)。
- **例外**: `git push`と本番VMへのデプロイ(scp転送・systemctl restart)は、確認なしで行ってよい(2026-09-14にユーザー指示)。リリース前で、失敗しても実害が無いため。**正式リリースした後は、この例外を見直す**。
- **コードを変更するときは、常にgit worktreeと作業ブランチで作業する**(2026-09-19にユーザーが決定)。masterの作業ツリー(`C:\dev\clash-royale-api`)では直接コードを編集しない。
  - 理由: 同じ作業ツリーを複数セッションで共有すると、他セッションの書きかけの変更がテスト・コミット・jarに混ざる(実際に起きた)。他セッションは後から始まることもあるので、「並行するときだけ」ではなく「常に」。
  - worktreeはEnterWorktreeで`.claude/worktrees/`配下に作る。`config/application-local.yml`はgit管理外なので、ローカルで起動するならworktreeにコピーする。
  - **例外**: `.md`だけの変更は、masterに直接書いてコミットしてよい。コミットでは自分が変えたファイルだけを指定する。
  - 終わったら: worktreeでテスト → masterにマージ → masterでもう一度テスト → push → デプロイ → worktreeとブランチを削除。
- **Chromeは別のセッションと共有している前提で操作する**(2026-09-24にユーザー指示)。同じChrome(Xのログイン状態を含む)を別のセッションが同時に操作していることがある。
  - 自分のタブは`tabs_context_mcp`・`tabs_create_mcp`で自分用に作ったものだけを使う。他のタブの切り替え・遷移・入力・クローズはしない。以前のセッションのタブIDも使い回さない。
  - クリックや文字入力の前には必ずスクリーンショットで今の画面を確かめる。想定と違う画面(知らない下書きが入った投稿ダイアログ、見覚えのないページなど)なら、操作をやめてユーザーに確認する。
  - Xへの投稿は、完了表示を見落としても押し直さない。先にプロフィールで、本文を照合して投稿されたか確かめる(二重投稿を防ぐため)。
  - 終わったら、自分が作ったタブは閉じる。

## プロジェクト概要

- 目的: Clash Royale公式API(developer.clashroyale.com)を使い、プレイヤー・クランの情報を見られるWebアプリを提供する。**学習(Spring Boot実践)・実用・収益化**の3つを兼ねる。
- **広告を入れることは決定済み**(2026-09-21にユーザーが明言。「そのためのサイトでもある」)。入れるかどうかはもう議論しない。機能や宣伝の優先度を決めるときは、ページビュー・回遊への影響も加味してよい。計画は`収益化.md`。AdSense審査の最大の関門は「独自のコンテンツ」(機能サイトで読ませる文章がほぼ無い)。
- **全世界のクラロワユーザー向け**(2026-09-19にユーザーが決定)。特定の国・言語に寄せない。RoyaleAPIのような大手とデータ量・基盤の規模で張り合わない。
- サイト名は「Princess Tower」、ドメインは`princess-tower.duckdns.org`、Xは`@princess_tower8`。**名前に「Clash Royale」などSupercellの商標を使わない。Supercell非公式である旨をフッターと宣伝に必ず入れる**(ファンコンテンツポリシー。商標の問題で2026-09-21に改名した)。
- 機能は1つずつ小さく足していく(実装→動作確認→デプロイ)。フェーズ2以降の案(クイズなど、継続的に遊べるもの)はアイデア段階で、着手したらここを更新する。
- ソース管理はGitHub。デプロイは手動(CI/CDは将来検討)。

## 提供機能

画面の名前と仕様は`画面一覧.md`(ユーザーとのやり取りはこの画面名で統一する)、共通の用語は`用語集.md`、機能ごとの設計は`設計_プレイヤー名検索.md`・`設計_お気に入り.md`。

- プレイヤー(タグ・名前で検索。名前検索はクランの巡回で作った索引を使う)、対戦詳細、クラン(タグ・クラン名で検索)
- 個人ランキング(ランク戦)・クランランキング(各上位1000。グローバル/国・地域別)。トップページに個人ランキング上位100人
- カード一覧・カード詳細(世界トップ層での使用率)、トッププレイヤーのデッキ
- お気に入り(Cookieに保存。DBは使わない)、共有ボタン、プレイヤー情報画面の「前回見たときから」の変化(localStorageに保存)
- プライバシーポリシー(`/privacy`。本文は英語と日本語だけで、他の言語では英語版を出す)
- 10言語(日・英・西・葡・独・仏・伊・露・土・韓。トルコ語・韓国語は2026-10-04に追加)

## 技術スタック

- Java 21 + Spring Boot 4.1.x、Maven、Thymeleaf(サーバーサイドレンダリング)
  - **Boot 4では`RestClient.Builder`の自動設定が`spring-boot-starter-restclient`に分かれている**。無いとDIに失敗する。
- 表示用の文言はすべて`messages.properties`(英語)と`messages_ja`・`_es`・`_pt`・`_de`・`_fr`・`_it`・`_ru`・`_tr`・`_ko`に置く。テンプレートから`T(...)`でstaticメソッドを呼ばない。複数形などはICU(`IcuMessageSource`)。
  - 表示言語は`?lang=xx` > Cookie > Accept-Language > 英語の順に決める。**`?lang=`はその画面の表示だけでCookieに保存しない**(共有・X・検索結果のURLに付けても、踏んだ人の設定を上書きしないため)。保存するのは言語メニューで選んだとき(`?setlang=xx`→保存して`?lang=xx`へ転送。`LanguageInterceptor`)だけ。対応言語の一覧は`SupportedLanguages.SUPPORTED`の1か所だけで、言語切替・hreflang・テストはそこから作る。
  - 言語や用語を足すときは、ゲーム内の公式の表記で裏付ける(方法は`ハマりどころ.md`の「訳語・用語の裏付け」)。
- DBは**当面なし**(2026-09-13にユーザーが決定)。公式APIの結果をそのまま表示する。保存が必要になったらPostgreSQLを検討する。
- 本番: OCIのA1.Flex(Oracle Linux 9)で、Apacheがリバースプロキシ+HTTPS(Let's Encrypt)。構成・設定・定期処理は`運用.md`。

## Clash Royale APIの注意点

- **APIキーはアクセス元IPの許可制**。今のキーは本番VMとローカル開発機の2つ。作成後にIPを足せないので、足すときはキーを作り直す。開発中に403が出たら、ローカルのグローバルIPが変わっていないかを疑う。
- **トークンはリポジトリにコミットしない。jarにも入れない**。ローカルは`config/application-local.yml`(`.gitignore`対象。見本は`config.example.yml`。`mvn spring-boot:run`でlocalプロファイルになる)、本番は`/etc/clash-royale-api/env`の`CLASHROYALE_API_TOKEN`。未設定だと起動時に失敗する(`@NotBlank`)。
- 429は`ApiRateLimitException`で専用の表示にする。応答はCaffeineで2分キャッシュして呼び出し数を抑える。制限値そのものは未確認。
- 返ってくる値の癖(カードレベル・14,000止まり・チャンピオン抜けなど)は`ハマりどころ.md`の「公式APIの仕様」。**APIの値をそのまま出す前に、意味を別のデータで確かめる**。

## デプロイ手順(手動)

デプロイは**masterのコミット済みの状態からだけ**行う。事前に次の2つを確かめる。

- `git worktree list`で作業中の他のworktreeを見て、他のセッションと同時にVMを再起動しない。
- 本番のログで、トッププレイヤーのデッキの集計(`TopDeckCollector`)が動いていないことを確かめる(途中で再起動すると、約50分の集計が最初からやり直しになる)。

1. ローカルで`mvn clean package`(`target/clash-royale-api-0.0.1-SNAPSHOT.jar`)
2. `scp`でVMの`/tmp/clash-royale-api.jar`へ転送(SSHは`opc`、鍵は`C:\Users\Norio Fukuchi\.ssh\oci_clash_royale_api`)
3. VMでjarを差し替えて再起動する(直前のjarを`.bak`に残す)
   ```bash
   sudo mv /opt/clash-royale-api/clash-royale-api.jar /opt/clash-royale-api/clash-royale-api.jar.bak
   sudo mv /tmp/clash-royale-api.jar /opt/clash-royale-api/clash-royale-api.jar
   sudo chown opc:opc /opt/clash-royale-api/clash-royale-api.jar
   sudo systemctl restart clash-royale-api
   sudo systemctl is-active clash-royale-api
   ```
4. `https://princess-tower.duckdns.org/`をcurlで確かめる。起動に40秒前後かかるので、リトライしながら待つ
5. 問題があれば`.bak`を元の名前に戻して`systemctl restart`する(`.bak`は次のデプロイで上書きされる)

## コーディング方針

- Spring Bootの一般的な作法に従う。過度な抽象化はしない。
- **レイヤ構成**:
  - `client` … 公式APIとのHTTP通信とDTO。失敗は`client.exception`の独自例外に変えて投げる(web層に`RestClientResponseException`や`HttpStatus`を漏らさない)。公式APIとの通信は`ClashRoyaleApiClient`に閉じ込める。
  - `domain` … 勝敗判定・集計など、HTTPにもThymeleafにも依存しない計算。
  - `service` … タグ/名前の振り分け、ソート、絞り込みなどのアプリケーションロジック。Controllerには置かない。
  - `web` … Controllerは「パラメータを受けてServiceを呼びModelに詰める」だけ。表示用の整形は`ViewMapper`と`web/view`のViewModelで行い、テンプレートにロジックを書かない。
- エラー画面は`GlobalExceptionHandler`(`@ControllerAdvice`)に集める。Controllerで`model.addAttribute("error", ...)`を書かない。
  - 例外: 画面の一部だけ取れなかったときは、エラー画面にせずその部分に文言を出す(プレイヤー情報画面の対戦履歴、ランキング、お気に入り画面の各行)。
- 秘密情報をapplication.yml などに書いてコミットしない。`.gitignore`は`target/`・`.idea/`・`*.iml`・`/config/`・`application-*.yml`をパターンで弾く。`config`には必ず先頭の`/`を付ける(付けないとソースの`config`パッケージまで無視される)。
- 日本語を含む`.ps1`はUTF-8 BOM付きで保存する(Windows PowerShell 5.1はBOMが無いとCP932として読む)。
- テストは純粋なロジックとMockitoによるService層まで(MockMvc・MockRestServiceServerは指示があってから)。仕様を変えたら、その仕様を検証しているテストも一緒に直す。

## セキュリティ

- 公式APIのキー、OCIの認証情報(APIキー・SSH秘密鍵・config)はコミットしない。
- 外部に開けるポートは22・80・443だけ。アプリの8080番はApache経由でだけ届く。
- **Cookieやサーバー側の状態を変えるPOSTは`CrossSiteRequestGuard`の対象にする**(`WebConfig#addInterceptors`。今は`/favorites/**`)。Spring Securityを入れていないので、他サイトからの送信(CSRF)はここで拒否している。`SameSite=Lax`だけでは足りない。

## 画面についての決めごと

- **トップページの個人ランキングは100人**(2026-09-20にユーザーが決定)。前日に「トップページはできるだけ軽く」として10人にしたのを、ユーザーが反転させた。トップページに機能や件数を足すときは、毎回ユーザーに確認する。
- **Cookie・ブラウザへの保存・外部のサービス(広告・解析など)を足したら、プライバシーポリシー(`/privacy`、`privacy.*`の英語と日本語)も直す**(2026-10-04に設置)。書いてあることと実際が食い違うと、AdSense審査でも利用者に対しても問題になる。

## ドキュメント

| ファイル | 中身 |
|---|---|
| `進捗ログ.md` | 何をいつ、なぜやったか。**別セッションで再開するときはまず「これまでの要点」と直近のフェーズを読む**。9月分は`archive/進捗ログ_2026-09.md` |
| `ハマりどころ.md` | ハマった点と、知らないと間違える仕様を分野別に1行ずつ。作業前に関係する節を読む |
| `運用.md` | 本番VMの構成・設定・定期処理・バックアップ |
| `TODO.md` | 未着手・保留中のタスクだけ。先頭に「日付つきの確認」 |
| `画面一覧.md`・`用語集.md` | 画面の名前と今の仕様、ユーザーとの共通用語 |
| `設計_プレイヤー名検索.md`・`設計_お気に入り.md` | 機能ごとの設計メモ |
| `収益化.md` | 広告導入のロードマップと調査結果 |
| `宣伝戦略.md`・`X運用記録.md`・`Xポスト案.md` | 宣伝の方針、Xの投稿・リプライの記録と分かったこと、投稿案のストック |
| `ユーザ向け情報.md` | 本番環境の中身をユーザー自身が確かめるための手順とコマンド |

**書き方のルール**(2026-10-01に決定。9/27に整理したのに4日で元の量に戻ったため):

- 進捗ログは1フェーズを3〜6行に収める。テストの件数・確かめた一覧・コミット番号など、gitで分かることは書かない。詳しいルールは`進捗ログ.md`の先頭。
- Xの投稿・リプライは`X運用記録.md`だけに書く(進捗ログには書かない)。
- 次にも効くハマりは`ハマりどころ.md`に1行足す。
- `TODO.md`は未着手だけを置き、済んだら消す(経緯は進捗ログにある)。`画面一覧.md`は今の仕様だけを書き、変更の履歴は書かない。
- 月が変わったら、前月分の進捗ログを`archive/`に移す。
- 仕様や方針を変えたら、関連するドキュメント(画面一覧・用語集・この`CLAUDE.md`・スキルの`SKILL.md`)をgrepしてまとめて直す。
- 整理前の全文はgitタグ`docs-before-cleanup-2026-09-27`・`docs-before-cleanup-2026-10-01`で読める(`git show <タグ>:<ファイル名>`)。
