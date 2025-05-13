# Overall Instruction
I'm porting apache lucene from java to platform agnostic kotlin common code.
The code needs to be generated strictly avoiding any java specific code.
Use only kotlin common code and libraries for multiplatform development.

# Specific Instruction
* do not use String.toByteArray() but use String.encodeToByteArray() instead.
* because this project is kotlin common project, in common code, do not use String.format function but use kotlin string interpolation.

# Logging
* import io.github.oshai.kotlinlogging.KotlinLogging
* private val logger = KotlinLogging.logger {}
* logger.debug { "message" }
