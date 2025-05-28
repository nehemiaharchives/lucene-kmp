package org.gnit.lucenekmp.util

import okio.Path
import org.gnit.lucenekmp.store.Directory;
import org.gnit.lucenekmp.store.FSDirectory;
import org.gnit.lucenekmp.store.FSLockFactory;
import org.gnit.lucenekmp.store.LockFactory;
import org.gnit.lucenekmp.store.NIOFSDirectory
import kotlin.jvm.JvmOverloads
import kotlin.reflect.KClass

/** Class containing some useful methods used by command line tools  */
object CommandLineUtil {
    /**
     * Creates a specific FSDirectory instance starting from its class name
     *
     * @param clazzName The name of the FSDirectory class to load
     * @param path The path to be used as parameter constructor
     * @param lf The lock factory to be used
     * @return the new FSDirectory instance
     */
    fun newFSDirectory(clazzName: String, path: Path): FSDirectory {
        return newFSDirectory(clazzName, path, FSLockFactory.default)
    }

    /**
     * Creates a specific FSDirectory instance starting from its class name, using the default lock
     * factory
     *
     * @param clazzName The name of the FSDirectory class to load
     * @param path The path to be used as parameter constructor
     * @return the new FSDirectory instance
     */
    fun newFSDirectory(
        clazzName: String,
        path: Path,
        lf: LockFactory = FSLockFactory.default
    ): FSDirectory {
        /*try {
            val clazz: KClass<out FSDirectory> = loadFSDirectoryClass(clazzName)
            return newFSDirectory(clazz, path, lf)
        } catch (e: ClassNotFoundException) {
            throw IllegalArgumentException(
                FSDirectory::class.simpleName + " implementation not found: " + clazzName, e
            )
        } catch (e: KClassCastException) {
            throw IllegalArgumentException(
                clazzName + " is not a " + FSDirectory::class.simpleName + " implementation", e
            )
        } catch (e: NoSuchMethodException) {
            throw IllegalArgumentException(
                clazzName + " constructor with " + Path::class.simpleName + " as parameter not found",
                e
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("Error creating $clazzName instance", e)
        }*/

        return newFSDirectory(
            NIOFSDirectory::class, // we have ported only this one so far
            path,
            lf
        )
    }

    /**
     * Loads a specific Directory implementation
     *
     * @param clazzName The name of the Directory class to load
     * @return The Directory class loaded
     * @throws ClassNotFoundException If the specified class cannot be found.
     */
    fun loadDirectoryClass(clazzName: String): KClass<out Directory> {
        /*return KClass.forName(adjustDirectoryClassName(clazzName))
            .asSubclass<Directory>(Directory::class)*/

        return NIOFSDirectory::class // we have ported only this one so far
    }

    /**
     * Loads a specific FSDirectory implementation
     *
     * @param clazzName The name of the FSDirectory class to load
     * @return The FSDirectory class loaded
     * @throws ClassNotFoundException If the specified class cannot be found.
     */
    fun loadFSDirectoryClass(clazzName: String): KClass<out FSDirectory> {
        /*return KClass.forName(adjustDirectoryClassName(clazzName))
            .asSubclass<FSDirectory>(FSDirectory::class)*/

        return NIOFSDirectory::class // we have ported only this one so far
    }

    private fun adjustDirectoryClassName(clazzName: String): String {

        // no operation because lucene-kmp does not have capability to load classes by name, needs ClassLoader walk around

        /*var clazzName = clazzName
        require(!(clazzName == null || clazzName.trim { it <= ' ' }.length == 0)) { "The " + FSDirectory::class.simpleName + " implementation must not be null or empty" }

        if (clazzName.indexOf('.') == -1) { // if not fully qualified, assume .store
            clazzName = Directory::class.getPackage().getName() + "." + clazzName
        }*/
        return clazzName
    }

    /**
     * Creates a new specific FSDirectory instance
     *
     * @param clazz The class of the object to be created
     * @param path The file to be used as parameter constructor
     * @param lf The lock factory to be used
     * @return The new FSDirectory instance
     * @throws NoSuchMethodException If the Directory does not have a constructor that takes `
     * Path`.
     * @throws InstantiationException If the class is abstract or an interface.
     * @throws IllegalAccessException If the constructor does not have public visibility.
     * @throws InvocationTargetException If the constructor throws an exception
     */
    /**
     * Creates a new specific FSDirectory instance
     *
     * @param clazz The class of the object to be created
     * @param path The file to be used as parameter constructor
     * @return The new FSDirectory instance
     * @throws NoSuchMethodException If the Directory does not have a constructor that takes `
     * Path`.
     * @throws InstantiationException If the class is abstract or an interface.
     * @throws IllegalAccessException If the constructor does not have public visibility.
     * @throws InvocationTargetException If the constructor throws an exception
     */
    @JvmOverloads
    fun newFSDirectory(
        clazz: KClass<out FSDirectory>,
        path: Path,
        lf: LockFactory = FSLockFactory.default
    ): FSDirectory {
        // Assuming every FSDirectory has a ctor(Path):
        /*val ctor: Constructor<out FSDirectory> =
            clazz.getConstructor(Path::class, LockFactory::class)
        return ctor.newInstance(path, lf)*/

        // we have ported only this one so far
        return NIOFSDirectory(path, lf)
    }
}
