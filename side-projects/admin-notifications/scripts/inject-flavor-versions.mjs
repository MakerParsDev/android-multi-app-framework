#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const versionsFile = path.resolve(__dirname, "../../../app-versions.properties");

if (!fs.existsSync(versionsFile)) {
  console.error(`Missing app versions file: ${versionsFile}`);
  process.exit(1);
}

const content = fs.readFileSync(versionsFile, "utf8");
const result = {};

for (const rawLine of content.split(/\r?\n/g)) {
  const line = rawLine.trim();
  if (!line || line.startsWith("#")) continue;

  const equalsIndex = line.indexOf("=");
  if (equalsIndex === -1) continue;

  const key = line.slice(0, equalsIndex).trim();
  const value = line.slice(equalsIndex + 1).trim();
  const dotIndex = key.lastIndexOf(".");
  if (dotIndex === -1) continue;

  const flavor = key.slice(0, dotIndex);
  const field = key.slice(dotIndex + 1);

  if (!result[flavor]) {
    result[flavor] = {};
  }

  if (field === "versionCode") {
    result[flavor].versionCode = Number.parseInt(value, 10);
  } else if (field === "versionName") {
    result[flavor].versionName = value;
  }
}

const escapedJson = JSON.stringify(result).replace(/'/g, "\\'");
console.log(`VITE_FLAVOR_VERSIONS='${escapedJson}'`);
