# ⚡ props-gradle-plugin

> **Strict, fail-fast, and idiomatic `.properties` file management for Gradle builds.**

[![Gradle Plugin Portal](https://img.shields.io/badge/Gradle_Portal-com.dynamero.props-02303A?style=for-the-badge&logo=gradle)](https://plugins.gradle.org/plugin/com.dynamero.props)
[![License: LGPL-3.0](https://img.shields.io/badge/License-LGPL--3.0-blue.svg?style=for-the-badge)](https://www.gnu.org/licenses/lgpl-3.0.html)

Stop hardcoding configuration or debugging silent null-pointer exceptions half an hour into a CI/CD run. **props-gradle-plugin** gives you clean, fail-fast property access with dynamic Groovy syntax, fallback resolution, and multi-file support.

---

## ✨ Features

- 🛑 **Fail-Fast by Default**: Missing files, missing keys, or empty/blank values throw informative `GradleException`s immediately at configuration time.
- 🎯 **Clean Dynamic Syntax**: Access keys using native Groovy dot notation (`props.API_KEY`), indexing (`props['API_KEY']`), or standard methods.
- 📁 **Multi-File Loader**: Default to root `gradle.properties`, or load Git-ignored sensitive files (like `local.properties` or `secrets.properties`) on the fly.
- 🛡️ **Zero Recursion & Conflict Safe**: Built on Groovy's MetaObject Protocol with direct field isolation to avoid internal recursion and name collisions.
- 🧪 **Battle-Tested**: 100% test coverage backed by Spock 2 and JUnit Platform.

---

## 🚀 Quick Start

### 1. Apply the Plugin

Add the plugin to your `build.gradle`:

```groovy
plugins {
    id 'com.dynamero.props' version '1.0.0'
}
```

Or using legacy plugin application:

```groovy
buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath 'com.dynamero:props-gradle-plugin:1.0.0'
    }
}

apply plugin: 'com.dynamero.props'
```

## 📖 Usage Guide

By default, applying the plugin binds the props extension directly to your root gradle.properties.

### 1. Reading from the Default File

Given gradle.properties:

```Properties
mod_version=1.0.0
repsy_url=[https://repo.repsy.io/mvn/example](https://repo.repsy.io/mvn/example)
```

Access values in build.gradle:

```groovy
// 1. Dynamic dot notation (fails fast if missing or empty)
version = props.mod_version

// 2. Map-style indexing
println props['repsy_url']

// 3. Explicit require method
String repoUrl = props.require('repsy_url')

// 4. Optional property with fallback default
String branch = props.get('git_branch', 'main')
```

### 2. Loading Custom & Sensitive Files (local.properties)

Keep sensitive credentials out of version control by loading gitignored files on demand with .load(filename):

```
my-project/
├── .gitignore          # Contains 'local.properties'
├── local.properties    # Machine-specific secrets
├── gradle.properties   # Public project configuration
└── build.gradle
```

In local.properties:

```Properties
publishing_token=ghp_secretToken12345
signing_key=mySuperSecretSigningKey
```

In build.gradle:

```Groovy
// Load <root>/local.properties into an isolated handle
def local = props.load('local')

publishing {
    repositories {
        maven {
            url = props.repsy_url // From root gradle.properties
            credentials {
                username = "admin"
                password = local.publishing_token // From root local.properties
            }
        }
    }
}
```

### 3. Multiple file reading at once
#### Option 1: Using your existing load() method (Simplest & Direct)
Because PropsExtension already has the load(String filename) factory method,
you don't actually need to register a second plugin extension.
You can instantiate as many independent instances as you need right inside your
build script:

```Groovy
// In your build script (e.g., curseforge-publishing.gradle or build.gradle)
plugins {
    id 'com.dynamero.props'
}

// Instance 1: Default instance loaded by the plugin (e.g., gradle.properties)
def gradleProps = props

// Instance 2: Distinct instance for local secrets (local.properties)
def localProps = props.load('local')

// Instance 3: Another distinct instance for custom credentials (credentials.properties)
def credsProps = props.load('credentials')

// Usage:
println localProps.CURSEFORGE_TOKEN
println gradleProps.mod_version
```
#### Option 2: Registering multiple named extensions via Gradle Plugin
If you want both instances to exist as top-level DSL objects registered on Gradle's 
project.extensions (so you can access project.props and project.localProps anywhere 
without calling .load() manually), configure your Plugin<Project> implementation to 
register both:

In your PropsPlugin.groovy:

```Groovy
package com.dynamero.props

import org.gradle.api.Plugin
import org.gradle.api.Project

class PropsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        // Primary extension (e.g. gradle.properties)
        project.extensions.create('props', PropsExtension, project, 'gradle')

        // Optional secondary extension (e.g. local.properties) if it exists
        File localFile = project.rootProject.file('local.properties')
        if (localFile.exists()) {
            project.extensions.create('localProps', PropsExtension, project, 'local')
        }
    }
}
```

Then in any consumer build script:

```Groovy
plugins {
    id 'com.dynamero.props'
}

// Both are available as first-class Gradle extensions:
String version = props.mod_version           // reads gradle.properties
String token   = localProps.CURSEFORGE_TOKEN // reads local.properties
```
#### Option 3: Using a NamedDomainObjectContainer (Dynamic Multi-File DSL)
If you want arbitrary, config-driven instances declared cleanly via a Gradle block, 
register a container:

```Groovy
// In your plugin:
def container = project.container(PropsExtension) { name ->
    new PropsExtension(project, name)
}
project.extensions.add('allProps', container)
```

Which allows the consumer build script to define and fetch files dynamically:

```Groovy
allProps {
    local       // loads local.properties
    production  // loads production.properties
}

println allProps.local.CURSEFORGE_TOKEN
```

## 🛡️ Other Ways To Load Plugin

### 1. By Plugin ID via apply plugin:
If the plugin is in buildSrc or already on the script's classpath, 
this applies the plugin by ID directly anywhere inside your build script 
(not restricted to the top of the file like the plugins {} block).
```Groovy
apply plugin: 'com.dynamero.props'
```

### 2. By Class Type via apply plugin:
If your build script has the plugin class on its classpath 
(standard when using buildSrc), you can apply it directly by class without 
resolving the plugin marker artifact:
```Groovy
import com.dynamero.props.PropsPlugin

apply plugin: PropsPlugin
```

### 3. Via the PluginManager or Project API
You can also apply it programmatically on the project or its pluginManager:
```Groovy
// By ID:
pluginManager.apply('com.dynamero.props')

// Or by Class:
import com.dynamero.props.PropsPlugin
plugins.apply(PropsPlugin)
```

### 4. Skip Applying the Plugin Entirely (Direct Instantiation)
Because the plugin is just a wrapper around the PropsExtension class, 
if dynamero-props-plugin is on the classpath (e.g. in buildSrc/build.gradle or 
via buildscript { dependencies { ... } }), you can bypass the plugin and instantiate 
the extension directly:
```Groovy
import com.dynamero.props.PropsExtension

// Create and register directly as an extension:
def prop = project.extensions.create('props', PropsExtension, project, 'gradle')
def local = project.extensions.create('localProps', PropsExtension, project, 'local')

// Or just create plain instances without registering to Gradle extensions:
def gradleProps = new PropsExtension(project, 'gradle')
def localProps  = new PropsExtension(project, 'local')

println localProps.CURSEFORGE_TOKEN
```
This avoids applying any plugin lifecycle logic altogether and lets you manage as many 
independent property readers as you want.

## 🔍 Intelligent File Resolution Strategy

Dynamero Props is engineered specifically for non-trivial Gradle environments—such as 
Minecraft multi-loader mod architectures (:common, :fabric, :neoforge), subproject - parent project
structure, composite builds, and convention plugins under buildSrc.

Instead of naively checking the immediate execution directory, the engine executes a deterministic, 
multi-tiered search cascade to discover <filename>.properties automatically.

## 🗺️ Resolution Priority Hierarchy

```access transformers
[ Evaluation Begins ]
         │
         ▼
 ┌───────────────┐      Found?      ┌────────────────────────┐
 │  Priority 1   │ ───────────────> │ Load Properties & Halt │
 └───────┬───────┘      (YES)       └────────────────────────┘
         │ (NO)
         ▼
 ┌───────────────┐      Found?      ┌────────────────────────┐
 │  Priority 2   │ ───────────────> │ Load Properties & Halt │
 └───────┬───────┘      (YES)       └────────────────────────┘
         │ (NO)
         ▼
 ┌───────────────┐      Found?      ┌────────────────────────┐
 │  Priority 3   │ ───────────────> │ Load Properties & Halt │
 └───────┬───────┘      (YES)       └────────────────────────┘
         │ (NO)
         ▼
 ┌───────────────────────────────────────────────────────────┐
 │ 💥 Throw Fail-Fast GradleException with Diagnostic Trace  │
 └───────────────────────────────────────────────────────────┘
```

| Order | Target Tier | Resolution Method | Ideal Use Case |
|:---:|:---|:---|:---|
| **01** | **Workspace Root** | `project.rootProject.projectDir` | Monorepo credentials (`local.properties`), master build versions, shared mod tokens. |
| **02** | **Subproject Root** | `project.projectDir` | Loader-specific overrides (e.g., Fabric-only metadata or NeoForge test credentials). |
| **03** | **Ancestor Traversal** | Recursive `searchDir.parentFile` | `buildSrc` execution contexts and composite (`includeBuild`) project isolation. |

## 🎯 Step-by-Step Traversal Breakdown
### Tier 1 · Workspace Root (project.rootProject.projectDir)
Primary Anchor — Centralized Single Source of Truth

Whenever a subproject (:fabric, :neoforge) calls props.load('local'), Dynamero Props immediately 
targets the root repository directory. This eliminates the need to duplicate local.properties 
into every child module folder across multi-project builds.

### Tier 2 · Current Project Directory (project.projectDir)
Secondary Anchor — Scoped Submodule Overrides

If the file does not exist at the workspace root, the resolver falls back to the immediate 
module's directory. This permits standalone subprojects or isolated components to define their own 
targeted configuration files without polluting the global scope.

### Tier 3 · Recursive Ancestor Traversal (parentFile)
Fallback Safety Net — Solving the buildSrc Isolation Trap

When convention plugins inside buildSrc/ run, Gradle isolates their execution scope: rootProject 
resolves to buildSrc itself, not your main repository root.

Dynamero Props climbs the directory hierarchy step-by-step from the current working directory until 
it locates the actual project root, ensuring tokens and secret keys are resolved seamlessly without 
hardcoded paths or complex buildscript hacks.

## 🚨 Smart Diagnostic Fail-Fast Reporting
If a required property file cannot be located across any search tier, 
Dynamero Props halts configuration immediately with an exhaustive, actionable diagnosis instead of a 
cryptic NullPointerException:

```access transformers
FAILURE: Build failed with an exception.

* What went wrong:
Missing required configuration file: local.properties

Searched in (in order):
  1. Root project:    E:\_ MODS _\Jilibs\local.properties
  2. Current project: E:\_ MODS _\Jilibs\fabric\local.properties

Available .properties files in root project:
  - gradle.properties
  - curseforge.properties
```
> Why Fail-Fast? Configuration-time validation guarantees that missing tokens or typos in file 
> names crash the build within milliseconds—well before long compilation or publishing tasks are triggered.

---

# 💥 BuildSrc Folder
In modern Gradle (especially with precompiled script plugins and multi-project convention setups),
buildSrc is the #1 place where developers hit classpath and lifecycle headaches.

1. The Classpath Isolation Gotcha: A plugin declared in the root project's plugins {} block 
is not visible to buildSrc. It must be added as an implementation dependency inside buildSrc/build.gradle.
2. Precompiled Script Plugins (.gradle / .gradle.kts in buildSrc): Consumers often don't know how to 
apply external plugins inside precompiled scripts, or how to import extension classes like 
PropsExtension across convention scripts.
3. The Root Project Trap: Inside buildSrc, project.rootProject points to buildSrc/, 
not the project's actual root workspace. Explaining how your plugin's recursive ancestor traversal 
solves this gives you immediate confidence.

## 🛠️ Using Inside `buildSrc` (Convention Plugins)

Gradle treats `buildSrc` as an independent, isolated build evaluated *before* the main project. 
Because of this boundary, using plugins inside `buildSrc` requires a specific setup.

### 1. Add the Plugin Dependency to `buildSrc/build.gradle`

To make Dynamero Props available to your convention plugins, declare it in `buildSrc/build.gradle`:

```groovy
plugins {
    id 'groovy-gradle-plugin' // or 'kotlin-dsl'
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    // Add your maven repo where dynamero-props-plugin is published
}

dependencies {
    implementation 'com.dynamero:dynamero-props-plugin:<version>'
}
```
### 2. Usage Across Your Convention Plugin Hierarchy
Once added to buildSrc/build.gradle, you can consume Dynamero Props across any script or convention 
plugin inside buildSrc/src/main/groovy/ (or kotlin/):

#### A. Precompiled Script Plugins (e.g., curseforge-publishing.gradle)
Apply the plugin by ID and load your property sets directly at the top of the convention script:

```Groovy
plugins {
    id 'io.github.themrmilchmann.curseforge-publish'
    id 'com.dynamero.props'
}

// 1. Default instance (reads root gradle.properties via recursive walk)
def prop = props

// 2. Custom isolated instance for secrets (reads root local.properties)
def local = props.load('local')

curseforge {
    apiToken = local.CURSEFORGE_TOKEN

    publications {
        register("curseForge") {
            projectId = local.CURSEFORGE_ID
            artifacts.register("main") {
                displayName = "${prop.mod_name} [${project.name.capitalize()}] - ${prop.mod_version}"
            }
        }
    }
}
```

#### B. Standalone Groovy Classes (e.g., MyCustomPlugin.groovy)
If you write standard Java/Groovy Plugin<Project> classes under buildSrc, you can instantiate 
PropsExtension directly without applying the plugin:
```Groovy
package com.myproject.plugins

import com.dynamero.props.PropsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class MyCustomPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        // Read configuration immediately during plugin execution
        PropsExtension localProps = new PropsExtension(project, 'local')
        String token = localProps.require('API_TOKEN')
    }
}
```

### 💡 Why Dynamero Props Excels in `buildSrc`
> The rootProject Trap: In standard Gradle, executing project.rootProject.file('local.properties') 
> from inside buildSrc looks inside <repo>/buildSrc/local.properties, causing builds to fail. 
> Dynamero Props uses Ancestor Traversal (Tier 3) to automatically climb past buildSrc and discover 
> your actual workspace root <repo>/local.properties without requiring custom path hacks like 
> rootDir.parentFile.

---

## ⚙️ Behavior & Guarantees

| Situation                         | Plugin Behavior                                             |
|:----------------------------------|:------------------------------------------------------------|
| **Properties file missing**       | 💥 Fails build immediately with path details                |
| **Key missing from file**         | 💥 Throws `GradleException` naming the missing key and file |
| **Key present but blank/empty**   | 💥 Throws `GradleException` (e.g. `TOKEN= ` is rejected)    |
| **`props.get('KEY', 'default')`** | 🛡️ Returns fallback safely if key is absent or empty       |

## 🛠️ Building & Contributing

```Bash
# Clone the repository
git clone [https://github.com/dynamero/props-gradle-plugin.git](https://github.com/dynamero/props-gradle-plugin.git)
cd props-gradle-plugin

# Run unit tests
./gradlew test

# Publish to local Maven cache (~/.m2)
./gradlew publishToMavenLocal
```

## 📄 License

Distributed under the terms of the [GNU Lesser General Public License v3.0](https://www.gnu.org/licenses/lgpl-3.0.html). See [LICENSE](LICENSE) for the full text.