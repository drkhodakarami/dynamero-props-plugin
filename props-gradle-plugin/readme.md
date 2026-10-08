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