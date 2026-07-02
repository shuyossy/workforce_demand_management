雛形

```
# ID:
- PBI名:
- ステータス: [to do/in progress/done]
- ユーザストーリー/背景
- 受け入れ基準
- 注意事項
- 指摘事項（in progressの場合のみ）
```

# ID: 1

- PBI名: アプリ設定方法見直し
- ステータス: done
- 背景
  - 現状は環境変数と`microprofile-config.properties`両方でアプリの設定を変更可能
- 受け入れ基準
  - アプリの設定は環境変数か`microprofile-config.properties`にできるだけ寄せることができている
  - 設定値が重複管理されていない
- 注意事項
- 指摘事項（in progressの場合のみ）

# ID: 2

- PBI名: 開発・本番の設定値が混合しないようにする
- ステータス: to do
- 背景
  - `WEB-INF/web.xml`で開発で用いるべき値が固定されているのを発見
    - `jakarta.faces.PROJECT_STAGE`とcookieの設定
      - cookieについては、本番がHTTPSでアクセスされる可能性もある（まだ決定ではない、HTTPSで認証リバプロにアクセス、HTTPSでアプリに直接アクセス、HTTPでアプリに直接アクセスなど様々な可能性がある）
- 受け入れ基準
  - `WEB-INF/web.xml`含め全体見直して開発・本番の設定値が混合していないか調査できている
    - 調査の結果によって適切な場所で設定値を指定できるように修正されている
- 注意事項
- 指摘事項（in progressの場合のみ）
