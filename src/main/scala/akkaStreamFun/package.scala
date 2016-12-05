import java.nio.file.Paths
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink}
import akka.util.ByteString
import scala.concurrent.Future

package object akkaStreamFun {
  /** The Source and Flow methods do not overwrite any pre-existing file sinks */
  def delete(fileName: String): Unit = {
    new java.io.File(fileName).delete()
    ()
  }

  /** @return Sink that writes to a file, overwriting if present */
  def fileSink[T](filename: String): Sink[T, Future[IOResult]] = {
    delete(filename)
    Flow[T]
      .map(s => ByteString(s + "\n"))
      .toMat(FileIO.toPath(Paths.get(filename)))(Keep.right)
  }
}
