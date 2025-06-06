import { existsSync, writeFileSync } from 'fs-extra';
import { relative, resolve } from 'path';

import { getCapacitorPackageVersion } from '../common';
import type { Config } from '../definitions';
import { getMajoriOSVersion } from '../ios/common';
import { logger } from '../log';
import type { Plugin } from '../plugin';
import { getPluginType, PluginType } from '../plugin';

export interface SwiftPlugin {
  name: string;
  path: string;
}

export async function checkPackageManager(config: Config): Promise<'Cocoapods' | 'SPM'> {
  const iosDirectory = config.ios.nativeProjectDirAbs;
  if (existsSync(resolve(iosDirectory, 'CapApp-SPM'))) {
    return 'SPM';
  }

  return 'Cocoapods';
}

export async function findPackageSwiftFile(config: Config): Promise<string> {
  const packageDirectory = resolve(config.ios.nativeProjectDirAbs, 'CapApp-SPM');
  return resolve(packageDirectory, 'Package.swift');
}

export async function generatePackageFile(config: Config, plugins: Plugin[]): Promise<void> {
  const packageSwiftFile = await findPackageSwiftFile(config);
  try {
    const textToWrite = await generatePackageText(config, plugins);
    writeFileSync(packageSwiftFile, textToWrite);
  } catch (err) {
    logger.error(`Unable to write to ${packageSwiftFile}. Verify it is not already open. \n Error: ${err}`);
  }
}

async function generatePackageText(config: Config, plugins: Plugin[]): Promise<string> {
  const iosPlatformVersion = await getCapacitorPackageVersion(config, config.ios.name);
  const iosVersion = getMajoriOSVersion(config);

  let packageSwiftText = `// swift-tools-version: 5.9
import PackageDescription

// DO NOT MODIFY THIS FILE - managed by Capacitor CLI commands
let package = Package(
    name: "CapApp-SPM",
    platforms: [.iOS(.v${iosVersion})],
    products: [
        .library(
            name: "CapApp-SPM",
            targets: ["CapApp-SPM"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", exact: "${iosPlatformVersion}")`;

  for (const plugin of plugins) {
    if (getPluginType(plugin, config.ios.name) === PluginType.Cordova) {
      packageSwiftText += `,\n        .package(name: "${plugin.name}", path: "../../capacitor-cordova-ios-plugins/sources/${plugin.name}")`;
    } else {
      const relPath = relative(config.ios.nativeXcodeProjDirAbs, plugin.rootPath);
      packageSwiftText += `,\n        .package(name: "${plugin.ios?.name}", path: "${relPath}")`;
    }
  }

  packageSwiftText += `
    ],
    targets: [
        .target(
            name: "CapApp-SPM",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")`;

  for (const plugin of plugins) {
    packageSwiftText += `,\n                .product(name: "${plugin.ios?.name}", package: "${plugin.ios?.name}")`;
  }

  packageSwiftText += `
            ]
        )
    ]
)
`;

  return packageSwiftText;
}
