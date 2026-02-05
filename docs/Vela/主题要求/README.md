# 主题要求

> 项目适配说明（2026-02-04）
> - 主题规范以`docs/主题要求/theme.md` 为准（不在此处重复）。
> - 主题文件保存路径：`internal://files/themes/{themeId}/theme.json`。
> - 手表端切换主题时会校验 `theme.json` 与 `checksums.json`（如存在）。
> - 主题传输协议见：`docs/基础通信/Theme_Transfer_Protocol.md`。
> - 运行时会以内置主题作为默认值进行深度合并，但必须至少包含 colors 的必填字段（theme/background/text_primary/text_secondary）。
> - 若系统不支持 `file.readArrayBuffer`，二进制文件的 checksum 校验会被跳过（仍会校验 text）。

