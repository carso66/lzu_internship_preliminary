# AI 记录索引

来源为用户提供的一份 Chatbox 本地备份，覆盖一个会话，不代表所有客户端或平台账户的全部请求。

| 文件 | 用途 |
|---|---|
| [chatbox-message-index.csv](chatbox-message-index.csv) | 128 条消息的时间、角色、请求模型、结束原因与已保存 token 字段 |
| [chatbox-usage-summary.json](chatbox-usage-summary.json) | 可复核的模型统计、备份范围、原 ZIP 的 SHA-256 |
| [chatbox-dialogue-redacted.txt](chatbox-dialogue-redacted.txt) | 按 M 编号保存的用户提问与助手可见正文 |
| [codex-export-recovery.txt](codex-export-recovery.txt) | Codex 报告导出排错的历史说明及当时恢复摘要，非完整原始对话导出 |
| [key-scenes.txt](key-scenes.txt) | Chatbox 表设计、输出上限、L1 修订、分批审查及 Jackson 错误建议摘录 |

模型归属使用每条助手消息的 modelId，不以正文中“我是什么模型”的自述覆盖历史。
备份共有 65 条助手消息，其中 63 条带模型 ID、2 条为无模型字段的交接摘要；63 条里含 9 条错误和 2 条 length 终止记录。仅 54 条保存了 usage。
这些是客户端记录口径，不是已经核对的平台计费调用数。inputTokens 包含缓存输入，不应再把 cachedInputTokens 加一次；reasoningTokens 是输出统计的细分，不作为额外费用估算。

本目录未复制 settings.json、原始 ZIP 或推理正文。对话中的凭据、手机号、邮箱、地址值等已脱敏，纯文本行尾空格已规范化；原始附件正文及图片仍保留在用户本地备份中。
备份的 18 条 warning 都是 attachment-portability，表示迁移设备后需重建附件路径/索引/上传句柄，不等于业务代码或模型请求失败。

Chatbox 记录截至 2026-11-25 22:35，包含当时全量原型的审查和 18/46 项证据；最终改为 L2-11/L2-12 的精简版本后，新的 11/41 项报告见 ../test-evidence。这段后续调整来自 Codex 协作与 Git/工具记录，不能归入此前 Chatbox 对话。
费用由本人按平台账单最后补填；本目录不包含账单，也不依据 token 数推算金额。
