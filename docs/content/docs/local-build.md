---
title: "Local Documentation Build"
description: "Build the website and Dokka API docs locally."
summary: ""
date: 2026-06-23T00:00:00+09:00
lastmod: 2026-06-23T00:00:00+09:00
draft: false
weight: 40
toc: true
params:
  seo:
    title: "Local Documentation Build"
    description: "Build the website and Dokka API docs locally."
    canonical: ""
    robots: ""
---

Build the combined Doks website and Dokka API docs from the repository root:

```bash
scripts/build-docs-site.sh
```

The script runs the configured Dokka task, copies generated API docs into `docs/api/`, builds the Doks site from `docs/`, and copies API docs into `docs/public/api/`.

Generated directories are ignored by Git:

- `docs/api/`
- `docs/public/`
- `docs/node_modules/`
- `build/dokka/`
