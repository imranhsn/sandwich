package com.skydoves.sandwich.generator

import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit

class SandwichGenerator(
    private val openapiGeneratorJarPath: String = "/tmp/openapi-generator-cli.jar"
) {

    // Protected for testing; allows overriding the actual command execution.
    protected fun executeCommand(command: List<String>, outputDir: String, packageName: String): Int {
        try {
            val process = ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()

            // Wait for the command to complete (with a timeout)
            val finished = process.waitFor(60, TimeUnit.SECONDS) // Use a shorter timeout for tests if needed

            if (finished && process.exitValue() == 0) {
                println("Kotlin client code generated successfully.")

                // Verify output directory structure
                val packagePath = packageName.replace('.', File.separatorChar)
                val expectedSourcePath = "src" + File.separator + "main" + File.separator + "kotlin" + File.separator + packagePath
                val expectedGeneratedDir = File(outputDir, expectedSourcePath)

                if (!expectedGeneratedDir.exists() || !expectedGeneratedDir.isDirectory) {
                    System.err.println("Generated code not found in the expected directory: $expectedGeneratedDir")
                    // In a real scenario, this might be an error condition.
                    // For now, just logging. Could throw an exception if strictness is required.
                } else {
                    println("Output directory structure verified: $expectedGeneratedDir")
                }
                return 0 // Success
            } else {
                val errorOutput = process.errorStream.bufferedReader().readText()
                if (errorOutput.isNotBlank()) {
                    System.err.println("Error generating Kotlin client code: $errorOutput")
                }
                if (!finished) {
                    process.destroyForcibly()
                    throw RuntimeException("openapi-generator-cli command timed out.")
                }
                System.err.println("openapi-generator-cli command failed with exit code ${process.exitValue()}")
                return process.exitValue().takeIf { it != 0 } ?: -1 // Ensure non-zero for failure
            }
        } catch (e: IOException) {
            System.err.println("An I/O error occurred during code generation: ${e.message}")
            e.printStackTrace()
            throw e // Re-throw as it's a significant issue
        } catch (e: InterruptedException) {
            System.err.println("Code generation process was interrupted: ${e.message}")
            e.printStackTrace()
            Thread.currentThread().interrupt() // Preserve interrupt status
            throw RuntimeException("Code generation process interrupted.", e)
        } catch (e: Exception) {
            System.err.println("An unexpected error occurred during command execution: ${e.message}")
            e.printStackTrace()
            throw e // Re-throw
        }
    }

    fun generate(packageName: String, swaggerFilePath: String, outputDir: String) {
        // Validate inputs
        require(packageName.isNotBlank()) { "Package name cannot be empty." }
        require(packageName.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$"))) {
            "Invalid package name: $packageName"
        }
        require(swaggerFilePath.isNotBlank()) { "Swagger file path or URL cannot be empty." }
        // Check if swaggerFilePath is a URL or a local file
        val isUrl = try {
            URL(swaggerFilePath).toURI() // Validate URL syntax
            true
        } catch (e: Exception) {
            false
        }
        if (!isUrl) {
            val swaggerFile = File(swaggerFilePath)
            require(swaggerFile.exists() && swaggerFile.isFile) {
                "Swagger file not found or is not a file: $swaggerFilePath"
            }
        }
        require(outputDir.isNotBlank()) { "Output directory cannot be empty." }

        // Create the output directory if it doesn't exist
        val outputDirectory = File(outputDir)
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }

        // Construct the command
        val command = listOf(
            "java",
            "-jar",
            openapiGeneratorJarPath, // Use the constructor-provided JAR path
            "generate",
            "-i", swaggerFilePath,
            "-g", "kotlin",
            "-o", outputDir,
            "--package-name", packageName
            // Add any other relevant CLI options here if they become configurable
        )

        // Create the output directory if it doesn't exist (as ProcessBuilder might need it)
        val outputDirectory = File(outputDir)
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }
        
        val exitCode = executeCommand(command, outputDir, packageName)
        if (exitCode != 0) {
            throw RuntimeException("openapi-generator-cli command failed with exit code $exitCode (simulated or actual).")
        }
        // Success is handled within executeCommand or implied if no exception is thrown.
    }
}
