# Builds the Render web service from server/. The Android app is not part of the image.
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY server/package.json server/package-lock.json ./
RUN npm ci --omit=dev
COPY server/src ./src
USER node
CMD ["node", "src/index.js"]
