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
 * <p>
 * File resolution strategy: searches in order for the properties file in:
 * <ol>
 *   <li>Root project directory (e.g., for gradle.properties, local.properties)</li>
 *   <li>Current project directory (e.g., for subprojects or buildSrc with their own config)</li>
 *   <li>Parent directories up the tree</li>
 * </ol>
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
 * @version 1.1.0
 */
class PropsExtension extends GroovyObjectSupport {

    final Project project
    final Properties propsData = new Properties()
    final File propertiesFile

    /**
     * Initializes the extension and eagerly loads the specified properties file.
     * <p>
     * Searches for the properties file in multiple locations with this priority:
     * <ol>
     *   <li>Root project directory</li>
     *   <li>Current project directory</li>
     *   <li>Walk up the directory tree</li>
     * </ol>
     * </p>
     *
     * @param project The Gradle project instance
     * @param filename Base name of the properties file without the {@code .properties} extension
     * @throws GradleException If the backing file does not exist in any searched location
     */
    @Inject
    PropsExtension(Project project, String filename) {
        this.project = project

        // Try to find the properties file in multiple locations
        File targetFile = findPropertiesFile(project, filename)

        this.propertiesFile = targetFile

        if (!this.propertiesFile.exists()) {
            String diagnostic = buildDiagnosticInfo(project, filename)
            throw new GradleException(
                    "Missing required configuration file: ${filename}.properties\n" +
                            diagnostic
            )
        }

        // Load the properties now that file exists
        loadPropertiesFile()
    }

    /**
     * Finds the properties file by searching in multiple locations.
     * <p>
     * Search order:
     * 1. Root project directory (project.rootProject.projectDir)
     * 2. Current project directory (project.projectDir)
     * 3. Walk up from current directory
     * </p>
     *
     * @param project The Gradle project instance
     * @param filename The properties file name without extension
     * @return The File object for the properties file (may not exist)
     */
    private static File findPropertiesFile(Project project, String filename) {
        String propFileName = "${filename}.properties"

        // Priority 1: Root project directory
        File rootDir = project.rootProject.projectDir
        if (rootDir != null && rootDir.exists()) {
            File rootFile = new File(rootDir, propFileName)
            if (rootFile.exists()) {
                return rootFile
            }
        }

        // Priority 2: Current project directory
        File currentDir = project.projectDir
        if (currentDir != null && currentDir.exists()) {
            File currentFile = new File(currentDir, propFileName)
            if (currentFile.exists()) {
                return currentFile
            }
        }

        // Priority 3: Walk up the directory tree from current project
        File searchDir = currentDir
        while (searchDir != null && searchDir.exists()) {
            File candidateFile = new File(searchDir, propFileName)
            if (candidateFile.exists()) {
                return candidateFile
            }
            searchDir = searchDir.parentFile
        }

        // If not found, return root project location as the "expected" path for error messages
        return new File(rootDir, propFileName)
    }

    /**
     * Builds diagnostic information to help debug file resolution issues.
     */
    private static String buildDiagnosticInfo(Project project, String filename) {
        StringBuilder sb = new StringBuilder()
        sb.append("\nSearched in (in order):\n")

        File rootDir = project.rootProject.projectDir
        if (rootDir != null) {
            sb.append("  1. Root project: ${rootDir.absolutePath}\n")
            sb.append("     ${new File(rootDir, "${filename}.properties").absolutePath}\n")
        }

        File currentDir = project.projectDir
        if (currentDir != null && currentDir != rootDir) {
            sb.append("  2. Current project: ${currentDir.absolutePath}\n")
            sb.append("     ${new File(currentDir, "${filename}.properties").absolutePath}\n")
        }

        // Show what properties files exist in root
        if (rootDir != null && rootDir.exists() && rootDir.isDirectory()) {
            File[] propsFiles = rootDir.listFiles { file ->
                file.isFile() && file.name.endsWith('.properties')
            }
            if (propsFiles != null && propsFiles.length > 0) {
                sb.append("  Available .properties files in root project:\n")
                propsFiles.each { file ->
                    sb.append("    - ${file.name}\n")
                }
            }
        }

        if (currentDir != null && currentDir.exists() && currentDir != rootDir && currentDir.isDirectory()) {
            File[] propsFiles = currentDir.listFiles { file ->
                file.isFile() && file.name.endsWith('.properties')
            }
            if (propsFiles != null && propsFiles.length > 0) {
                sb.append("  Available .properties files in current project:\n")
                propsFiles.each { file ->
                    sb.append("    - ${file.name}\n")
                }
            }
        }

        return sb.toString()
    }

    /**
     * Loads properties from file with error handling.
     */
    private void loadPropertiesFile() {
        try {
            this.propertiesFile.withInputStream { stream ->
                this.propsData.load(stream)
            }
        } catch (IOException e) {
            throw new GradleException(
                    "Failed to read properties file: ${this.propertiesFile.absolutePath}\n" +
                            "Error: ${e.message}",
                    e
            )
        }
    }

    /**
     * Loads an additional properties file on demand.
     * <p>
     * Uses the same file resolution strategy as the main properties file.
     * Returns a new, independent {@link PropsExtension} instance backed by
     * {@code <filename>.properties}, with the same strict fail-fast validation rules.
     * </p>
     *
     * <h4>Example</h4>
     * <pre>{@code
     * // Loads local.properties from root or current project directory
     * def myProps = props.load('local')
     *
     * // Access properties dynamically or via method calls
     * String secret = myProps.CURSEFORGE_TOKEN
     * String token = myProps.require('API_KEY')
     *}</pre>
     *
     * @param filename Base name of the properties file without the {@code .properties} extension
     * @return A newly initialized {@link PropsExtension} instance for the target file
     * @throws GradleException If the target file does not exist in any searched location
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
     * Fallback dynamic resolution hook for Groovy MOP.
     * Intercepts dynamic dot-property access: {@code props.KEY}.
     *
     * @param name The name of the property being accessed
     * @return Trimmed string value resolved via {@link #require(String)}
     */
    Object propertyMissing(String name) {
        return require(name)
    }
}