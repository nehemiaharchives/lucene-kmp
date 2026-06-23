import fs from "node:fs";
import path from "node:path";

const apiDir = path.resolve(process.argv[2] ?? "docs/api");

function walk(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  return entries.flatMap((entry) => {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) return walk(fullPath);
    return entry.isFile() && entry.name.endsWith(".html") ? [fullPath] : [];
  });
}

function decodeHtmlAttribute(value) {
  return value
    .replaceAll("&amp;", "&")
    .replaceAll("&quot;", "\"")
    .replaceAll("&#34;", "\"")
    .replaceAll("&#39;", "'");
}

function isLocalPageHref(href) {
  return (
    href.length > 0 &&
    !href.startsWith("#") &&
    !href.startsWith("/") &&
    !/^[a-z][a-z0-9+.-]*:/i.test(href)
  );
}

function targetExists(fromFile, href) {
  const [pathname] = decodeHtmlAttribute(href).split("#", 1);
  if (!isLocalPageHref(pathname)) return true;

  const target = path.normalize(path.resolve(path.dirname(fromFile), pathname));
  if (!target.startsWith(apiDir + path.sep) && target !== apiDir) return true;
  if (fs.existsSync(target) && fs.statSync(target).isFile()) return true;
  if (fs.existsSync(target) && fs.statSync(target).isDirectory()) {
    return fs.existsSync(path.join(target, "index.html"));
  }
  return false;
}

let filesChanged = 0;
let linksRemoved = 0;

for (const file of walk(apiDir)) {
  const original = fs.readFileSync(file, "utf8");
  const updated = original.replace(
    /<a\b([^>]*?)\bhref=(["'])(.*?)\2([^>]*)>([\s\S]*?)<\/a>/g,
    (match, before, quote, href, after, body) => {
      if (targetExists(file, href)) return match;
      linksRemoved += 1;
      return `<span${before}${after}>${body}</span>`;
    }
  );

  if (updated !== original) {
    fs.writeFileSync(file, updated);
    filesChanged += 1;
  }
}

console.log(`Dokka broken-link cleanup: removed ${linksRemoved} links in ${filesChanged} files.`);
