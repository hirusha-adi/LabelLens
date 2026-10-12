# The Label Lens API. Also used, with a different command, to migrate and seed the database.
FROM node:24-alpine

ENV NODE_ENV=production
WORKDIR /app

RUN npm install --global pnpm@11 && npm cache clean --force

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile --prod

# data/ is needed by the seed step; src/ and scripts/ by everything.
COPY db ./db
COPY data ./data
COPY scripts ./scripts
COPY src ./src

USER node
EXPOSE 8787
CMD ["node", "src/server.js"]
