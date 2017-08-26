package com.wire

import java.nio.file.{Files, Path, Paths}


/**
  * The problem with native libraries is, that Java's API to import
  * these kind of libraries is very limited. There is no way to
  * import the libraries from a resource stream or directly from a
  * resource. The library has to be located somewhere in the file
  * system. To solve the packaging and distribution issues that result
  * out of these limitations, the following approach has been chosen in
  * this project:
  * The native libraries are first extracted to a temporary path, which
  * is added to the java.library.path, so that the Java sources in
  * cryptobox-jni are able to find them.
  */
object JniHandler {
  def prepareNativeLibrariesPath() = {
    val tempDir = Files.createTempDirectory("com.wire.jni.JniHandler_nativeLibs")
    unsafeAddDirToJavasLibraryPath(tempDir)
    tempDir.toFile.deleteOnExit

    val libs = List[String]("cryptobox", "cryptobox-jni", "sodium")
    libs.foreach(lib => {
      val libName = System.mapLibraryName(lib)
      val is = getClass.getClassLoader.getResourceAsStream(libName)
      val targetFile = Paths.get(tempDir.toAbsolutePath.toString, libName);

      Files.copy(is, targetFile)
      targetFile.toFile.deleteOnExit()
    })

  }

  /* Hack to add an addition to java.library.path. For reference see:
     * http://www.scala-lang.org/old/node/7542.html#comment-31218
     */
  private def unsafeAddDirToJavasLibraryPath(path: Path)

  = try {
    val dir = path.toAbsolutePath.toString
    val field = classOf[ClassLoader].getDeclaredField("usr_paths")
    field.setAccessible(true)
    val paths = field.get(null).asInstanceOf[Array[String]]
    if (!(paths contains dir)) {
      field.set(null, paths :+ dir)
      System.setProperty("java.library.path",
        System.getProperty("java.library.path") +
          java.io.File.pathSeparator +
          dir)
    }
  } catch {
    case _: IllegalAccessException =>
      sys.error("Insufficient permissions; can't modify private variables.")
    case _: NoSuchFieldException =>
      sys.error("JVM implementation incompatible with path hack")
  }
}
