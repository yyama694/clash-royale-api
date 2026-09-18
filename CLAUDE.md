# プロジェクト設定: Clash Royale API + Spring Boot + OCI デプロイ

このファイルは本プロジェクト(Clash Royale公式APIを使ったWebアプリのOCIデプロイ)専用の作業方針です。
共通の作業方針はグローバルのCLAUDE.mdを参照。個別ルールが競合する場合は本ファイルを優先する。
基本構成・進め方は `C:\dev\hello-world` プロジェクトを踏襲しているが、下記の点は本プロジェクト用に変更している。

## 進め方(hello-worldからの変更点)

- ユーザーはhello-worldプロジェクトでSpring Boot + OCIデプロイを一通り経験済みのため、hello-worldで採用した「1作業ごとに事前説明+確認」という細かい進め方は本プロジェクトでは採用しない。
- 通常セッションと同等の粒度で進めてよい。ただし下記のような大きな判断・不可逆な操作は引き続き事前確認する(グローバル方針・セッション方針と同様)。
  - OCIインフラの作成・削除
  - GitHubリポジトリ作成
  - 課金が発生し得る操作
  - 既存データ・設定の削除や上書き
- 専門用語が出た際に簡単な補足を添える方針(グローバルCLAUDE.md)は本プロジェクトでも引き続き適用する。
- 確認は自由入力ではなく選択肢形式(AskUserQuestion)で行う(hello-worldでの決定を踏襲)。
- **例外**: `CLAUDE.md`など`.md`ファイルへの進捗状況の記入・更新は、事前確認なしで行ってよい(2026-09-13にユーザーから明示指示。hello-worldでの運用を踏襲)。実際のコード変更・コマンド実行・git操作・外部サービスへの操作(OCI操作、GitHubへのpushなど)は引き続き大きな判断のみ事前確認する。
- **例外**: `git push`とVMへのデプロイ(scp転送・systemctl restart)は、事前確認なしで行ってよい(2026-09-14にユーザーから明示指示。理由: このアプリはまだリリース前であり、pushやデプロイの失敗が実害に繋がらないため)。**アプリを正式リリースした後は、この例外は見直す想定**(リリース後は本番影響があるため、都度確認に戻すか要検討)。GitHubリポジトリ作成やOCIインフラの作成・削除など、他の大きな判断は引き続き事前確認する。
- **(撤回済み)複数セッション運用時のルール**: 2026-09-15に「調査系セッションは自分で編集せず別セッションに依頼する」というルールを一時導入したが、同日中にユーザーの判断で撤回。各セッションは通常通り自分でファイルを編集してよい(経緯は`進捗ログ.md`参照)。

## プロジェクト概要

- 目的: Clash Royale公式API(developer.clashroyale.com)を利用し、Clash Royaleユーザー向けにプレイヤー情報・クラン情報などを閲覧できるWebアプリを提供する。学習(Spring Boot実践)と実用を兼ねる。
- **サービスの方向性(2026-09-18にユーザーが決定)**: 日本のクラロワユーザーに特化したサービスにする。RoyaleAPIのような大手のデッキ統計サイトとは、データ量と基盤の規模で張り合わない。機能を追加するときは「日本のユーザーがこの目的ならこちらを使う」理由になるかで判断する。詳細と公開に向けた優先順位は`TODO.md`の「サービスの方向性」を参照。
- 最初のマイルストーンは最小機能(プレイヤー検索・クラン検索)とし、その後クイズ機能など追加サービスを継続的に足していく方針(2026-09-13時点でユーザーが表明)。
- ソース管理: GitHub(このディレクトリでgit初期化 → GitHubリポジトリにpush、hello-worldと同様の手順)。
- デプロイは当面手動(SSH接続してjarを配置・再起動)。CI/CD自動化は将来検討。

## 提供機能(段階的に追加)

- **フェーズ1(最小機能、実装済み)**: 画面ごとの詳細は`画面一覧.md`を参照。
  - プレイヤータグ検索: 戦績サマリー・得意/苦手カード・直近対戦履歴、対戦詳細(両者のデッキ・タワーユニット)
  - カード詳細画面(`/card/{id}`): 画像(進化があれば2枚)・レアリティ・エリクサー・レベル範囲。対戦詳細や得意/苦手カードのカード画像からリンク
  - クラン検索(タグ・クラン名の両対応): メンバー一覧(役職・トロフィー・寄付数・最終アクセス、列ごとのソート)
  - クランランキング画面(`/ranking`、トップページからリンク): グローバル/国・地域別をタブで切替。対象国はAccept-Language推定+Cookie保存
  - 個人ランキング: トップページに上位10人、個人ランキング画面(`/ranking/players`)に上位200人(ランク戦の現在シーズン。ランク戦は2025年6月に公式がパス・オブ・レジェンドから改名したもの)。どちらもグローバル/国・地域別をタブで切替
  - 日本語/英語の表示切替
- **フェーズ2以降(未着手・アイデア段階)**: クラロワクイズなど、ユーザーが継続的に遊べる追加コンテンツ。着手時にこのセクションを更新する。

## 画面一覧

画面名の用語集は`画面一覧.md`(プロジェクトルート)を参照。ユーザーとのコミュニケーションではこのファイルの名称で統一する。

## 技術スタック

- 言語/フレームワーク: Java 21 (LTS) + Spring Boot 4.1.x
  - 2026-09-15にSpring Boot 3.3.4から4.1.1へ更新。3.3系・3.5系ともOSSサポートが終了しておりセキュリティパッチが届かないため。
  - **Boot 4での注意点**: `RestClient.Builder`の自動設定は`spring-boot-starter-restclient`に分離されている。この依存が無いとDIに失敗する。
- ビルドツール: Maven
- 画面: Thymeleaf(サーバーサイドレンダリング。hello-worldと同じ方針)
  - 表示用ラベルはすべて`messages.properties`(英語) / `messages_ja.properties`(日本語)に集約。テンプレートからの`T(...)`によるstaticメソッド呼び出しは使わない。
  - 表示言語は`?lang=ja` / `?lang=en`で切替(Cookie保持)。未指定時はAccept-Language、それも無ければ日本語。
- DB: **当面なし**。まずはClash Royale APIの呼び出し結果をそのまま画面に表示する構成で開始し、キャッシュやクイズデータの保存が必要になった段階でPostgreSQL導入を検討する(2026-09-13時点でユーザーが決定)。
- Webサーバー: Spring Boot組み込みTomcatを直接公開(hello-worldと同様、当面リバースプロキシなし)。

## Clash Royale API連携に関する注意点

- APIキーは2026-09-13に取得済み(developer.clashroyale.comでSupercell IDログインして発行)。
- **重要な制約**: Clash Royale公式APIのキーは、アクセス元のIPアドレスを事前に登録(ホワイトリスト)する方式。
  - 本番(OCI VM: `132.226.7.203`)用と、ローカル開発用とでIPアドレスが異なる点に注意。両方とも許可リストに登録済み。
  - ローカル開発機のグローバルIPは固定でない可能性があるため、開発中にAPIが403エラーになった場合はIPアドレスが変わっていないか、developer.clashroyale.com側のキー設定を確認する。
  - Allowed IP Addressesは作成後の編集(追加)が不可のため、IPを追加したい場合はキーの作り直しが必要(詳細は`進捗ログ.md`参照)。
- APIキー(トークン)は絶対にリポジトリにコミットしない。**jarにも焼き込まない**(2026-09-15に対応)。
  - ローカル: `./config/application-local.yml`(`src/main/resources`の外、`.gitignore`対象)。サンプルは`config.example.yml`。`mvn spring-boot:run`で自動的にlocalプロファイルが有効になる。
  - 本番: 環境変数`CLASHROYALE_API_TOKEN`。VM上の`/etc/clash-royale-api/env`(root:root 600)をsystemdの`EnvironmentFile`で読み込む。
  - トークン未設定のまま起動すると`ClashRoyaleApiProperties`の`@NotBlank`で起動時に失敗する(403を出し続けるより原因が分かりやすいため)。
- 呼び出し回数制限: 429応答は`ApiRateLimitException`で専用のエラー表示にし、Caffeineキャッシュで呼び出し数自体も抑えている。具体的な制限値は未確認(判明したら追記する)。

## インフラ構成(OCI)

- hello-worldプロジェクトで作成したOCI VM(VM.Standard.E2.1.Micro、`140.245.83.216`)は2026-09-13時点でユーザーの意向により削除し、本プロジェクト用に新規VMを作成済み(ローカルの`C:\dev\hello-world`フォルダ・GitHubリポジトリ自体は学習記録としてそのまま残す)。
- 新規VMは2026-09-13に構築完了。パブリックIP: `132.226.7.203`。リージョン: 東京(ap-tokyo-1)。Ampere A1.Flex(ARM, Always Free)は"Out of host capacity"で作成できず、hello-worldと同じ経緯でx86_64の**VM.Standard.E2.1.Micro**(Always Free枠)で作成。OS: Oracle Linux Server 9.8。SSH鍵: `oci_clash_royale_api`(hello-world用を流用)。
- ネットワーク公開・ポート開放・ファイアウォール設定はhello-worldでの手順(firewalld + OCIセキュリティリスト双方の開放が必要)を踏襲し、8080番ポートの開放済み。

## デプロイ手順の方針(手動、hello-worldを踏襲)

- VM上の実体: jar配置先 `/opt/clash-royale-api/clash-royale-api.jar`、systemdサービス名 `clash-royale-api.service`(`opc`ユーザーで実行、起動オプション`--spring.profiles.active=prod`)。
- **プレイヤー名の蓄積先(2026-09-18に追加)**: `/var/lib/clash-royale-api/player-index/`(opc所有)。`/etc/clash-royale-api/env`の`PLAYER_INDEX_DIR`で指定している(未指定時はWorkingDirectory配下の`data/player-index`)。jarを差し替えても消えないよう、jar置き場とは分けている。詳細は`プレイヤー名検索（検討中）.md`。
- **APIキーの供給(2026-09-15に変更)**: `/etc/clash-royale-api/env`(root:root 600、`CLASHROYALE_API_TOKEN=...`)をsystemdの`EnvironmentFile`で読み込む。以前あった`/opt/clash-royale-api/application-prod.yml`は`.removed`にリネームして退避済み。jar側にも秘密情報は入っていない。
- **VMがフリーズしやすい点に注意**: Always Free枠の低スペック機のため、デプロイ・再起動の前後でSSH/HTTPともに無応答になることがある(2026-09-15に2回発生)。数分待って復帰しなければOCIコンソールからの再起動をユーザーに依頼する。再起動後のアプリ起動は40秒前後かかるため、`curl`は数回リトライする前提で確認する。
- SSH鍵: ローカルの `C:\Users\Norio Fukuchi\.ssh\oci_clash_royale_api`。接続先ユーザーは`opc`(Oracle Linux標準)。
- **2026-09-15にロールバック対応のため手順を変更**(`TODO.md`のデプロイ課題対応): 新jarで上書きする前に現行jarをリネームしてバックアップする運用にした。失敗時は`.bak`を戻せば切り戻せる。

1. ローカルで `mvn clean package` してjarをビルド(`target/clash-royale-api-0.0.1-SNAPSHOT.jar`)
2. `scp` でjarをVMの `/tmp/clash-royale-api.jar` に転送
3. VM上で以下を実行してjarを差し替え、サービスを再起動する
   ```bash
   sudo mv /opt/clash-royale-api/clash-royale-api.jar /opt/clash-royale-api/clash-royale-api.jar.bak
   sudo mv /tmp/clash-royale-api.jar /opt/clash-royale-api/clash-royale-api.jar
   sudo chown opc:opc /opt/clash-royale-api/clash-royale-api.jar
   sudo systemctl restart clash-royale-api
   sudo systemctl is-active clash-royale-api
   ```
4. 動作確認は `http://132.226.7.203:8080/` へのアクセス(`curl`のHTTPステータス確認でも可)で行う。起動に40秒前後かかるためリトライループで待つこと
5. 問題があれば `.bak` を元のファイル名に戻して`systemctl restart`することでロールバックする(`.bak`は次回デプロイ時に上書きされるため、長期保管はしない)

## コーディング方針(本プロジェクト固有)

- パッケージ構成やController/Service層の分割など、Spring Bootの一般的な作法に従う。過度な抽象化はしない(グローバル方針と同様)。
- **レイヤ構成(2026-09-15のコードレビュー対応で整備)**:
  - `client` … 公式APIとのHTTP通信とDTO。失敗は`client.exception`配下の独自例外に変換して投げる(web層に`RestClientResponseException`や`HttpStatus`を漏らさない)。
  - `domain` … 勝敗判定・集計・役職の序列など、HTTPにもThymeleafにも依存しない計算。
  - `service` … タグ/クラン名の振り分け、ソート、絞り込みなどのアプリケーションロジック。Controllerには置かない。
  - `web` … Controllerは「パラメータを受けてServiceを呼びModelに詰める」だけ。表示用の整形は`ViewMapper`と`web/view`のViewModelで行い、テンプレートにロジックを書かない。
- Clash Royale APIとの通信は専用のクラス(`ClashRoyaleApiClient`)に閉じ込め、Controllerから直接HTTP呼び出しを行わない。
- エラー画面は`GlobalExceptionHandler`(`@ControllerAdvice`)に集約する。Controller内で`model.addAttribute("error", ...)`を書かない。
  - 例外: 画面の一部だけ取得に失敗した場合は、エラー画面にせずその部分に文言を出す。プレイヤー情報画面の対戦履歴(`PlayerController`の`battleLogErrorKey`)と、ランキング(`RankingService`が空リストを返し`ranking.unavailable`を表示)がこれに当たる。
- APIレスポンスはCaffeineで2分キャッシュする(レート制限対策と低スペックVMの負荷軽減)。
- application.properties/yml にAPIキーなどの秘密情報を平文でコミットしない。ローカル用設定と本番用設定は分離する。
- .gitignore には target/, .idea/, *.iml, `/config/`, `application-*.yml` を含める(ファイル名の個別列挙ではなくパターンで弾く)。
  - `config`は必ず先頭に`/`を付けてリポジトリ直下に限定する。`config/`だとソースの`config`パッケージ(`src/main/java/.../config/`)まで無視され、`CacheConfig.java`・`WebConfig.java`が一度もコミットされていなかった(2026-09-17に判明・修正)。

## セキュリティ上の注意

- Clash Royale APIキー、OCIの認証情報(APIキー、SSH秘密鍵、config)は絶対にリポジトリにコミットしない。
- VMのセキュリティリストは必要最小限のポートのみ開放する(SSH: 22、アプリ: 8080など)。

## 進捗状況

進捗ログ(何をいつ、なぜやったか、既知の制約・ハマりポイント)は`進捗ログ.md`(プロジェクトルート)を参照。
別セッションで再開する際は、まずそちらを確認する。着手した項目・新たな制約が判明した際は、都度`進捗ログ.md`側を更新する。

## 開発方針

- 大きく作り込まず、機能を1つずつ小さく追加していく方針(2026-09-13にユーザーが表明)。1機能ずつ実装→動作確認→デプロイのサイクルを回す。

## TODO

未着手のタスク一覧は`TODO.md`(プロジェクトルート)を参照。
