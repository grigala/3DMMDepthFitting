package ch.unibas.cs.gravis.thriftservice.server

import java.io.File

import com.twitter.logging.Logger

import scala.sys.process._

class DepthCameraServer(serverScript: File, ip: String = "127.0.0.1", port: Int = 9000) {
    private val logger: Logger = Logger.get("depth-camera-server")
    def startCameraServer(): Boolean = {
        // TODO Research how this process execution is happening...
        // FIX: Server logs are not visible I'm guessing there must be a ProcessLogger attached to this call...
        val serverProc: Process = s"python3 ${serverScript.getAbsolutePath} $ip $port".run()
        logger.info("Server process has started...")
        Thread.sleep(500)
        if (serverProc.isAlive())
            true
        else
            false
    }

}
