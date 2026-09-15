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
- **複数セッション運用時のルール**(2026-09-15にユーザーが決定): `ListAgents`で他に動いているセッションが確認できる場合、あるセッションでの調査・レビューの結果としてドキュメント修正やコード修正が必要になったときは、そのセッション自身では編集せず、対象ファイルと修正内容を明記した上で別セッションへ`SendMessage`で依頼する運用をデフォルトとする(「あなたは参照のみ」という指示を毎回省略できるようにするため)。単独セッションのみで作業している場合は、通常通り自分で編集してよい。ユーザーが個別に別の指示をした場合はそちらを優先する。

## プロジェクト概要

- 目的: Clash Royale公式API(developer.clashroyale.com)を利用し、Clash Royaleユーザー向けにプレイヤー情報・クラン情報などを閲覧できるWebアプリを提供する。学習(Spring Boot実践)と実用を兼ねる。
- 最初のマイルストーンは最小機能(プレイヤー検索・クラン検索)とし、その後クイズ機能など追加サービスを継続的に足していく方針(2026-09-13時点でユーザーが表明)。
- ソース管理: GitHub(このディレクトリでgit初期化 → GitHubリポジトリにpush、hello-worldと同様の手順)。
- デプロイは当面手動(SSH接続してjarを配置・再起動)。CI/CD自動化は将来検討。

## 提供機能(段階的に追加)

- **フェーズ1(最小機能)**: プレイヤータグ検索(戦績サマリー・得意/苦手カード・直近対戦履歴表示)、クランタグ検索(メンバー一覧・トロフィー・寄付数表示)。
- **フェーズ2以降(未着手・アイデア段階)**: クラロワクイズなど、ユーザーが継続的に遊べる追加コンテンツ。着手時にこのセクションを更新する。

## 画面一覧

画面名の用語集は`画面一覧.md`(プロジェクトルート)を参照。ユーザーとのコミュニケーションではこのファイルの名称で統一する。

## 技術スタック

- 言語/フレームワーク: Java 21 (LTS) + Spring Boot
- ビルドツール: Maven
- 画面: Thymeleaf(サーバーサイドレンダリング。hello-worldと同じ方針)
- DB: **当面なし**。まずはClash Royale APIの呼び出し結果をそのまま画面に表示する構成で開始し、キャッシュやクイズデータの保存が必要になった段階でPostgreSQL導入を検討する(2026-09-13時点でユーザーが決定)。
- Webサーバー: Spring Boot組み込みTomcatを直接公開(hello-worldと同様、当面リバースプロキシなし)。

## Clash Royale API連携に関する注意点

- APIキーは2026-09-13に取得済み(developer.clashroyale.comでSupercell IDログインして発行)。
- **重要な制約**: Clash Royale公式APIのキーは、アクセス元のIPアドレスを事前に登録(ホワイトリスト)する方式。
  - 本番(OCI VM: `132.226.7.203`)用と、ローカル開発用とでIPアドレスが異なる点に注意。両方とも許可リストに登録済み。
  - ローカル開発機のグローバルIPは固定でない可能性があるため、開発中にAPIが403エラーになった場合はIPアドレスが変わっていないか、developer.clashroyale.com側のキー設定を確認する。
  - Allowed IP Addressesは作成後の編集(追加)が不可のため、IPを追加したい場合はキーの作り直しが必要(詳細は`進捗ログ.md`参照)。
- APIキー(トークン)は絶対にリポジトリにコミットしない。環境変数、またはgitignore対象の設定ファイル(`application-local.yml`等)で管理する。
- Clash Royale APIには呼び出し回数制限がある可能性があるため、実装時に公式ドキュメントでレート制限を確認する。

## インフラ構成(OCI)

- hello-worldプロジェクトで作成したOCI VM(VM.Standard.E2.1.Micro、`140.245.83.216`)は2026-09-13時点でユーザーの意向により削除し、本プロジェクト用に新規VMを作成済み(ローカルの`C:\dev\hello-world`フォルダ・GitHubリポジトリ自体は学習記録としてそのまま残す)。
- 新規VMは2026-09-13に構築完了。パブリックIP: `132.226.7.203`。リージョン: 東京(ap-tokyo-1)。Ampere A1.Flex(ARM, Always Free)は"Out of host capacity"で作成できず、hello-worldと同じ経緯でx86_64の**VM.Standard.E2.1.Micro**(Always Free枠)で作成。OS: Oracle Linux Server 9.8。SSH鍵: `oci_clash_royale_api`(hello-world用を流用)。
- ネットワーク公開・ポート開放・ファイアウォール設定はhello-worldでの手順(firewalld + OCIセキュリティリスト双方の開放が必要)を踏襲し、8080番ポートの開放済み。

## デプロイ手順の方針(手動、hello-worldを踏襲)

- VM上の実体: jar配置先 `/opt/clash-royale-api/clash-royale-api.jar`、systemdサービス名 `clash-royale-api.service`(`opc`ユーザーで実行、起動オプション`--spring.profiles.active=prod`)。
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
4. 動作確認は `http://132.226.7.203:8080/` へのアクセス(`curl`のHTTPステータス確認でも可)で行う
5. 問題があれば `.bak` を元のファイル名に戻して`systemctl restart`することでロールバックする(`.bak`は次回デプロイ時に上書きされるため、長期保管はしない)

## コーディング方針(本プロジェクト固有)

- パッケージ構成やController/Service層の分割など、Spring Bootの一般的な作法に従う。過度な抽象化はしない(グローバル方針と同様)。
- Clash Royale APIとの通信は専用のServiceクラス(例: `ClashRoyaleApiClient`)に閉じ込め、Controllerから直接HTTP呼び出しを行わない。
- application.properties/yml にAPIキーなどの秘密情報を平文でコミットしない。ローカル用設定と本番用設定は分離する。
- .gitignore には target/, .idea/, *.iml, application-local.yml(または同等の秘匿設定ファイル)を含める。

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
