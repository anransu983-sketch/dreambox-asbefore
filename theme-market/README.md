# 梦盒主题市场

仿 Komari theme-market 做的梦盒主题目录。App 内「主题商店」从这里拉取主题列表。

## 目录

- `catalog.json`：主题目录（生产目录），每个条目含 id / name / description / version / author / url / themeMode / seedColor / preview / download。
- `themes/<id>.json`：单个主题的完整 JSON（梦盒主题格式，可直接用「从 URL 安装」）。

## 提交主题

1. 在 `themes/` 下新增 `<id>.json`，格式：
   ```json
   {"id":"my-theme","name":"我的主题","version":"1.0","author":"作者",
    "description":"一句话描述","themeMode":"dark","seedColor":"#FF6B9D",
    "sourceUrl":"https://raw.githubusercontent.com/anransu983-sketch/dreambox-android/main/theme-market/themes/my-theme.json"}
   ```
   - `themeMode`：`dark` / `light` / `system`
   - `seedColor`：`#RRGGBB` 或 `#AARRGGBB`
   - `id` 只允许小写字母、数字、中划线
2. 在 `catalog.json` 的 `themes` 数组里加一条对应记录（含 `download` 指向上面的 raw 地址）。
3. 提 PR 或直接推 main，App 端下次打开商店即生效。
