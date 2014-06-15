
import java.io._
import java.net._
import DataNode._

object DataNode {

  def main(args: Array[String]) {
    try {
      val port: Int = 3131
      new DataNode(port)
    } catch {
      case e: NumberFormatException => println("Invalid port number. Please enter an integer.")
      case ae: ArrayIndexOutOfBoundsException => println("No port number entered. Please enter a port number")
    }
  }
}

class DataNode(port: Int) {
  try {
    val connectionSocket = new ServerSocket(port)
    println("Server Running")
    while (true) {
      val clientSocket = connectionSocket.accept()
      val con = new Connection(clientSocket)
      (new Thread(con)).start()
    }
  } catch {
    case e: SocketException =>
    case e: IOException => e.printStackTrace()
  }
}
