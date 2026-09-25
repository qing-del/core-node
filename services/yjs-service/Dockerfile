FROM node:22-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json ./
COPY src ./src
RUN npm run build

FROM node:22-alpine

WORKDIR /app

COPY --chown=node:node package.json package-lock.json ./
RUN npm ci --omit=dev
COPY --from=build --chown=node:node /app/dist ./dist

USER node

EXPOSE 3100
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 CMD node -e "fetch('http://127.0.0.1:3100/internal/yjs/merge',{method:'POST',headers:{'content-type':'application/json'},body:'{\"baseState\":null,\"updates\":[]}' }).then(response=>process.exit(response.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "dist/server.js"]
