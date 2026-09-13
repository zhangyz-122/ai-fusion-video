# Bundled AgentScope skills

This directory is the classpath repository for bundled AgentScope skills.
Each skill lives in its own subdirectory and contains a `SKILL.md` file.

The bundled set is intentionally domain-specific for the AI comic production
platform. It describes planning and validation rules only; it does not grant
shell access, external network access, or permission to bypass platform tools.

The current rules were reviewed against the open Agent Skills specification and
two public film-production skill implementations: `zhangzhangco/film-production-skills`
for traceable production contracts and `62656456/ai-film-skills` for directing,
storyboard, action, and AI-video handoff logic. The platform keeps the result as
an application-owned, single-entry bundle rather than copying provider-specific
runtime code or exposing a multi-skill workflow to end users.
