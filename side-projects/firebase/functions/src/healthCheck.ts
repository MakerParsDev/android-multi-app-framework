import { onRequest } from "firebase-functions/v2/https";
import { BUILD_METADATA } from "./generated/buildMetadata";

const REGION = "europe-west1";

type HealthEnvironment = Record<string, string | undefined>;

export function createHealthPayload(environment: HealthEnvironment = process.env) {
    return {
        ok: true,
        service: "firebase-functions",
        environment: environment.FUNCTIONS_ENVIRONMENT?.trim() || "production",
        gitSha: BUILD_METADATA.gitSha,
        builtAt: BUILD_METADATA.builtAt,
        region: REGION,
        timestamp: new Date().toISOString(),
    };
}

export const healthCheck = onRequest(
    { region: REGION, cors: true },
    async (_req, res) => {
        res.status(200).json(createHealthPayload());
    },
);
