package com.dynamero.props

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Gradle plugin that exposes strict, fail-fast property resolution capabilities to build scripts.
 * <p>
 * Upon application, this plugin eagerly creates and registers a {@link PropsExtension} under the
 * name {@code props}. By default, it binds to {@code gradle.properties} located in the root project
 * directory and validates that the file exists immediately.
 * </p>
 *
 * <h3>Usage Example</h3>
 * Apply the plugin in your {@code build.gradle}:
 * <pre><code>
 * plugins {
 *     id 'com.dynamero.props'
 * }
 *
 * // 1. Read values from the default root gradle.properties
 * String version = props.mod_version
 * String mavenUrl = props.require('repsy_url')
 *
 * // 2. Load secondary/custom configuration files on demand
 * def localProps = props.load('local')       // Loads root local.properties
 * def secretProps = props.load('prop_file')  // Loads root prop_file.properties
 *
 * String secretKey = secretProps.prop_field
 * </code></pre>
 *
 * @author Dynamero
 * @version 1.0.0
 * @see PropsExtension
 */
class PropsPlugin implements Plugin<Project> {
    /**
     * Applies the plugin to the given target project.
     * <p>
     * Registers the {@code props} extension backed by the root project's {@code gradle.properties}.
     * </p>
     *
     * @param project The Gradle target project
     */
    @Override
    void apply(Project project) {
        // Exposes the extension as 'props' in scripts applying this plugin
        project.extensions.create('props', PropsExtension, project, 'gradle')
    }
}