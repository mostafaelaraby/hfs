
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
import scala.collection.mutable.HashMap
import java.util.Properties
import java.io.FileReader
import java.util.Random
import java.security.MessageDigest
import scala.math.BigInt
import java.math.BigInteger
import java.io.FileWriter
import com.google.protobuf.ByteString
import scala.collection.JavaConversions._

object Connection {
  trait ServerReactor {

    def update: Unit
  }
}
  class file_data(in_ipp:String,idd:String,in_portt:Int,accesss:Int,md55:String){
    var file_id:String = idd
    var ip:String = in_ipp
    var port: Int = in_portt
    var allow_access: Int = accesss  //0 for not accessible and 1 for accessible
    var checksum: String = md55
    var Replicates = HashMap[Int, String]()
    var outToServer: DataOutputStream = null
    var clientSocket: Socket = null
    def set_file_id(id:String) 
    {
      file_id = id
    }
    def set_ip(in_ip:String) 
    {
      ip = in_ip
    }
    def set_port(in_port:Int) 
    {
      port = in_port
    }
    def set_access(access:Int) 
    {
      allow_access = access
    }
    def set_checksum(md5:String) 
    {
       checksum = md5
    }
      def get_file_id() :String =
    {
      return file_id
    }
    def get_ip() :String =
    {
      return ip
    }
    def get_port() :Int =
    {
      return port
    }
    def get_access() :Int =
    {
      return allow_access
    }
    def get_checksum() :String =
    {
      return checksum
    }
    def update_replicas(bytes: Array[Byte])
    {
      for(k <- Replicates.keySet.toArray)
        sendfile(bytes,Replicates(k).split(",",-1)(0).trim(), Replicates(k).split(",",-1)(1).trim().toInt)
      
    }
    def  replicate(bytes: Array[Byte],index:HashMap[Int, String])
    {
      //used to replicate the current file
      var i = 0;
      for(i<- 1 to Math.floor(index.size/2).toInt)
      {
        val randomNumbers = new Random
          val start = 1
          val end = index.size
          var number = start + randomNumbers.nextInt(end - start + 1)
        while(Replicates.contains(number) && (index(number).split(",",-1)(1).trim().toInt!=port || index(number).split(",",-1)(0).trim()!=ip)){
    	  val randomNumbers = new Random
          val start = 1
          val end = index.size
          number = start + randomNumbers.nextInt(end - start + 1)
        }
         
        sendfile(bytes,index(number).split(",",-1)(0).trim(),index(number).split(",",-1)(1).trim().toInt)
        Replicates+=(number -> (index(number)));
      }
    }
    def sendfile(bytes: Array[Byte],ipp:String,portt:Int){
       try {
    clientSocket = new Socket(ipp, portt)
    outToServer = new DataOutputStream(clientSocket.getOutputStream)
  } catch {
    case e: Exception =>
  }
        var message = Proto.protocole.newBuilder
                .setOperation("put")
                .setFileName(file_id)
                .build
            message.writeDelimitedTo(outToServer)
            var response2 = Proto.protocole.parseDelimitedFrom(clientSocket.getInputStream)
            if (response2.getResponse.startsWith("012 SENDFILE"))
            {
              //sending file
               try {
        var message = Proto.protocole.newBuilder
          .setData(ByteString.copyFrom(bytes))
          .setResponse(bytes.length + "")
          .setHash(checksum)
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
              //file sent
            }
      
    }
  }
class Connection(var clientSocket: Socket) extends Runnable {

  // var inFromClient: BufferedReader = _
  var outToClient: DataOutputStream = _
  var index = HashMap[Int, String]()
  var list_files = new HashMap[String, file_data] { override def default(key: String) = new file_data("","",0,0,"") } //the string value holds the port and ip and the hash separated by commas
  var props = new Properties
  props.load(new FileReader("index.ini"))
  for (k <- props.keySet.toArray)
    index += (k.toString.toInt -> props.getProperty(k.toString))
  props = new Properties
  props.load(new FileReader("namenode.ini"))
  for (k <- props.keySet.toArray)
    list_files += (k.toString -> (new file_data(props.getProperty(k.toString).split(",",-1)(0).trim(),k.toString,props.getProperty(k.toString).split(",",-1)(1).trim().toInt,1,props.getProperty(k.toString).split(",",-1)(2).trim())))
  try {
    // inFromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream))
    outToClient = new DataOutputStream(clientSocket.getOutputStream)
  } catch {
    case e: Exception =>
  }
  var state: ServerReactor = new Serverler

  def run {
    println("Client connected @ " + clientSocket.getInetAddress)
    while (!clientSocket.isClosed) {
      try {
        state.update
      } catch {
        case e: Exception => e.printStackTrace
      }
    }
  }
  


  private class Serverler extends ServerReactor {

    def update {
      try {
        var command = Proto.protocole.parseDelimitedFrom(clientSocket.getInputStream)
        println(command.getOperation + " " + command.getFileName + " From: " + clientSocket.getInetAddress)
        if (command.getOperation == "exit") {
          state = new Disconnect
        } else if (command.getOperation == "ls") {
          state = new ListRemoteFiles
        } else if (command.getOperation.startsWith("get")) {
          state = new GetFile(command.getFileName.trim)
        } else if (command.getOperation.startsWith("put")) {
          state = new PutFile(command.getFileName.trim)
        } else if (command.getOperation.startsWith("rr")) {
          state = new DeleteFile(command.getFileName.trim)
        } else {
          var message = Proto.protocole.newBuilder
            .setResponse("Command " + command.getOperation + " not recognised. Type help for a list of valid server commands")
            .build
          message.writeDelimitedTo(outToClient)
          // outToClient.writeBytes("Command " + command.getOperation + " not recognised. Type help for a list of valid server commands\n")
        }
      } catch {
        case e: IOException => e.printStackTrace
      }
    }
  }

  private class Disconnect extends ServerReactor {

    def update {
      try {
        Thread.sleep(4000)
        clientSocket.close
      } catch {
        case e: IOException => e.printStackTrace
        case e: InterruptedException => e.printStackTrace
      }
    }
  }

  private class ListRemoteFiles extends ServerReactor {

    def update {
      var props = new Properties
      props.load(new FileReader("namenode.ini"))
      var output = ""
      for (k <- props.keySet.toArray) {
        output += k + "\t" + props.getProperty(k.toString).split(",")(0).trim + "\t" + props.getProperty(k.toString).split(",")(1).trim + "-"
      }
      try {
        //outToClient.writeBytes("021 REMOTEFILELIST\n")
        var message = Proto.protocole.newBuilder
          .setFileList(output)
          .build
        message.writeDelimitedTo(outToClient)
        //outToClient.writeBytes(output)
        //outToClient.writeBytes("022 ENDOFFILELIST\n")
      } catch {
        case e: IOException => e.printStackTrace
      }
      state = new Serverler
    }
  }

  private class PutFile(var fileName: String) extends ServerReactor {
    var checksum: String = ""
    def getchecksum_data : Array[Byte]={
      try {
        val fileExists = Proto.protocole.parseDelimitedFrom(clientSocket.getInputStream)
        if (!fileExists.getResponse.startsWith("011 FILE NOT FOUND")) {
          val fileSize = java.lang.Integer.parseInt(fileExists.getResponse)
          val mybytearray = Array.ofDim[Byte](fileSize)
          //outToClient.writeBytes("111 FILESIZEOK\n")
          // var bytesRead = 0
          var md: MessageDigest = MessageDigest.getInstance("MD5")

          var message = Proto.protocole.newBuilder
            .setResponse("111 FILESIZEOK")
            .build
          message.writeDelimitedTo(clientSocket.getOutputStream)
          // while (bytesRead < fileSize - 1) {
          //bytesRead += is.read(mybytearray, bytesRead, mybytearray.length - bytesRead)
          fileExists.getData.copyTo(mybytearray, 0)
          md.update(mybytearray, 0, fileSize)
          var hash = md.digest;
          checksum = new BigInteger(1, hash).toString(16); //don't use this, truncates leading zero
          return mybytearray
          // }
        } else {
          throw new FileNotFoundException
        }
      } catch {
        case e: FileNotFoundException => println("File not found on client.")
        return null
        case e: Exception => e.printStackTrace
        return null
      }
    }
    def update {
      try {
        var flag_send = true
        if (list_files.contains(fileName)) {
          /*
       var messsage = Proto.protocole.newBuilder
       .setResponse("050 There's another file with same name")
       .build
       messsage.writeDelimitedTo(outToClient)*/
          if (list_files(fileName).get_access == 0) {
            var message = Proto.protocole.newBuilder
              .setResponse("051 Access Denied")
              .build
            message.writeDelimitedTo(outToClient)
            flag_send = false
            //outToClient.writeBytes("051 Access Denied\n")//in case we added write
          } else {
            //add flag to the file as being locked so nobody can read it or write to it
            var temp = list_files(fileName)
            temp.set_access(0)
            list_files(fileName) = temp
            flag_send = true
          }
        }
        // outToClient.writeBytes("050 There's another file with same name\n")
        if (flag_send) {
          var message = Proto.protocole.newBuilder
            .setResponse("012 SENDFILE")
            .setFileName(fileName)
            .build
          message.writeDelimitedTo(outToClient)
          //outToClient.writeBytes("012 SENDFILE " + fileName + "\n")
         var file_received_bytes:Array[Byte] = getchecksum_data
          val randomNumbers = new Random
          val start = 1
          val end = index.size
          val number = start + randomNumbers.nextInt(end - start + 1)
          if (list_files.contains(fileName))
          {
            var temp = list_files(fileName)
            temp.set_checksum(this.checksum)
            list_files(fileName) = temp
           list_files(fileName).update_replicas(file_received_bytes)
          }
          else
          {
        	  list_files += (fileName -> (new file_data(index(number).split(",",-1)(0).trim(),fileName,index(number).split(",",-1)(1).trim().toInt,0,this.checksum)))
        	 list_files(fileName).replicate(file_received_bytes,index)
          }
          val fw = new FileWriter("namenode.ini", true)
          try {
            fw.write("\n" + fileName + "=" + (index(number) + " ," + this.checksum))
          } finally fw.close
          message = Proto.protocole.newBuilder
            .setResponse("010 FILE FOUND")
            .setFileName(fileName)
            .setIp(list_files(fileName).get_ip())
            .setPort(list_files(fileName).get_port())
            .setHash(this.checksum)
            .build
          message.writeDelimitedTo(outToClient)
          Thread.sleep(100)
            var temp = list_files(fileName)
            temp.set_access(1)
            list_files(fileName) = temp
            
          //outToClient.writeBytes("010 FILE FOUND\n")
          //outToClient.writeBytes(list_files(fileName)+"\n")
        }
      } catch {
        case e: IOException => e.printStackTrace
      }
      state = new Serverler
    }
  }

  private class GetFile(var fileName: String) extends ServerReactor {
    def update {
      //send the address of the needed file to the client
      try {
        var message: Proto.protocole = null
        if (!list_files.contains(fileName)) {
          message = Proto.protocole.newBuilder
            .setResponse("011 FILE NOT FOUND")
            .build
          message.writeDelimitedTo(outToClient)
          //outToClient.writeBytes("011 FILE NOT FOUND\n")
        } else if (list_files(fileName).get_access() == 0) {
          message = Proto.protocole.newBuilder
            .setResponse("051 Access Denied")
            .build
          message.writeDelimitedTo(outToClient)
          //outToClient.writeBytes("051 Access Denied\n")//in case we added write
        } else {
          message = Proto.protocole.newBuilder
            .setResponse("010 FILE FOUND")
            .setIp(list_files(fileName).get_ip())
            .setPort(list_files(fileName).get_port())
            .setHash(list_files(fileName).get_checksum())
            .build
          message.writeDelimitedTo(outToClient)
          // outToClient.writeBytes("010 FILE FOUND\n")
          //outToClient.writeBytes(list_files(fileName)+"\n")
        }
      } catch {
        case e: IOException => e.printStackTrace
      }
      state = new Serverler
    }
  }
  private class DeleteFile(var fileName: String) extends ServerReactor {
    def update {
      //send the address of the needed file to the client
      try {
        var message: Proto.protocole = null
        if (!list_files.contains(fileName)) {
          message = Proto.protocole.newBuilder
            .setResponse("011 FILE NOT FOUND")
            .build
          message.writeDelimitedTo(outToClient)
        } else if (list_files(fileName).get_access()== 0) {
          message = Proto.protocole.newBuilder
            .setResponse("051 Access Denied")
            .build
          message.writeDelimitedTo(outToClient)
        } else {
          message = Proto.protocole.newBuilder
            .setResponse("010 FILE FOUND")
            .setIp(list_files(fileName).get_ip())
            .setPort(list_files(fileName).get_port())
            .setHash(list_files(fileName).get_checksum())
            .build
          message.writeDelimitedTo(outToClient)
          val randomNumbers = new Random
          val start = 1
          val end = index.size
          val number = start + randomNumbers.nextInt(end - start + 1)
          if (list_files.contains(fileName)) {
            list_files.remove(fileName)
            props.load(new FileReader("namenode.ini"))
            props.remove(fileName)
            props.store(new FileOutputStream("namenode.ini"), "filelist")
          }
        }
      } catch {
        case e: IOException => e.printStackTrace
      }
      state = new Serverler
    }
  }
}
