package com.dynamero.props

import org.gradle.api.GradleException
import org.gradle.api.Project

import javax.inject.Inject

/**
 * Gradle configuration extension providing strict, fail-fast access to properties files.
 * <p>
 * This extension eagerly validates that the backing properties file exists upon initialization
 * and enforces non-blank values when resolving required keys. It supports idiomatic Groovy
 * dynamic dot-notation, map indexing, explicit requirements, and optional fallbacks.
 * </p>
 *
 * <h3>Usage Example</h3>
 * In your Gradle build script:
 * <pre><code>
 * plugins {
 *     id 'com.dynamero.props'
 * }
 *
 * // 1. Access keys from the default properties file (e.g., gradle.properties)
 * String localUser = props.USER_NAME
 *
 * // 2. Load any secondary/custom properties file on demand
 * def customProps = props.load('prop_file')
 * String customSecret = customProps.prop_field
 * </code></pre>
 *
 * @author Dynamero
 * @version 1.0.0
 */
class PropsExtension extends GroovyObjectSupport {

    final Project project
    final Properties propsData = new Properties()
    final File propertiesFile
    private boolean fileLoaded = false

    /**
     * Initializes the extension and eagerly loads the specified properties file from the root project directory.
     *
     * @param project The Gradle project instance
     * @param filename Base name of the properties file without the {@code .properties} extension
     * @throws GradleException If the backing file does not exist at the root project location
     */
    @Inject
    PropsExtension(Project project, String filename) {
        this.project = project

        // Resolve the root project directory reliably across all contexts
        File rootDir = resolveRootDirectory(project)
        File targetFile = new File(rootDir, "${filename}.properties")

        this.propertiesFile = targetFile

        if (!this.propertiesFile.exists()) {
            String diagnostic = buildDiagnosticInfo(project, rootDir, filename)
            throw new GradleException(
                    "Missing required configuration file: ${filename}.properties\n" +
                            "Expected path: ${this.propertiesFile.absolutePath}\n" +
                            diagnostic
            )
        }

        loadPropertiesFile()
    }

    /**
     * Loads properties from file with error handling.
     */
    private void loadPropertiesFile() {
        if (fileLoaded) {
            return
        }

        try {
            this.propertiesFile.withInputStream { stream ->
                this.propsData.load(stream)
            }
            fileLoaded = true
        } catch (IOException e) {
            throw new GradleException(
                    "Failed to read properties file: ${this.propertiesFile.absolutePath}\n" +
                            "Error: ${e.message}",
                    e
            )
        }
    }

    /**
     * Builds diagnostic information to help debug file resolution issues.
     */
    private static String buildDiagnosticInfo(Project project, File rootDir, String filename) {
        StringBuilder sb = new StringBuilder()
        sb.append("\nDiagnostic Information:\n")
        sb.append("  Root Project Dir: ${project.rootProject.projectDir.absolutePath}\n")
        sb.append("  Current Project Dir: ${project.projectDir.absolutePath}\n")
        sb.append("  Resolved Root Dir: ${rootDir.absolutePath}\n")
        sb.append("  Looking for: ${filename}.properties\n")

        // List files in root dir to help debugging
        if (rootDir.exists() && rootDir.isDirectory()) {
            File[] files = rootDir.listFiles { file ->
                file.name.endsWith('.properties')
            }
            if (files != null && files.length > 0) {
                sb.append("  Available .properties files in root:\n")
                files.each { file ->
                    sb.append("    - ${file.name}\n")
                }
            } else {
                sb.append("  No .properties files found in root directory\n")
            }
        }

        return sb.toString()
    }

    /**
     * Resolves the root project directory reliably across buildSrc, main projects, and subprojects.
     * <p>
     * In buildSrc (a composite build), project.rootProject.projectDir is still the main project root.
     * This method handles edge cases by:
     * 1. Using project.rootProject.projectDir (most reliable)
     * 2. Falling back to walk up the directory tree if needed
     * </p>
     *
     * @param project The current Gradle project
     * @return The root project directory
     */
    private static File resolveRootDirectory(Project project) {
        // Primary strategy: use rootProject.projectDir
        // This works correctly in buildSrc, main project, and all subprojects
        File rootProjectDir = project.rootProject.projectDir

        if (rootProjectDir != null && rootProjectDir.exists()) {
            return rootProjectDir
        }

        // Fallback: walk up from current project directory
        File current = project.projectDir
        File lastValid = current

        while (current != null && current.exists()) {
            lastValid = current

            // Check if this directory looks like a Gradle root
            if (isGradleRoot(current)) {
                return current
            }

            current = current.parentFile
        }

        // Ultimate fallback: return the highest we could walk to
        return lastValid
    }

    /**
     * Checks if a directory is likely a Gradle project root.
     */
    private static boolean isGradleRoot(File dir) {
        return (new File(dir, "settings.gradle").exists() ||
                new File(dir, "settings.gradle.kts").exists() ||
                (new File(dir, "gradle.properties").exists() &&
                        new File(dir, "build.gradle").exists()) ||
                (new File(dir, "gradle.properties").exists() &&
                        new File(dir, "build.gradle.kts").exists()))
    }

    /**
     * Loads an additional properties file on demand from the root project directory.
     * <p>
     * Returns a new, independent {@link PropsExtension} instance backed by
     * {@code <filename>.properties}, with the same strict fail-fast validation rules.
     * </p>
     *
     * <h4>Example</h4>
     * <pre>{@code
     * // Loads <root>/prop_file.properties
     * def myProps = props.load('prop_file')
     *
     * // Access properties dynamically or via method calls
     * String secret = myProps.prop_field
     * String token = myProps.require('prop_field')
     *}</pre>
     *
     * @param filename Base name of the properties file without the {@code .properties} extension
     * @return A newly initialized {@link PropsExtension} instance for the target file
     * @throws GradleException If the target file does not exist at the root project location
     */
    PropsExtension load(String filename) {
        return new PropsExtension(this.@project, filename)
    }

    /**
     * Retrieves a required property value.
     *
     * @param key The key to look up in the properties file
     * @return Trimmed string value associated with the key
     * @throws GradleException If the key does not exist or has an empty/whitespace-only value
     */
    String require(String key) {
        loadPropertiesFile()

        String val = this.@propsData.getProperty(key)

        if (!val || val.trim().isEmpty()) {
            throw new GradleException(
                    "Required property '${key}' is missing or empty in ${this.@propertiesFile.name}\n" +
                            "File path: ${this.@propertiesFile.absolutePath}\n" +
                            "Available properties: ${this.@propsData.stringPropertyNames().join(', ')}"
            )
        }

        return val.trim()
    }

    /**
     * Retrieves an optional property value with a fallback default.
     *
     * @param key The key to look up
     * @param defaultValue Fallback value returned if the key is missing or blank
     * @return Trimmed string value or the provided default
     */
    String get(String key, String defaultValue = null) {
        loadPropertiesFile()
        String val = this.@propsData.getProperty(key)
        return (val != null && !val.trim().isEmpty())
                ? val.trim()
                : defaultValue
    }

    /**
     * Enables map-style index syntax: {@code props['KEY']}.
     *
     * @param key The property key
     * @return Trimmed string value
     * @throws GradleException If the property is missing or blank
     */
    String getAt(String key) {
        return require(key)
    }

    /**
     * Intercepts dynamic dot-property access: {@code props.KEY}.
     *
     * @param name The name of the property being accessed
     * @return Trimmed string value resolved via {@link #require(String)}
     */
    /*@Override
    Object getProperty(String name) {
        // Check if the class itself (or a superclass) defines this property/field
        MetaProperty metaProp = metaClass.getMetaProperty(name)
        if (metaProp != null) {
            return metaProp.getProperty(this)
        }
        return require(name)
    }*/

    /**
     * Fallback dynamic resolution hook for Groovy MOP.
     */
    Object propertyMissing(String name) {
        return require(name)
    }
}