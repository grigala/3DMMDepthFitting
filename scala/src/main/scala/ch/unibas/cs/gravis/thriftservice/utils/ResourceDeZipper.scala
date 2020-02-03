package ch.unibas.cs.gravis.thriftservice.utils

import java.io.{File, PrintWriter}
import java.nio.file.Files

import scala.io.Source

/**
 * When program is a jar, resources are inside the jar (basically a zip file)
 * this allows to unpack resources, forexample scripts, that they can be executed
 */
class ResourceDeZipper {

    /**
     * Recursively unpacks resource content of dir to outDir.
     * Does not override already existing files.
     * @param dir resource folder, must start with /
     * @param outDir where to unpack to
     */
    def deZipRecursively(dir: String, outDir: File): Unit = {
        val content = getResourceFolderContent(dir)
        content.foreach{ f=>
            val res = dir + "/" + f.getName
            val target = new File(outDir.getAbsolutePath + File.separator + f.getName)
            if(f.isDirectory){
                deZipRecursively(res, target)
            }else{
                saveResource(res,target)
            }
        }
    }

    // does only work if in project containing the resource. Not if the resource is in a dependency jar.
    def getResourceFolderContent(dir: String): Seq[File] = {
        val path = getClass.getResource(dir)
        val folder = new File(path.getPath)
        if (folder.exists && folder.isDirectory){
            folder.listFiles.toSeq
        }else{
            Nil
        }
    }

    def saveResource(resource: String, outFile: File): Unit = {
        println(s"saving $resource to $outFile ...")
        if(!outFile.exists()) {
            outFile.getParentFile.mkdirs()
        }
        else{
            outFile.delete()
        }
        val fileStream = getClass.getResourceAsStream(resource)
        Files.copy(fileStream, outFile.toPath)
    }

    def listResourceContent(start: String): Seq[String] = {


        def getContent(start: String): Seq[String] = {
            val cont = getResourceFolderContent(start)
            cont.flatMap{f=>
                if(f.isDirectory && f.exists()){
                    getContent(start + "/" + f.getName)
                }else Seq(start + "/" + f.getName)
            }
        }

        getContent(start)
    }

    def deZip(contents: Seq[String], destination: File): Unit = {
        println(s"contents size: ${contents.size}")
        contents.foreach{f => {
            val dest = new File(destination.getAbsolutePath + File.separator + f.substring(1))
            println(f + " -> " + dest)
            saveResource(f, dest)
        }
        }
    }
}
