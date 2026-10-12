# Builds the website, then serves the static files with Caddy on port 3000.

FROM node:24-alpine AS build
WORKDIR /app
RUN npm install --global pnpm@11
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
# Baked into the JavaScript at build time: where the browser finds the API,
# and where "Get it on Google Play" goes.
ARG VITE_API_URL
ARG VITE_PLAY_URL
RUN test -n "$VITE_API_URL" || (echo "VITE_API_URL is required, e.g. https://api.example.com" && false)
RUN pnpm exec vite build

FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
EXPOSE 3000
