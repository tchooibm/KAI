# App runbook

## Deploy

1. Build the image with temurin:17 as the base.
2. Push the image to the registry.
3. Start the container and open port 8080.
4. Check the health page.

## Rollback

Redeploy the previous image and check the health page again.
