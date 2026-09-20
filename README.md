# LR-站点追踪

Android 站点目录与可用性检测工具。

## 功能
- 内置 wpzzz/blocked-sites-in-south-korea 的 list.txt 与 kr.list
- 自动去重、搜索、分类、收藏、分页
- 单站点检测与当前页批量检测
- 内置 WebView 浏览器，支持视频全屏与外部浏览器打开
- 可手动从上游 GitHub 同步最新列表并缓存
- 成人/博彩类别打开前二次确认

## 网络边界
该应用只做普通 HTTP/HTTPS 连通性检测与浏览，不包含代理、VPN、DoH 解锁、域前置或其他绕过地区/运营商限制的功能。

## 构建
GitHub Actions 会在 lr-site-tracker-apk 分支自动构建 debug APK，并上传为 Actions Artifact。
