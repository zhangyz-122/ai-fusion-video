# ARCHITECTURE 摘要
后端 ai-fusion-video:Spring Boot 3 + MyBatis-Plus(Flyway) + Redis 队列 + AgentScope V2 管道 + ComfyUI 执行器(绑定渲染)。
前端 ai-fusion-video-web:Next.js 16 App Router + Shadcn(base-ui) + zustand/pipeline-store + TaskStream SSE。
生产链:StoryboardItem→ProductionRun/Step→VideoTask(count=3)→VideoItem→ProductionTake→QC→selectedTakeId→VideoComposeService。
