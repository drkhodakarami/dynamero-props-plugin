package com.dynamero.props

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class PropsPluginTest extends Specification {

    @TempDir
    Path testProjectDir

    Project project

    def setup() {
        project = ProjectBuilder.builder()
                .withProjectDir(testProjectDir.toFile())
                .build()
    }

    def "case 1: fails when file is missing"() {
        when: "Loading non-existent local.properties"
        new PropsExtension(project, "local")

        then: "Throws GradleException mentioning missing file"
        def ex = thrown(GradleException)
        ex.message.contains("Missing required configuration file")
        ex.message.contains("local.properties")
    }

    def "case 2: fails when file exists but requested property is missing"() {
        given: "sometest.properties containing only some_key"
        File propsFile = testProjectDir.resolve("sometest.properties").toFile()
        propsFile.text = "some_key=some_value\n"

        when: "Accessing an absent property"
        def props = new PropsExtension(project, "sometest")
        def _ = props.non_existent_key

        then: "Throws GradleException"
        def ex = thrown(GradleException)
        ex.message.contains("Required property 'non_existent_key' is missing or empty in sometest.properties")
    }

    def "case 3: fails when file exists and key exists but value is empty"() {
        given: "sometest.properties with a blank property"
        File propsFile = testProjectDir.resolve("sometest.properties").toFile()
        propsFile.text = """
            some_key=some_value
            some_missing_key=
        """.stripIndent()

        when: "Accessing the empty property"
        def props = new PropsExtension(project, "sometest")
        def _ = props.some_missing_key

        then: "Throws GradleException"
        def ex = thrown(GradleException)
        ex.message.contains("Required property 'some_missing_key' is missing or empty in sometest.properties")
    }

    def "case 4: passes when file, key, and value all exist"() {
        given: "sometest.properties with valid key-value pair"
        File propsFile = testProjectDir.resolve("sometest.properties").toFile()
        propsFile.text = "some_key=some_value\n"

        when: "Creating extension"
        def props = new PropsExtension(project, "sometest")

        then: "Value is returned via dynamic dot syntax, map syntax, and require()"
        props.some_key == "some_value"
        props['some_key'] == "some_value"
        props.require("some_key") == "some_value"
    }

    def "case 5: loads custom secondary properties file via .load() method"() {
        given: "sometest.properties and custom prop_file.properties in the temp directory"
        File baseFile = testProjectDir.resolve("sometest.properties").toFile()
        baseFile.text = "default_key=default_value\n"

        File secondaryFile = testProjectDir.resolve("prop_file.properties").toFile()
        secondaryFile.text = "prop_field=my_secret_token\n"

        when: "Initializing extension with base file and loading secondary file"
        def defaultProps = new PropsExtension(project, "sometest")
        def customProps = defaultProps.load("prop_file")

        then: "Both instances resolve their respective keys independently"
        defaultProps.default_key == "default_value"
        customProps.prop_field == "my_secret_token"
        customProps.require("prop_field") == "my_secret_token"
    }
}