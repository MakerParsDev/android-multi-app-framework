#!/usr/bin/env node
import { execFileSync, spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, "../..");
const project = process.argv[2];
const extraArgs = process.argv.slice(3);
const projects = {
  "admin-notifications": {
    cwd: "side-projects/admin-notifications",
    healthEnv: "ADMIN_NOTIFICATIONS_METADATA_URL",
    command: ["node_modules/.bin/wrangler", "pages", "deploy", "dist", "--project-name=admin-notifications"],
  },
  "admin-api": {
    cwd: "side-projects/cloudflare/workers/admin-api",
    healthEnv: "ADMIN_API_HEALTH_URL",
    command: ["node_modules/.bin/wrangler", "deploy"],
    worker: true,
  },
  "content-api": {
    cwd: "side-projects/cloudflare/workers/content-api",
    healthEnv: "CONTENT_API_HEALTH_URL",
    command: ["node_modules/.bin/wrangler", "deploy"],
    worker: true,
  },
  "ssv-callback": {
    cwd: "side-projects/cloudflare/workers/ssv-callback",
    healthEnv: "SSV_CALLBACK_HEALTH_URL",
    command: ["node_modules/.bin/wrangler", "deploy"],
    worker: true,
  },
  "firebase-functions": {
    cwd: "side-projects/firebase/functions",
    healthEnv: "FIREBASE_FUNCTIONS_HEALTH_URL",
    method: "POST",
    command: ["../rules-tests/node_modules/.bin/firebase", "deploy", "--only", "functions", "--project", "makerpars-oaslananka-mobil", "--config", "../firebase.json"],
  },
};
if (!projects[project]) throw new Error(`Unsupported side project: ${project ?? "missing"}`);
const config = projects[project];
const sha = execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();
const worktreeStatus = execFileSync(
  "git",
  ["status", "--porcelain", "--untracked-files=all"],
  { cwd: root, encoding: "utf8" },
).trim();
if (worktreeStatus) {
  throw new Error("Refusing deployment from a dirty worktree; commit or remove every source change first");
}
const reportPath = path.join(root, "build/reports/side-projects/quality.json");
const report = JSON.parse(readFileSync(reportPath, "utf8"));
if (report.status !== "passed" || report.gitSha !== sha || report.projects?.[project]?.status !== "passed") {
  throw new Error(`Verified side-project quality artifact is missing or stale for ${project}`);
}
const completedAt = Date.parse(report.completedAt ?? "");
if (!Number.isFinite(completedAt) || Date.now() - completedAt > 6 * 60 * 60 * 1000) {
  throw new Error("Verified side-project quality artifact is older than six hours");
}
const healthUrl = process.env[config.healthEnv]?.trim();
if (!healthUrl) throw new Error(`${config.healthEnv} is required for strict post-deploy drift verification`);
const builtAt = new Date().toISOString();
const args = [...config.command.slice(1)];
if (config.worker) {
  args.push("--var", `SERVICE_GIT_SHA:${sha}`, "--var", `SERVICE_BUILD_TIMESTAMP:${builtAt}`, "--var", "SERVICE_ENVIRONMENT:production");
}
args.push(...extraArgs);
const executable = path.join(root, config.cwd, config.command[0]);
const result = spawnSync(executable, args, { cwd: path.join(root, config.cwd), stdio: "inherit", env: process.env });
if (result.error) throw result.error;
if (result.status !== 0) process.exit(result.status ?? 1);
const endpoints = JSON.stringify({
  [project]: { url: healthUrl, method: config.method ?? "GET" },
});
execFileSync(
  "python3",
  [
    path.join(root, "scripts/ci/check_side_project_deployment_drift.py"),
    "--expected-git-sha", sha,
    "--endpoints-json", endpoints,
    "--mode", "strict",
    "--report", path.join(root, `build/reports/side-projects/${project}-deployment.json`),
  ],
  { cwd: root, stdio: "inherit" },
);
