
import java.io._
import java.net._
import NameNode._
import java.util.Properties
import scala.collection.parallel._
object NameNode {
  def main(args: Array[String]) {
    try {
      val port: Int = 9800
      new NameNode(port)
    } catch {
      case e: NumberFormatException => println("Invalid port number. Please enter an integer.")
      case ae: ArrayIndexOutOfBoundsException => println("No port number entered. Please enter a port number")
    }
  }
}

class NameNode(port: Int) {
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
