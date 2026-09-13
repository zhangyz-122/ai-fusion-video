# FLUX.2 Klein 官方工作流模板

2026-09-13：已添加用户指定的三个原始模板，JSON可解析，复制前后SHA256一致。

- image_flux2_klein_image_edit_4b_base.json
- image_flux2_klein_image_edit_4b_distilled.json
- image_flux2_klein_text_to_image.json

来源：本机 C:/AI-T8-video-onekey/python/Lib/site-packages/comfyui_workflow_templates_json/templates/ 中的同名文件。保留原始节点、子图、说明和来源链接。

状态：CATALOG_REGISTERED_DISABLED / NOT_RUNTIME_VERIFIED。已通过平台管理页面创建三个同标识的图片工作流目录项，页面目录由11项增至14项，三项均禁用且暂无版本。入口：系统设置 → AI配置 → ComfyUI → 管理工作流。已实际确认三个名称可见。

这些是含子图的ComfyUI界面格式，不是可直接提交的API Format；模板文件仍在本目录，尚未上传为平台版本或发布为生成模型，因此不会出现在可运行模型选择器中。未修改或替换原有生图及文戏视频。

## 原模板模型依赖

| 模板 | diffusion_models | text_encoders | vae |
|---|---|---|---|
| 编辑 Base | flux-2-klein-base-4b-fp8.safetensors | qwen_3_4b.safetensors | full_encoder_small_decoder.safetensors |
| 编辑 Distilled | flux-2-klein-4b-fp8.safetensors | qwen_3_4b.safetensors | flux2-vae.safetensors |
| 文生图（包含两个分支） | flux-2-klein-base-4b.safetensors、flux-2-klein-4b.safetensors | qwen_3_4b.safetensors | flux2-vae.safetensors |

初次扫描默认models目录未找到上述文件；不能据此排除其他外部模型目录。object_info探测未取得有效加载器列表，不能宣称节点检查通过。

## 待完成的集成

1. 核对实际Runtime模型搜索路径及节点列表，确认缺失依赖。
2. 获取必要模型；未经验证不得混用FP8与非FP8文件或替换VAE。
3. 在ComfyUI加载原始模板，选定分支并导出真实API图，保留原模板。
4. 建立提示词、参考图片、种子、尺寸等bindings与依赖清单。
5. 经现有平台API保存草稿、真实测试后发布，连接生图入口。
6. 保存实际产物、版本和运行记录；失败不得通过直接修改数据库标记成功。
