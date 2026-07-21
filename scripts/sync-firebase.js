const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Configuration
const CONFIG_FILE = path.join(__dirname, '../config/firebase-apps.json');
const APP_SRC_DIR = path.join(__dirname, '../app/src');

// Colors for console output
const colors = {
  reset: "\x1b[0m",
  green: "\x1b[32m",
  yellow: "\x1b[33m",
  red: "\x1b[31m",
  blue: "\x1b[34m"
};

function log(message, color = colors.reset) {
  console.log(`${color}${message}${colors.reset}`);
}

function ensureDirectoryExistence(filePath) {
  const dirname = path.dirname(filePath);
  if (fs.existsSync(dirname)) {
    return true;
  }
  ensureDirectoryExistence(dirname);
  fs.mkdirSync(dirname);
}

function getRemoteConfig(appId, projectId) {
  try {
    // Use Firebase CLI to get config
    // Using --json might be cleaner if supported, but sdkconfig usually outputs strict file content
    const cmd = `firebase apps:sdkconfig android ${appId} --project ${projectId}`;
    log(`Executing: ${cmd}`, colors.blue);
    const output = execSync(cmd, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });

    // The output might contain "App configuration located at..." or the actual JSON.
    // `firebase apps:sdkconfig` typically outputs the JSON content directly, but sometimes with preamble.
    // Let's sanitize. Find the start of JSON.
    const jsonStart = output.indexOf('{');
    const jsonEnd = output.lastIndexOf('}');
    if (jsonStart === -1 || jsonEnd === -1) {
      throw new Error("Invalid output format from Firebase CLI");
    }
    return output.substring(jsonStart, jsonEnd + 1);
  } catch (error) {
    log(`Error fetching config for ${appId}: ${error.message}`, colors.red);
    return null;
  }
}

async function main() {
  log("Starting Firebase Config Sync...", colors.green);

  if (!fs.existsSync(CONFIG_FILE)) {
    log(`Config file not found: ${CONFIG_FILE}`, colors.red);
    process.exit(1);
  }

  const config = JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf8'));
  const flavors = Object.keys(config);

  for (const flavor of flavors) {
    const { projectId, appId } = config[flavor];

    if (!projectId || !appId || appId.includes("PLACEHOLDER")) {
      log(`Skipping ${flavor}: Missing or placeholder projectId/appId`, colors.yellow);
      continue;
    }

    const targetFile = path.join(APP_SRC_DIR, flavor, 'google-services.json');

    log(`Processing flavor: ${flavor} (AppID: ${appId})`, colors.blue);

    const remoteConfigContent = getRemoteConfig(appId, projectId);

    if (remoteConfigContent) {
      let shouldWrite = true;

      // Check if file exists and compare
      if (fs.existsSync(targetFile)) {
        const currentContent = fs.readFileSync(targetFile, 'utf8');
        // Simple string comparison (could be improved with JSON deep equal)
        if (JSON.stringify(JSON.parse(currentContent)) === JSON.stringify(JSON.parse(remoteConfigContent))) {
          log(`  Config matches for ${flavor}, skipping overwrite.`, colors.green);
          shouldWrite = false;
        } else {
          log(`  Config changed for ${flavor}, updating...`, colors.yellow);
        }
      } else {
        log(`  Creating new config for ${flavor}...`, colors.green);
      }

      if (shouldWrite) {
        ensureDirectoryExistence(targetFile);
        fs.writeFileSync(targetFile, remoteConfigContent);
        log(`  Successfully wrote to ${targetFile}`, colors.green);
      }
    } else {
      log(`  Failed to fetch config for ${flavor}`, colors.red);
    }
  }

  log("Sync Complete!", colors.green);
}

main();
