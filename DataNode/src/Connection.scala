
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.Socket
import java.util.ArrayList
import Connection._
import com.sun.org.apache.xalan.internal.xsltc.compiler.StartsWithCall
import com.google.protobuf.ByteString
import java.security.MessageDigest
import java.math.BigInteger

object Connection {

  trait ServerReactor {
    def update(): Unit
  }
}

class Connection(var clientSocket: Socket) extends Runnable {

  // var inFromClient: BufferedReader = _

  var outToClient: DataOutputStream = _

  var state: ServerReactor = new Serverler()

  try {
    // inFromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream))
    outToClient = new DataOutputStream(clientSocket.getOutputStream)
  } catch {
    case e: Exception =>
  }

  def run() {
    println("Client connected @ " + clientSocket.getInetAddress)
    while (!clientSocket.isClosed) {
      try {
        state.update()
      } catch {
        case e: Exception =>
      }
    }
  }

  private class Serverler extends ServerReactor {

    def update() {
      try {
        var command = Proto.protocole.parseDelimitedFrom(clientSocket.getInputStream())
        if (command.getOperation().startsWith("exit")) {
          state = new Disconnect()
        } /*else if (command == "ls") {
          state = new ListRemoteFiles()
        }*/else if(command.getOperation.startsWith("rr")){
        	state = new DeleteLocalFile(command.getFileName())
        } else if (command.getOperation().startsWith("get")) {
          state = new GetFile(command.getFileName().trim())
        } else if (command.getOperation().startsWith("put")) {
          state = new PutFile(command.getFileName().trim())
        } else {
          var message = Proto.protocole.newBuilder()
            .setResponse("Command " + command.getOperation() + " not recognised. Type help for a list of valid server commands")
            .build()
          message.writeDelimitedTo(outToClient)
          // outToClient.writeBytes("Command " + command + " not recognised. Type help for a list of valid server commands\n")
        }
      } catch {
        case e: IOException => e.printStackTrace()
      }
    }
  }

  private class Disconnect extends ServerReactor {

    def update() {
      try {
        Thread.sleep(4000)
        clientSocket.close()
      } catch {
        case e: IOException => e.printStackTrace()
        case e: InterruptedException => e.printStackTrace()
      }
    }
  }
  /*
  private class ListRemoteFiles extends ServerReactor {

    def update() {
      val path = "data"
      val dir = new File(path)
      val list = dir.list()
      var output = ""
      for (fileNo <- 0 until list.length) {
        output += list(fileNo) + "\n"
      }
      output += "\n"
      try {
        outToClient.writeBytes("021 REMOTEFILELIST\n")
        outToClient.writeBytes(output)
        outToClient.writeBytes("022 ENDOFFILELIST\n")
      } catch {
        case e: IOException => e.printStackTrace()
      }
      state = new Serverler()
    }
  }
*/
  private class PutFile(var fileName: String) extends ServerReactor {

    def update() {
      try {
        // outToClient.writeBytes("012 SENDFILE " + fileName + "\n")
        var message = Proto.protocole.newBuilder()
          .setResponse("012 SENDFILE")
          .setFileName(fileName)
          .build()
        message.writeDelimitedTo(outToClient)
        val fileExists = Proto.protocole.parseDelimitedFrom(clientSocket.getInputStream())
        if (fileExists.getResponse() != "011 FILE NOT FOUND") {
          val fileSize = java.lang.Integer.parseInt(fileExists.getResponse())

          val mybytearray = Array.ofDim[Byte](fileSize)
          // val is = new DataInputStream(clientSocket.getInputStream)
          val fos = new FileOutputStream(fileName)
          val bos = new BufferedOutputStream(fos)
          var message = Proto.protocole.newBuilder()
            .setResponse("111 FILESIZEOK")
            .build()
          message.writeDelimitedTo(outToClient)
          // outToClient.writeBytes("111 FILESIZEOK\n")
          fileExists.getData().copyTo(mybytearray, 0)
          // var bytesRead = 0
          //while (bytesRead < fileSize - 1) {
          //bytesRead += is.read(mybytearray, bytesRead, mybytearray.length - bytesRead)
          //}
          bos.write(mybytearray, 0, fileSize)
          bos.close()
          var md: MessageDigest = MessageDigest.getInstance("MD5")
          md.update(mybytearray, 0, fileSize)
          var hash = md.digest();
          var checksum = new BigInteger(1, hash).toString(16); //don't use this, truncates leading zero
          if (checksum.toLowerCase().equals(fileExists.getHash().toLowerCase()))
            println("File recieved from server.")
          else
            println("The file received is corrupted based on the checksum! original: " + fileExists.getHash().toLowerCase() + " New: " + checksum.toLowerCase())
        } else {
          throw new FileNotFoundException()
        }
      } catch {
        case e: FileNotFoundException => println("File not found on client.")
        case e: Exception => e.printStackTrace()
      }
      state = new Serverler()
    }
  }
  private class DeleteLocalFile(filename: String) extends ServerReactor {
    def update {
      try {
        val x = new CRUD().rm(filename)
        Proto.protocole.newBuilder().setFileName(filename).setOperation("delete").setResponse("1").build().writeDelimitedTo(outToClient)
        println(filename + " Was Deleted")
      } catch {
        case t: IOException => println("")
        case h: FileNotFoundException =>
          println("File Not Found... ")
          h.printStackTrace
      }
      state = new Serverler
    }
  }
  private class GetFile(var fileName: String) extends ServerReactor {

    def update() {
      try {

        var message = Proto.protocole.newBuilder()
          .setResponse("013 RECIEVEFILE")
          .setFileName(fileName)
          .build()
        message.writeDelimitedTo(outToClient)
        //outToClient.writeBytes("013 RECIEVEFILE " + fileName + "\n")
        val myFile = new File(fileName)
        if (!myFile.exists()) {
          throw new FileNotFoundException()
        }
        /*
         message = Proto.protocole.newBuilder()
          .setResponse(myFile.length +"")
          .build()
          message.writeDelimitedTo(outToClient)*/
        //outToClient.writeBytes(myFile.length + "\n")
        val fileBytes = Array.ofDim[Byte](myFile.length.toInt)
        val bufferedInFromFile = new BufferedInputStream(new FileInputStream(myFile))
        bufferedInFromFile.read(fileBytes, 0, myFile.length.toInt)
        bufferedInFromFile.close()
        // Proto.protocole.parseDelimitedFrom(clientSocket.getInputStream())
        //inFromClient.readLine()
        message = Proto.protocole.newBuilder()
          .setFileName(fileName)
          .setData(ByteString.copyFrom(fileBytes))
          .setResponse(myFile.length + "")
          .build()
        message.writeDelimitedTo(outToClient)

        Thread.sleep(100)
        //outToClient.write(fileBytes, 0, fileBytes.length)
        //outToClient.flush()
        println("File sent to client")
      } catch {
        case e: FileNotFoundException => try {
          var message = Proto.protocole.newBuilder()
            .setResponse("011 FILE NOT FOUND")
            .build()
          message.writeDelimitedTo(outToClient)
        } catch {
          case e1: IOException =>
        }
        case e: IOException =>
        case e: InterruptedException => e.printStackTrace()
      }
      state = new Serverler
    }
  }
}
