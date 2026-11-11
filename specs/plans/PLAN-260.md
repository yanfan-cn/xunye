# PLAN-260

1. UI 文档 §1.3 增「过渡动画 / Material Motion」硬性条款。
2. `AiProviderPresets`：wizard 仅 deepseek（首）、xiaomi_mimo、zhipu_glm、qwen；更新 suggestedModels；旧厂 showInWizard=false。
3. `AiConfigScreen`：顶栏两 Action；隐藏显示名称；自定义才显示模型 ID；测试连接在保存上方。
4. `AiConfigViewModel`：注入 `AiTextGateway`；`TestWizardConnection`；默认选 DeepSeek。
5. `AiChatScreen`：移除思考档位按钮；发送路径强制 `AiReasoningLevel.AUTO`。
6. 中英文字符串；assembleAlohaDebug。
