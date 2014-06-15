import scalax.file.{ Path, PathSet, FileOps }

class CRUD(private val root:String = "") {
  /**
   * Creates files
   */
  def touch(filePath: String) {
    val path: Path = Path(root +"/" + filePath)
    // create file but fail if the file already exists.
    // an exception may be thrown
    try {
      path.createFile()
    } catch {
      case t => System.err.println("File or Directory Already Exist: " + t.printStackTrace());
    }
  }
  /**
   * Creates directories
   */
  def mkdir(filePath: String) {
    var path = Path(root +"/" +filePath)
    try {
      path.createDirectory()
    } catch {
      case t => System.err.println("File or Directory Already Exist: " + t.printStackTrace());
    }
  }

  /**
   * Delete files and directories
   */
  def rm(filePath: String, recurisve: Boolean = false) {

    val path: Path = Path(filePath)
    if (!recurisve)
      try {
        path.deleteIfExists()
      } catch {
        case t => System.err.println("File or folder isn't writable: " + t.printStackTrace());
      }
    else
      path.deleteRecursively(true)
  }
  /**
   * Lists files and directories
   */
  def ls(filePath: String = root) {
    val Files: PathSet[Path] = Path(filePath) * "*.*"
    val myFileSet: Set[Path] = Files.toSet
    Files.toSet.toList
  }
  /**
   * Open a File with locking it for reading
   */

  def read(filePath: String): Array[Byte] = {
    val file: FileOps = Path(root +"/" +filePath)
    //    
    //    // By default the entire file is locked with exclusive access
    //    val result: Option[Array[Byte]] = file.withLock(shared = true) {
    //      s => s.byteArray
    //    }
    file.byteArray
  }

  def create(filePath: String, data: Array[Byte]) {
    val file: FileOps = Path(root +"/" +filePath)
    //	 // By default the entire file is locked with exclusive access
    //    file.withLock(shared = false) { s =>
    //      s.write(data)
    //    }
    file.write(data)
  }

  def cp(path: Path, dest: Path) {
    import scalax.file.Path

    // make a copy of the file
    // by default this will fail if dest already exists
    // also attribute information like datestamp will be
    // set on the destination file
    // If path is a directory the copy will not be recursive
    path.copyTo(dest)

    // Copy explicitly declaring options
    path.copyTo(target = dest,
      copyAttributes = true,
      replaceExisting = true)
  }

  def mv(path: Path, dest: Path) {
    // Move/Rename the path
    // by default throw exception if destination exists
    // and if a copy is required by underlying filesystem then do that
    path.moveTo(target = dest)

    // Here we will overwrite existing files (but not non-empty directories)
    // and will fail if a copy is required (similar to java.file.File.renameTo)
    // if a failure occures an exception is thrown
    path.moveTo(target = dest,
      replace = true,
      atomicMove = true)
  }
}

