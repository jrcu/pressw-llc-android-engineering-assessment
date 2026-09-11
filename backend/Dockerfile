# pnpm@11.8.0 (pinned in package.json) requires Node >=22.13 — node:20-alpine
# fails on "Unknown built-in module: node:sqlite" during `pnpm install`.
FROM node:22-alpine

WORKDIR /app

RUN corepack enable

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .

EXPOSE 3000

CMD ["pnpm", "dev"]
