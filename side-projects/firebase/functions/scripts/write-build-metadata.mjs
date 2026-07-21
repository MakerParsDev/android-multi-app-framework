import { execFileSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const projectDir = path.resolve(here, "..");
const repoRoot = path.resolve(projectDir, "../../..");
const candidate = process.env.SERVICE_GIT_SHA
  ?? process.env.BUILD_SOURCEVERSION
  ?? execFileSync("git", ["rev-parse", "HEAD"], { cwd: repoRoot, encoding: "utf8" }).trim();
if (!/^[0-9a-f]{7,40}$/i.test(candidate)) {
  throw new Error("Unable to resolve a traceable git SHA for Firebase Functions");
}
const builtAt = process.env.SERVICE_BUILD_TIMESTAMP ?? new Date().toISOString();
const outputDir = path.join(projectDir, "src/generated");
mkdirSync(outputDir, { recursive: true });
writeFileSync(
  path.join(outputDir, "buildMetadata.ts"),
  `export const BUILD_METADATA = ${JSON.stringify({ gitSha: candidate, builtAt })} as const;\n`,
  "utf8",
);
