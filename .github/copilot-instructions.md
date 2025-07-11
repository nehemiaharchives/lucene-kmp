## Overall Instruction
I'm porting apache lucene from java to platform agnostic kotlin common code.
The code needs to be generated strictly avoiding any java specific code.
Use only kotlin common code and libraries for multiplatform development.

## Specific Instruction
* do not use String.toByteArray() but use String.encodeToByteArray() instead.
* because this project is kotlin common project, in common code, do not use String.format function but use kotlin string interpolation.

## Logging
* when logging, use the following code:
* import io.github.oshai.kotlinlogging.KotlinLogging
* private val logger = KotlinLogging.logger {}
* logger.debug { "message" }

## Running Unit Tests

### Priority 1, Intelij IDEA MCP Server
When you have access to Intelij IDEA MCP server, you should use the IDEA's internal test runner.

### Priority 2, Gradle command line
When you don't have access to Intelij IDEA MCP server, you should use the command line Gradle test runner.
