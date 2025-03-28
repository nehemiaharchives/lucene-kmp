package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.ParseException


/**
 * Use by certain classes to match version compatibility across releases of Lucene.
 *
 *
 * **WARNING**: When changing the version parameter that you supply to components in Lucene,
 * do not simply change the version at search-time, but instead also adjust your indexing code to
 * match, and re-index.
 */
class Version private constructor(major: Int, minor: Int, bugfix: Int, prerelease: Int = 0) {
    /** Major version, the difference between stable and trunk  */
    val major: Int

    /** Minor version, incremented within the stable branch  */
    val minor: Int

    /** Bugfix number, incremented on release branches  */
    val bugfix: Int

    /** Prerelease version, currently 0 (alpha), 1 (beta), or 2 (final)  */
    val prerelease: Int

    // stores the version pieces, with most significant pieces in high bits
    // ie:  | 1 byte | 1 byte | 1 byte |   2 bits   |
    //         major   minor    bugfix   prerelease
    private val encodedValue: Int

    init {
        this.major = major
        this.minor = minor
        this.bugfix = bugfix
        this.prerelease = prerelease
        // NOTE: do not enforce major version so we remain future proof, except to
        // make sure it fits in the 8 bits we encode it into:
        require(!(major > 255 || major < 0)) { "Illegal major version: $major" }
        require(!(minor > 255 || minor < 0)) { "Illegal minor version: $minor" }
        require(!(bugfix > 255 || bugfix < 0)) { "Illegal bugfix version: $bugfix" }
        require(!(prerelease > 2 || prerelease < 0)) { "Illegal prerelease version: $prerelease" }
        require(!(prerelease != 0 && (minor != 0 || bugfix != 0))) {
            ("Prerelease version only supported with major release (got prerelease: "
                    + prerelease
                    + ", minor: "
                    + minor
                    + ", bugfix: "
                    + bugfix
                    + ")")
        }

        encodedValue = major shl 18 or (minor shl 10) or (bugfix shl 2) or prerelease

        require(encodedIsValid())
    }

    /** Returns true if this version is the same or after the version from the argument.  */
    fun onOrAfter(other: Version): Boolean {
        return encodedValue >= other.encodedValue
    }

    override fun toString(): String {
        if (prerelease == 0) {
            return "$major.$minor.$bugfix"
        }
        return "$major.$minor.$bugfix.$prerelease"
    }

    override fun equals(o: Any?): Boolean {
        return o != null && o is Version && o.encodedValue == encodedValue
    }

    // Used only by assert:
    private fun encodedIsValid(): Boolean {
        require(major == ((encodedValue ushr 18) and 0xFF))
        require(minor == ((encodedValue ushr 10) and 0xFF))
        require(bugfix == ((encodedValue ushr 2) and 0xFF))
        require(prerelease == (encodedValue and 0x03))
        return true
    }

    override fun hashCode(): Int {
        return encodedValue
    }

    companion object {
        @Deprecated("Use latest")
        val LUCENE_10_0_0: Version = Version(10, 0, 0)

        /**
         * Match settings and bugs in Lucene's 10.1.0 release.
         *
         */
        @Deprecated("Use latest")
        val LUCENE_10_1_0: Version = Version(10, 1, 0)

        /**
         * Match settings and bugs in Lucene's 10.2.0 release.
         *
         */
        @Deprecated("Use latest")
        val LUCENE_10_2_0: Version = Version(10, 2, 0)

        /**
         * Match settings and bugs in Lucene's 11.0.0 release.
         *
         *
         * Use this to get the latest &amp; greatest settings, bug fixes, etc, for Lucene.
         */
        val LUCENE_11_0_0: Version = Version(11, 0, 0)

        // To add a new version:
        //  * Only add above this comment
        //  * If the new version is the newest, change LATEST below and deprecate the previous LATEST
        /**
         * **WARNING**: if you use this setting, and then upgrade to a newer release of Lucene, sizable
         * changes may happen. If backwards compatibility is important then you should instead explicitly
         * specify an actual version.
         *
         *
         * If you use this constant then you may need to **re-index all of your documents** when
         * upgrading Lucene, as the way text is indexed may have changed. Additionally, you may need to
         * **re-test your entire application** to ensure it behaves as expected, as some defaults may
         * have changed and may break functionality in your application.
         */
        val LATEST: Version = LUCENE_11_0_0

        /**
         * Constant for backwards compatibility.
         *
         */
        @Deprecated("Use {@link #LATEST}")
        val LUCENE_CURRENT: Version = LATEST

        /**
         * Constant for the minimal supported major version of an index. This version is defined by the
         * version that initially created the index.
         */
        val MIN_SUPPORTED_MAJOR: Int = LATEST.major - 1

        /**
         * @see .getPackageImplementationVersion
         */
        private var implementationVersion: String? = null

        /**
         * Parse a version number of the form `"major.minor.bugfix.prerelease"`.
         *
         *
         * Part `".bugfix"` and part `".prerelease"` are optional. Note that this is
         * forwards compatible: the parsed version does not have to exist as a constant.
         *
         * @lucene.internal
         */
        @Throws(ParseException::class)
        fun parse(version: String): Version {
            val tokens = StrictStringTokenizer(version, '.')
            if (tokens.hasMoreTokens() == false) {
                throw ParseException(
                    "Version is not in form major.minor.bugfix(.prerelease) (got: $version)", 0
                )
            }

            val major: Int
            var token: String = tokens.nextToken()
            try {
                major = token.toInt()
            } catch (nfe: NumberFormatException) {
                val p: ParseException =
                    ParseException(
                        "Failed to parse major version from \"$token\" (got: $version)", 0
                    )
                p.initCause(nfe)
                throw p
            }

            if (tokens.hasMoreTokens() == false) {
                throw ParseException(
                    "Version is not in form major.minor.bugfix(.prerelease) (got: $version)", 0
                )
            }

            val minor: Int
            token = tokens.nextToken()
            try {
                minor = token.toInt()
            } catch (nfe: NumberFormatException) {
                val p: ParseException =
                    ParseException(
                        "Failed to parse minor version from \"$token\" (got: $version)", 0
                    )
                p.initCause(nfe)
                throw p
            }

            var bugfix = 0
            var prerelease = 0
            if (tokens.hasMoreTokens()) {
                token = tokens.nextToken()
                try {
                    bugfix = token.toInt()
                } catch (nfe: NumberFormatException) {
                    val p: ParseException =
                        ParseException(
                            "Failed to parse bugfix version from \"$token\" (got: $version)", 0
                        )
                    p.initCause(nfe)
                    throw p
                }

                if (tokens.hasMoreTokens()) {
                    token = tokens.nextToken()
                    try {
                        prerelease = token.toInt()
                    } catch (nfe: NumberFormatException) {
                        val p: ParseException =
                            ParseException(
                                ("Failed to parse prerelease version from \""
                                        + token
                                        + "\" (got: "
                                        + version
                                        + ")"),
                                0
                            )
                        p.initCause(nfe)
                        throw p
                    }
                    if (prerelease == 0) {
                        throw ParseException(
                            ("Invalid value "
                                    + prerelease
                                    + " for prerelease; should be 1 or 2 (got: "
                                    + version
                                    + ")"),
                            0
                        )
                    }

                    if (tokens.hasMoreTokens()) {
                        // Too many tokens!
                        throw ParseException(
                            "Version is not in form major.minor.bugfix(.prerelease) (got: $version)", 0
                        )
                    }
                }
            }

            try {
                return Version(major, minor, bugfix, prerelease)
            } catch (iae: IllegalArgumentException) {
                val pe: ParseException =
                    ParseException(
                        "failed to parse version string \"" + version + "\": " + iae.message, 0
                    )
                pe.initCause(iae)
                throw pe
            }
        }

        /**
         * Parse the given version number as a constant or dot based version.
         *
         *
         * This method allows to use `"LUCENE_X_Y"` constant names, or version numbers in the
         * format `"x.y.z"`.
         *
         * @lucene.internal
         */
        @Throws(ParseException::class)
        fun parseLeniently(version: String): Version {
            var version = version
            val versionOrig: String = version
            version = version.uppercase()
            when (version) {
                "LATEST", "LUCENE_CURRENT" -> return LATEST
                else -> {
                    version =
                        version
                            .replaceFirst("^LUCENE_(\\d+)_(\\d+)_(\\d+)$".toRegex(), "$1.$2.$3")
                            .replaceFirst("^LUCENE_(\\d+)_(\\d+)$".toRegex(), "$1.$2.0")
                            .replaceFirst("^LUCENE_(\\d)(\\d)$".toRegex(), "$1.$2.0")
                    try {
                        return parse(version)
                    } catch (pe: ParseException) {
                        val pe2: ParseException =
                            ParseException(
                                ("failed to parse lenient version string \""
                                        + versionOrig
                                        + "\": "
                                        + pe.message),
                                0
                            )
                        pe2.initCause(pe)
                        throw pe2
                    }
                }
            }
        }

        /**
         * Returns a new version based on raw numbers
         *
         * @lucene.internal
         */
        fun fromBits(major: Int, minor: Int, bugfix: Int): Version {
            return Version(major, minor, bugfix)
        }

        val packageImplementationVersion: String
            /**
             * Return Lucene's full implementation version. This version is saved in Lucene's metadata at
             * build time (JAR manifest, module info). If it is not available, an `unknown`
             * implementation version is returned.
             *
             * @return Lucene implementation version string, never `null`.
             */
            get() {
                // Initialize the lazy value.
                if (implementationVersion == null) {
                    var version: String? = null

                    /* TODO following operation is not supported in Kotlin Multiplatform

                    val p: java.lang.Package = Version::class.java.getPackage()
                    version = p.getImplementationVersion()

                    if (version == null) {
                        val module: java.lang.Module = Version::class.java.getModule()
                        if (module.isNamed()) {
                            // Running as a module Try parsing the manifest manually.
                            try {
                                module.getResourceAsStream("/META-INF/MANIFEST.MF").use { `is` ->
                                    if (`is` != null) {
                                        val m: java.util.jar.Manifest = java.util.jar.Manifest(`is`)
                                        version = m.getMainAttributes().getValue("Implementation-Version")
                                    }
                                }
                            } catch (e: java.io.IOException) {
                                throw java.io.UncheckedIOException(e)
                            }
                        }
                    }*/

                    if (version == null) {
                        version = "unknown"
                    }

                    implementationVersion = version
                }
                return implementationVersion!!
            }
    }
}
