# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@AGENTS.md

## Before writing any code

This project pins `next@16.3.4`, a version newer than your training data with breaking API/convention changes. Per the imported `AGENTS.md` block (auto-managed by `next dev` — see `node_modules/next/dist/server/lib/generate-agent-files.js`), read the relevant guide under `node_modules/next/dist/docs/` before making changes, and heed any deprecation notices found there.

## Commands

- `pnpm dev` — start the dev server (App Router, http://localhost:3000)
- `pnpm build` — production build
- `pnpm start` — run the production build
- `pnpm lint` — run ESLint (flat config in `eslint.config.mjs`, extends `eslint-config-next`'s core-web-vitals and typescript rule sets)

There is no test setup in this project yet.

## Architecture

This is a stock `create-next-app` scaffold (App Router, TypeScript, Tailwind CSS v4, pnpm) with no custom routes, API handlers, or components added beyond the default template (`app/layout.tsx`, `app/page.tsx`, `app/globals.css`). The `@/*` import alias maps to the repo root (see `tsconfig.json`).

Package installs are constrained via `pnpm-workspace.yaml` (`sharp` and `unrs-resolver` builds are disabled).
