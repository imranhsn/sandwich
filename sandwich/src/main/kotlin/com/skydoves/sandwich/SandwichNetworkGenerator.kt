package com.skydoves.sandwich

import com.skydoves.sandwich.generator.SandwichGenerator
import java.io.File // Added for potential future use, good to have.
import java.net.MalformedURLException // For specific exception handling
import java.io.IOException // For specific exception handling

/**
 * Generates a network module using Sandwich based on a Swagger/OpenAPI specification.
 *
 * @param packageName The package name for the generated Kotlin client code.
 * @param swaggerFilePath The local file path or URL to the Swagger/OpenAPI specification file.
 * @param outputDir The directory where the generated network module should be saved.
 *
 * @throws IllegalArgumentException If any of the input parameters are invalid.
 * @throws IOException If an I/O error occurs during file operations or network access.
 * @throws RuntimeException If the code generation process fails for other reasons (e.g., openapi-generator-cli issues).
 */
fun generateNetworkModule(packageName: String, swaggerFilePath: String, outputDir: String) {
    try {
        val generator = SandwichGenerator()
        generator.generate(packageName, swaggerFilePath, outputDir)
        println("Network module generation initiated successfully for package '$packageName' at '$outputDir'.")
    } catch (e: IllegalArgumentException) {
        System.err.println("Error generating network module: Invalid arguments. ${e.message}")
        throw e // Re-throw to allow calling code to handle it
    } catch (e: MalformedURLException) {
        System.err.println("Error generating network module: Invalid Swagger file URL. ${e.message}")
        throw IllegalArgumentException("Invalid Swagger file URL: $swaggerFilePath", e)
    } catch (e: IOException) {
        System.err.println("Error generating network module: I/O error. ${e.message}")
        // It could be that the swagger file is not found, or output dir is not writable
        throw e
    } catch (e: RuntimeException) {
        System.err.println("Error generating network module: Generation process failed. ${e.message}")
        // This can include openapi-generator-cli errors or timeouts
        throw e
    } catch (e: Exception) {
        System.err.println("An unexpected error occurred during network module generation: ${e.message}")
        throw RuntimeException("Unexpected error during network module generation.", e)
    }
}
