# AGENTS

## Project
- CraftedGateway is a Mohist Minecraft plugin for SanctuaryMC.
- Optimize for memory performance.

## Guidance
- Prefer bounded caches; avoid unbounded lists/maps.
- Reuse objects and buffers where safe.
- Favor primitive arrays or collections in hot paths.
- Lazy-load data and release references when no longer needed.
- Keep allocations out of tick/event loops where possible.

## Workflow
- After major edits or after finishing TODOs, open a PR for review.
- If repo/branch details are missing, ask for the target base branch and remote.
