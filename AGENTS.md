# Codex用の入口

このリポジトリの作業ルールは`CLAUDE.md`にある。**作業を始める前に`CLAUDE.md`を全部読み、そのとおりに進める**。`CLAUDE.md`やスキルに出てくる「Claude」は、このリポジトリで作業するAI(Codexも含む)のこととして読む。

ルールはこのファイルにコピーしない。2026-11-04にコピーで作ったら、4日で古くなった(11/8のクイズの変更や、スキルの修正が入っていなかった)。ルールを足す・変えるときは`CLAUDE.md`を直す。

## スキル

決まった作業の手順書(スキル)は`.claude/skills/<名前>/SKILL.md`にある。依頼を受けたら、まず各`SKILL.md`の先頭の`description`を見て(PowerShellなら`Select-String -Path .claude/skills/*/SKILL.md -Pattern '^description:'`)、合うものがあれば読んで手順に従う。スキルが使うスクリプトや記録(`history/`など)も`.claude/skills/`の中のものを使い、別の場所にコピーしない。

## Claude Code専用の道具の読み替え

`CLAUDE.md`とスキルには、Claude Code専用の道具が出てくる。Codexでは同じことができる方法で行う。

- `EnterWorktree` → `git worktree add .claude/worktrees/<名前> -b <ブランチ名>`(置き場はClaudeと同じ`.claude/worktrees/`)。`ExitWorktree` → リポジトリ直下に移ってから`git worktree remove`
- `AskUserQuestion`(選択肢での確認) → 選択肢を番号付きで示し、返事を待つ
- Claude in Chrome(`mcp__claude-in-chrome__*`) → Codexで使えるブラウザ操作の道具。タブの決めごと(自分で開いたタブだけを使う、操作の前に画面を確かめる、Xの投稿は押し直さない)はそのまま守る

## 終える前に(やり残さない)

Codexは、書いた記録をコミットせずに終えることがあった(11/8に`X運用記録.md`のリプライ4件分が残っていた)。終える前に必ず次を確かめる。

1. `git status`を見て、自分が変えたファイルを残さない。`.md`だけの変更は、masterで自分が変えたファイルだけを指定してコミットし、pushする(`CLAUDE.md`の例外)。
2. コードを変えたなら、`CLAUDE.md`の「終わったら」(worktreeでテスト → masterにマージ → masterでもう一度テスト → push → デプロイ → worktreeとブランチを削除)を最後までやる。worktreeを消したら、`.claude/worktrees/`にフォルダが残っていないかも見る。
3. 最後までできないときは、何が残っているか(ファイル・ブランチ・次にやること)を報告して終える。
