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
import Client._
import com.sun.org.apache.xalan.internal.xsltc.compiler.StartsWithCall
import com.google.protobuf.ByteString
import java.security.MessageDigest
import java.math.BigInteger

object Client {

  def main(args: Array[String]) {
    try {
      //      val port = java.lang.Integer.parseInt(args(1))
      //      new Client(args(0), port)
      val port = 9800 //3131
      new Client("localhost", port)

    } catch {
      case e: NumberFormatException => println("Invalid port number. Please enter an integer.")
    }
  }

  trait ClientState {
    def update: Unit
  }
}

class Client(host: String, port: Int) {

  var outToServer: DataOutputStream = _

  var inFromUser: BufferedReader = new BufferedReader(new InputStreamReader(System.in))

  var clientSocket: Socket = _

  var state: ClientState = new Authorised

  try {
    clientSocket = new Socket(host, port)
    outToServer = new DataOutputStream(clientSocket.getOutputStream)

  } catch {
    case e: Exception =>
  }

  run

  private def run {
    if (clientSocket != null) {
      while (!clientSocket.isClosed) {
        try {
          state.update
        } catch {
          case e: Exception => e.printStackTrace
        }
      }
    } else {
      println("Could not connect to server, check host name and port number, then try again.")
    }
  }

  private class Authorised extends ClientState {
    def sendfile(fileName: String) {
      try {
        val myFile = new File(fileName)
        if (!myFile.exists) {
          throw new FileNotFoundException
        }
        val fileBytes = Array.ofDim[Byte](myFile.length.toInt)
        val bufferedInFromFile = new BufferedInputStream(new FileInputStream(myFile))
        bufferedInFromFile.read(fileBytes, 0, fileBytes.length)
        bufferedInFromFile.close
        var message = Proto.protocole.newBuilder
          .setData(ByteString.copyFrom(fileBytes))
          .setResponse(myFile.length + "")
          .build;
        message.writeDelimitedTo(outToServer)
        Proto.protocole.parseDelimitedFrom(clientSocket.getInputStream)
      } catch {
        case e: FileNotFoundException => try {
          println("File not found in local folder.")
        } catch {
          case e1: IOException =>
        }
        case e: IOException =>
        case e: InterruptedException => e.printStackTrace
      }
    }
    def update {
      try {
        var command = inFromUser.readLine
        var message: Proto.protocole = null
        while (command == "")
          command = inFromUser.readLine
        if (command == "lls") {
          state = new ListLocalFiles
        } else if (command.startsWith("rm ")) {
          state = new DeleteLocalFile(command.split(" ")(1))
        } else if (command == "exit") {
          state = new Disconnect
          message = Proto.protocole.newBuilder
            .setOperation(command)
            .setFileName(" ")
            .build;
          message.writeDelimitedTo(outToServer)
        } else {
          //get the address of the data from the namenode first
          var commands = command.split(" ", -1)
          if (commands.length > 1) {
            message = Proto.protocole.newBuilder
              .setOperation(commands(0).trim)
              .setFileName(commands(1).trim)
              .build;
            message.writeDelimitedTo(outToServer)
          } else {
            message = Proto.protocole.newBuilder
              .setOperation(commands(0).trim)
              .setFileName(" ")
              .build;
            message.writeDelimitedTo(outToServer)
          }
          var response = Proto.protocole.parseDelimitedFrom(clientSocket.getInputStream) //data received from Name Node
          var Hash: String = ""
          println(response.getResponse)
          if (response.getResponse.startsWith("111 FILESIZEOK"))
            response = Proto.protocole.parseDelimitedFrom(clientSocket.getInputStream)
          
          if (response.getResponse.startsWith("012 SENDFILE")) {
            this.sendfile(response.getFileName)
            response = Proto.protocole.parseDelimitedFrom(clientSocket.getInputStream)
          }
          if (response.getResponse.startsWith("011 FILE NOT FOUND"))
            println("Remote file does not exist.")
          else if (response.getResponse.startsWith("050 There's another file with same name"))
            println("There's a file with the same name you are trying to put on datanode")
          else if (response.getResponse.startsWith("051 Access Denied"))
            println("Access denied another user is writing to the file!")
          else if (response.getResponse.startsWith("010 FILE FOUND")) {
            ///get the new port and the ip address of the requested file
            var received_host = response.getIp
            var received_port = response.getPort
            Hash = response.getHash
            try {
              clientSocket = new Socket(received_host, received_port)
              outToServer = new DataOutputStream(clientSocket.getOutputStream)
            } catch {
              case e: Exception =>
            }
            var commands = command.split(" ", -1)
            if (commands.length > 1) {
              message = Proto.protocole.newBuilder
                .setOperation(commands(0))
                .setFileName(commands(1))
                .build
            } else {
              message = Proto.protocole.newBuilder
                .setOperation(commands(0))
                .setFileName(" ")
                .build
            }
            message.writeDelimitedTo(outToServer)
            var response2 = Proto.protocole.parseDelimitedFrom(clientSocket.getInputStream)
            if (response2.getResponse.startsWith("011 FILE NOT FOUND"))
              println("Remote file does not exist.")
            else if (response2.getResponse.startsWith("012 SENDFILE")) {
              state = new SendFile(response2.getFileName, Hash.trim)
            } else if (response2.getResponse.startsWith("013 RECIEVEFILE")) {
              state = new RecieveFile(response2.getFileName, Hash.trim)
            } else if (response2.getResponse.startsWith("011 FILE NOT FOUND")) {
              println("Remote file does not exist.")
            } else if (response2.getResponse.startsWith("021 REMOTEFILELIST")) {
              state = new ListRemoteFiles
            }
          }

        }
      } catch {
        case e: IOException => e.printStackTrace
      }
    }
  }

  private class Disconnect extends ClientState {

    def update {
      try {
        println("Closing conncetion")
        Thread.sleep(1000)
        clientSocket.close
      } catch {
        case e: IOException => println("IO Error, please check connection to server")
        case e: InterruptedException =>
      }
    }
  }

  private class ListLocalFiles extends ClientState {

    def update {
      val localPath = "."
      val dir = new File(localPath)
      val list = dir.list
      var output = ""
      for (i <- 0 until list.length) {
        output += list(i) + "\n"
      }
      output += "\n"
      System.out.print(output)
      state = new Authorised
    }
  }
  private class DeleteLocalFile(filename: String) extends ClientState {

    def update {
      try {
        val x =new CRUD().rm(filename)
        println(filename + " Was Deleted")
      } catch {
        case t: IOException => println("")
        case h: FileNotFoundException =>
          println("File Not Found... ")
          h.printStackTrace
      }
      state = new Authorised
    }
  }

  private class ListRemoteFiles extends ClientState {

    def update {
      try {
        var file = Proto.protocole.parseDelimitedFrom(clientSocket.getInputStream)
        var files = file.getFileList.split("-", -1)
        var cnt = 0
        do {
          println(files(cnt))
          cnt = cnt + 1
        } while (cnt < files.length)
      } catch {
        case e: IOException => e.printStackTrace
      }
      state = new Authorised
    }
  }

  private class DeleteRemoteFiles(command: String) extends ClientState {
    def update {
      try {
        println("ANA HENA")
        var filename = command.split(" ")(1)
        var outdata = Proto.protocole.newBuilder().setOperation(command).setFileName(filename)
          .build().writeDelimitedTo(outToServer)
        var data = Proto.protocole.parseDelimitedFrom(clientSocket.getInputStream)
        var response = data.getResponse
      } catch {
        case e: IOException => e.printStackTrace
      }
      state = new Authorised
    }
  }
  private class SendFile(var fileName: String, var Hash: String) extends ClientState {

    def update {
      try {
        val myFile = new File(fileName)
        if (!myFile.exists) {
          throw new FileNotFoundException
        }
        val fileBytes = Array.ofDim[Byte](myFile.length.toInt)
        val bufferedInFromFile = new BufferedInputStream(new FileInputStream(myFile))
        bufferedInFromFile.read(fileBytes, 0, fileBytes.length)
        bufferedInFromFile.close
        var message = Proto.protocole.newBuilder
          .setData(ByteString.copyFrom(fileBytes))
          .setResponse(myFile.length + "")
          .setHash(Hash)
          .build;
        message.writeDelimitedTo(outToServer)
        Thread.sleep(100)
        println("File sent to server")
      } catch {
        case e: FileNotFoundException => try {
          var message = Proto.protocole.newBuilder
            .setResponse("011 FILE NOT FOUND")
            .build;
          message.writeDelimitedTo(outToServer)
          println("File not found in local folder.")
        } catch {
          case e1: IOException =>
        }
        case e: IOException =>
        case e: InterruptedException => e.printStackTrace
      }
      state = new Authorised
    }
  }

  private class RecieveFile(var fileName: String, var Hash: String) extends ClientState {

    def update {
      try {
        val fileExists = Proto.protocole.parseDelimitedFrom(clientSocket.getInputStream) //inFromServer.readLine
        if (!fileExists.getResponse.startsWith("011 FILE NOT FOUND")) {
          val fileSize = fileExists.getResponse.toInt
          val mybytearray = Array.ofDim[Byte](fileSize)
          val fos = new FileOutputStream(fileName)
          val bos = new BufferedOutputStream(fos)
          var message = Proto.protocole.newBuilder
            .setResponse("111 FILESIZEOK")
            .build;
          message.writeDelimitedTo(outToServer)
          fileExists.getData.copyTo(mybytearray, 0) //need to be tested
          bos.write(mybytearray, 0, fileSize)
          bos.close
          var md: MessageDigest = MessageDigest.getInstance("MD5")
          md.update(mybytearray, 0, fileSize)
          var received_hash = md.digest;
          var checksum: String = new BigInteger(1, received_hash).toString(16); //don't use this, truncates leading zero
          if (checksum.toLowerCase.equals(Hash.toLowerCase))
            println("File recieved from server.")
          else
            println("The file received is corrupted based on the checksum! original: " + Hash.toLowerCase + " New: " + checksum.toLowerCase)
        } else {
          throw new FileNotFoundException
        }
      } catch {
        case e: FileNotFoundException => println("File not found on remote server.")
        case e: Exception => e.printStackTrace
      }
      state = new Authorised
    }
  }
}
