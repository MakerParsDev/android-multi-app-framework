import * as admin from "firebase-admin";
import * as logger from "firebase-functions/logger";
import { onRequest } from "firebase-functions/v2/https";
import { GoogleAuth } from "google-auth-library";
import { authenticateAdminRequest } from "./adminAuth";

const REGION = "europe-west1";
const REMOTE_CONFIG_SCOPE = "https://www.googleapis.com/auth/firebase.remoteconfig";
const REMOTE_CONFIG_API_BASE = "https://firebaseremoteconfig.googleapis.com/v1";

type RemoteConfigParameter = {
    defaultValue?: { value?: string };
    description?: string;
    valueType?: string;
};

type RemoteConfigTemplate = {
    parameters?: Record<string, RemoteConfigParameter>;
    parameterGroups?: Record<string, {
        description?: string;
        parameters?: Record<string, RemoteConfigParameter>;
    }>;
    conditions?: unknown[];
    version?: unknown;
};

const remoteConfigAuth = new GoogleAuth({ scopes: [REMOTE_CONFIG_SCOPE] });

async function getRemoteConfigAccessToken(): Promise<string> {
    const client = await remoteConfigAuth.getClient();
    const tokenResponse = await client.getAccessToken();
    const accessToken = typeof tokenResponse === "string"
        ? tokenResponse
        : tokenResponse?.token;

    if (!accessToken) {
        throw new Error("Failed to resolve Remote Config access token");
    }

    return accessToken;
}

function resolveProjectId(): string {
    return process.env.GCLOUD_PROJECT
        ?? admin.app().options.projectId
        ?? "";
}

async function fetchRemoteConfigTemplate(projectId: string, accessToken: string) {
    return fetch(`${REMOTE_CONFIG_API_BASE}/projects/${projectId}/remoteConfig`, {
        method: "GET",
        headers: {
            Authorization: `Bearer ${accessToken}`,
            "Accept-Encoding": "gzip",
        },
    });
}

export const adminGetRemoteConfig = onRequest(
    { region: REGION, cors: true },
    async (req, res) => {
        if (req.method !== "GET") {
            res.status(405).json({ error: "Method not allowed" });
            return;
        }

        const auth = await authenticateAdminRequest(req.get("authorization"));
        if (!auth.ok) {
            res.status(auth.statusCode).json({ error: auth.error });
            return;
        }

        try {
            const projectId = resolveProjectId();
            if (!projectId) {
                throw new Error("Missing Firebase project id");
            }

            const accessToken = await getRemoteConfigAccessToken();
            const rcResponse = await fetchRemoteConfigTemplate(projectId, accessToken);

            if (!rcResponse.ok) {
                const body = await rcResponse.text().catch(() => "");
                logger.error("Remote Config fetch failed", {
                    status: rcResponse.status,
                    body,
                    uid: auth.uid,
                });
                res.status(502).json({ error: `Remote Config API: HTTP ${rcResponse.status}` });
                return;
            }

            const etag = rcResponse.headers.get("etag") ?? undefined;
            const template = await rcResponse.json() as RemoteConfigTemplate;

            logger.info("adminGetRemoteConfig success", {
                uid: auth.uid,
                paramCount: countParameters(template),
            });

            res.status(200).json({
                parameters: template.parameters ?? {},
                parameterGroups: template.parameterGroups ?? {},
                conditions: template.conditions ?? [],
                version: template.version ?? null,
                etag,
            });
        } catch (error) {
            logger.error("adminGetRemoteConfig error", { error });
            res.status(500).json({ error: "Failed to fetch Remote Config" });
        }
    },
);

export const adminUpdateRemoteConfig = onRequest(
    { region: REGION, cors: true },
    async (req, res) => {
        if (req.method !== "POST") {
            res.status(405).json({ error: "Method not allowed" });
            return;
        }

        const auth = await authenticateAdminRequest(req.get("authorization"));
        if (!auth.ok) {
            res.status(auth.statusCode).json({ error: auth.error });
            return;
        }

        const body = typeof req.body === "object" && req.body !== null
            ? req.body as Record<string, unknown>
            : {};
        const key = typeof body.key === "string" ? body.key.trim() : "";
        const value = typeof body.value === "string" ? body.value : "";
        const description = typeof body.description === "string" ? body.description : undefined;
        const requestedGroupName = typeof body.groupName === "string" ? body.groupName.trim() : "";

        if (!key) {
            res.status(400).json({ error: "key is required" });
            return;
        }

        if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key)) {
            res.status(400).json({ error: "Invalid Remote Config key format" });
            return;
        }

        try {
            const projectId = resolveProjectId();
            if (!projectId) {
                throw new Error("Missing Firebase project id");
            }

            const accessToken = await getRemoteConfigAccessToken();
            const currentResponse = await fetchRemoteConfigTemplate(projectId, accessToken);

            if (!currentResponse.ok) {
                const bodyText = await currentResponse.text().catch(() => "");
                logger.error("Remote Config prefetch failed", {
                    status: currentResponse.status,
                    body: bodyText,
                    uid: auth.uid,
                    key,
                });
                res.status(502).json({ error: `Remote Config API: HTTP ${currentResponse.status}` });
                return;
            }

            const etag = currentResponse.headers.get("etag") ?? "*";
            const currentTemplate = await currentResponse.json() as RemoteConfigTemplate;
            const parameters = { ...(currentTemplate.parameters ?? {}) };
            const parameterGroups = cloneParameterGroups(currentTemplate.parameterGroups);

            const resolvedLocation = resolveParameterLocation(currentTemplate, key, requestedGroupName);
            const updatedParameter: RemoteConfigParameter = {
                ...resolvedLocation.parameter,
                ...(description !== undefined ? { description } : {}),
                defaultValue: { value },
            };

            if (resolvedLocation.groupName) {
                const existingGroup = parameterGroups[resolvedLocation.groupName] ?? {};
                parameterGroups[resolvedLocation.groupName] = {
                    ...existingGroup,
                    parameters: {
                        ...(existingGroup.parameters ?? {}),
                        [key]: updatedParameter,
                    },
                };
                delete parameters[key];
            } else {
                parameters[key] = updatedParameter;
            }

            const updateResponse = await fetch(
                `${REMOTE_CONFIG_API_BASE}/projects/${projectId}/remoteConfig`,
                {
                    method: "PUT",
                    headers: {
                        Authorization: `Bearer ${accessToken}`,
                        "Content-Type": "application/json; charset=UTF-8",
                        "If-Match": etag,
                    },
                    body: JSON.stringify({
                        parameters,
                        parameterGroups,
                        conditions: currentTemplate.conditions ?? [],
                    }),
                },
            );

            if (!updateResponse.ok) {
                const bodyText = await updateResponse.text().catch(() => "");
                logger.error("Remote Config update failed", {
                    status: updateResponse.status,
                    body: bodyText,
                    uid: auth.uid,
                    key,
                });
                res.status(502).json({ error: `Remote Config API: HTTP ${updateResponse.status}` });
                return;
            }

            logger.info("adminUpdateRemoteConfig success", { uid: auth.uid, key, groupName: resolvedLocation.groupName ?? null });
            res.status(200).json({ success: true, key, groupName: resolvedLocation.groupName ?? null });
        } catch (error) {
            logger.error("adminUpdateRemoteConfig error", { error, key, uid: auth.uid });
            res.status(500).json({ error: "Failed to update Remote Config" });
        }
    },
);

function countParameters(template: RemoteConfigTemplate): number {
    const rootCount = Object.keys(template.parameters ?? {}).length;
    const groupedCount = Object.values(template.parameterGroups ?? {}).reduce((sum, group) => {
        return sum + Object.keys(group.parameters ?? {}).length;
    }, 0);
    return rootCount + groupedCount;
}

function cloneParameterGroups(source: RemoteConfigTemplate["parameterGroups"]): NonNullable<RemoteConfigTemplate["parameterGroups"]> {
    const result: NonNullable<RemoteConfigTemplate["parameterGroups"]> = {};
    for (const [groupName, group] of Object.entries(source ?? {})) {
        result[groupName] = {
            ...group,
            parameters: { ...(group.parameters ?? {}) },
        };
    }
    return result;
}

function resolveParameterLocation(
    template: RemoteConfigTemplate,
    key: string,
    requestedGroupName: string,
): { groupName?: string; parameter: RemoteConfigParameter } {
    if (requestedGroupName) {
        const requestedGroup = template.parameterGroups?.[requestedGroupName];
        return {
            groupName: requestedGroupName,
            parameter: requestedGroup?.parameters?.[key] ?? {},
        };
    }

    for (const [groupName, group] of Object.entries(template.parameterGroups ?? {})) {
        const groupedParameter = group.parameters?.[key];
        if (groupedParameter) {
            return {
                groupName,
                parameter: groupedParameter,
            };
        }
    }

    return {
        parameter: template.parameters?.[key] ?? {},
    };
}
