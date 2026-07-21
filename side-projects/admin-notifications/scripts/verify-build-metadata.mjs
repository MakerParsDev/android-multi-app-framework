import { readFileSync } from "node:fs";
const payload = JSON.parse(readFileSync(new URL("../dist/build-metadata.json", import.meta.url), "utf8"));
if (!/^[0-9a-f]{7,40}$/i.test(payload.gitSha ?? "")) throw new Error("build metadata gitSha is not traceable");
if (Number.isNaN(Date.parse(payload.builtAt ?? ""))) throw new Error("build metadata builtAt is invalid");
console.log(`Admin build metadata verified: gitSha=${payload.gitSha}`);
