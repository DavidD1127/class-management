# 课本风格主题素材

这些封面用于本项目的学科主题，不是正式教材，也不使用出版社名称、徽标或教材审定信息。版式参考课本的平面印刷、清晰学科标题与图文分区，不复制整张教材封面。

## 真实馆藏素材

以下图像均通过 Cleveland Museum of Art Open Access API 核实 `share_license_status: CC0`，下载于 2026-10-26。仅做等比例缩放、WebP 压缩；组件可能因画幅显示局部。原作链接也显示在主题预览区。

| 文件 | 作品与作者 | 年代 | 原作与授权记录 |
| --- | --- | --- | --- |
| chinese.webp | 萧云从，Album of Seasonal Landscapes, Leaf A | 1668 | https://www.clevelandart.org/art/1955.302.4 |
| biology.webp | Sydenham Edwards，White Cape - Coast Lily，植物学版画 | 1806 | https://www.clevelandart.org/art/1955.142 |
| history.webp | Tripod (Ding)，青铜鼎馆藏实物 | 公元前11世纪 | https://www.clevelandart.org/art/2003.2 |
| art.webp | Claude Monet，Water Lilies (Agapanthus) | 约1915-1926 | https://www.clevelandart.org/art/1960.81 |

API 入口：https://openaccess-api.clevelandart.org/api/artworks/ 。图像原始链接为 `https://openaccess-cdn.clevelandart.org/{馆藏号}/{馆藏号}_web.jpg`。

## 原创平面内容

其余九份 SVG 是可编辑的文字排版或学科示意图，不使用图片生成模型，不是第三方图标，也不模拟产品界面。

- 数学：勾股定理、完全平方公式、直角三角形和抛物线 `y = x²`。坐标图是示意，不标注无依据的刻度。
- 英语：字母和听说读写的英文排版。
- 物理：单摆、电路示意和基本物理公式。示意图不表示可直接操作的实验电路。
- 化学：配平的水生成式、烧瓶与试管示意、H / He / Li / Be 元素符号。
- 地理：球面经纬网，不使用虚构的大陆轮廓。
- 政治：公民、规则、权利和义务的书籍式文字排版，不引用或解释具体法律条文。
- 体育：田径场平面示意，不作为工程尺寸图。
- 音乐：高音谱表上的 C 大调上行音阶，4/4 拍，共两小节。
- 信息技术：`0100 0001` 对应十进制 65 和 ASCII 字符 A，以及网络连接示意。

原来的 `frontend/public/theme-art/` 生成插画已退出主题引用，保留原文件以便回退，不作为当前版本素材。
