package com.skydoves.sandwich.generator

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.PrintStream

@RunWith(MockitoJUnitRunner::class)
class SandwichGeneratorTest {

    private lateinit var tempOutputDir: File
    private val sampleSwaggerPath = "src/test/resources/sample-swagger.json"
    private val fakeJarPath = "src/test/resources/fake-openapi-generator-cli.jar"

    private val originalSysOut = System.out
    private val originalSysErr = System.err
    private lateinit var systemOutCapture: ByteArrayOutputStream
    private lateinit var systemErrCapture: ByteArrayOutputStream

    @Before
    fun setUp() {
        tempOutputDir = File.createTempFile("sandwich_test_", "_dir").apply {
            delete() // delete the file to replace it with a directory
            mkdirs() // create as a directory
        }

        // Capture System.out and System.err
        systemOutCapture = ByteArrayOutputStream()
        System.setOut(PrintStream(systemOutCapture))
        systemErrCapture = ByteArrayOutputStream()
        System.setErr(PrintStream(systemErrCapture))
    }

    @After
    fun tearDown() {
        tempOutputDir.deleteRecursively()

        // Restore System.out and System.err
        System.setOut(originalSysOut)
        System.setErr(originalSysErr)
    }

    // Helper test subclass
    class TestableSandwichGenerator(
        jarPath: String,
        private val simulatedExitCode: Int = 0,
        private val simulateIoException: Boolean = false,
        private val onExecute: ((List<String>, String, String) -> Unit)? = null
    ) : SandwichGenerator(jarPath) {
        var executedCommand: List<String>? = null
        var executionOutputDir: String? = null
        var executionPackageName: String? = null

        override fun executeCommand(command: List<String>, outputDir: String, packageName: String): Int {
            this.executedCommand = command
            this.executionOutputDir = outputDir
            this.executionPackageName = packageName
            onExecute?.invoke(command, outputDir, packageName)

            if (simulateIoException) {
                throw IOException("Simulated I/O error during command execution")
            }
            return simulatedExitCode
        }
    }

    // --- Input Validation Tests ---

    @Test(expected = IllegalArgumentException::class)
    fun `generate with empty package name throws IllegalArgumentException`() {
        val generator = SandwichGenerator(fakeJarPath) // Use real generator for validation
        generator.generate("", sampleSwaggerPath, tempOutputDir.absolutePath)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `generate with invalid package name characters throws IllegalArgumentException`() {
        val generator = SandwichGenerator(fakeJarPath)
        generator.generate("com.example-api", sampleSwaggerPath, tempOutputDir.absolutePath)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `generate with empty swagger file path throws IllegalArgumentException`() {
        val generator = SandwichGenerator(fakeJarPath)
        generator.generate("com.example.api", "", tempOutputDir.absolutePath)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `generate with non-existent local swagger file throws IllegalArgumentException`() {
        val generator = SandwichGenerator(fakeJarPath)
        generator.generate("com.example.api", "nonexistent.json", tempOutputDir.absolutePath)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun `generate with malformed swagger URL should throw IllegalArgumentException from validation`() {
        val generator = SandwichGenerator(fakeJarPath)
        // This URL is syntactically malformed for the URL class constructor
        generator.generate("com.example.api", "htp:/invalid", tempOutputDir.absolutePath)
    }


    @Test(expected = IllegalArgumentException::class)
    fun `generate with empty output directory throws IllegalArgumentException`() {
        val generator = SandwichGenerator(fakeJarPath)
        generator.generate("com.example.api", sampleSwaggerPath, "")
    }

    // --- Command Construction and Simulated Execution Tests ---

    @Test
    fun `generate constructs correct command`() {
        val packageName = "com.example.testapi"
        val testableGenerator = TestableSandwichGenerator(fakeJarPath)
        
        try {
            testableGenerator.generate(packageName, sampleSwaggerPath, tempOutputDir.absolutePath)
        } catch (e: RuntimeException) {
            // Expected if executeCommand returns non-zero, which it does by default (0)
            // but generate() throws if exitCode from executeCommand is not 0
        }


        val expectedCommand = listOf(
            "java", "-jar", fakeJarPath, "generate",
            "-i", sampleSwaggerPath,
            "-g", "kotlin",
            "-o", tempOutputDir.absolutePath,
            "--package-name", packageName
        )
        assertEquals(expectedCommand, testableGenerator.executedCommand)
    }

    @Test
    fun `generate with simulated successful CLI execution`() {
        val packageName = "com.example.success"
        val testableGenerator = TestableSandwichGenerator(
            jarPath = fakeJarPath,
            simulatedExitCode = 0,
            onExecute = { _, outputDir, pkgName ->
                // Simulate CLI creating the correct directory structure
                val packagePath = pkgName.replace('.', File.separatorChar)
                val expectedSourceDir = File(outputDir, "src/main/kotlin/$packagePath")
                expectedSourceDir.mkdirs()
                assertTrue(expectedSourceDir.exists() && expectedSourceDir.isDirectory)
            }
        )

        testableGenerator.generate(packageName, sampleSwaggerPath, tempOutputDir.absolutePath)
        
        val output = systemOutCapture.toString()
        assertTrue(output.contains("Kotlin client code generated successfully."))
        assertTrue(output.contains("Output directory structure verified"))
    }

    @Test
    fun `generate with simulated CLI failure exit code throws RuntimeException`() {
        val packageName = "com.example.failure"
        val testableGenerator = TestableSandwichGenerator(fakeJarPath, simulatedExitCode = 1)

        try {
            testableGenerator.generate(packageName, sampleSwaggerPath, tempOutputDir.absolutePath)
            fail("Expected RuntimeException was not thrown")
        } catch (e: RuntimeException) {
            assertTrue(e.message!!.contains("openapi-generator-cli command failed with exit code 1"))
        }
        val errOutput = systemErrCapture.toString()
        // This message comes from the base class's executeCommand
        assertTrue(errOutput.contains("openapi-generator-cli command failed with exit code 1"))
    }

    @Test
    fun `generate with simulated CLI throwing IOException propagates IOException`() {
        val packageName = "com.example.ioerror"
        val testableGenerator = TestableSandwichGenerator(fakeJarPath, simulateIoException = true)

        try {
            testableGenerator.generate(packageName, sampleSwaggerPath, tempOutputDir.absolutePath)
            fail("Expected IOException was not thrown")
        } catch (e: IOException) {
            assertEquals("Simulated I/O error during command execution", e.message)
        }
         val errOutput = systemErrCapture.toString()
        // This message comes from the base class's executeCommand
        assertTrue(errOutput.contains("An I/O error occurred during code generation: Simulated I/O error during command execution"))
    }
    
    @Test
    fun `generate with successful CLI but missing output directory logs error`() {
        val packageName = "com.example.missingdir"
        // Simulate success from CLI (exit code 0), but DON'T create the directory structure
        val testableGenerator = TestableSandwichGenerator(
            jarPath = fakeJarPath,
            simulatedExitCode = 0
            // No onExecute to create dirs
        )

        testableGenerator.generate(packageName, sampleSwaggerPath, tempOutputDir.absolutePath)

        val output = systemOutCapture.toString()
        assertTrue(output.contains("Kotlin client code generated successfully."))
        
        val errOutput = systemErrCapture.toString()
        val expectedMissingDir = File(tempOutputDir, "src/main/kotlin/" + packageName.replace('.', File.separatorChar))
        assertTrue(errOutput.contains("Generated code not found in the expected directory: ${expectedMissingDir.absolutePath}"))
    }
}
