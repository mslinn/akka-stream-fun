package akkaStreamFun

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import java.nio.file.Paths
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object QuickStart extends App {
  implicit val system = ActorSystem("QuickStart")
  implicit val executionContext = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val source: Source[Int, NotUsed] = Source(1 to 10)
  val factorials: Source[BigInt, NotUsed] = source.scan(BigInt(1))((acc, next) => acc * next)

  WaitForFuture() {
    source.runForeach(i => print(i + " "))(materializer)
  }
  println("")

  WaitForFuture.deleteIfPresent("factorials.txt")() {
    factorials
      .map(num => ByteString(s"$num\n"))
      .runWith(FileIO.toPath(Paths.get("factorials.txt")))
      /*.andThen { // just for interest's sake
          case scala.util.Success(x) => println(x) } */
  }.showFile("factorials.txt")

  WaitForFuture.deleteIfPresent("factorial2.txt")("Reusable Pieces") {
    def lineSink(filename: String): Sink[String, Future[IOResult]] =
      Flow[String]
        .map(s => ByteString(s + "\n"))
        .toMat(FileIO.toPath(Paths.get(filename)))(Keep.right)

    factorials.map(_.toString).runWith(lineSink("factorial2.txt"))
  }.showFile("factorial2.txt")

  WaitForFuture("Time-Based Processing") {
    val done: Future[Done] =
      factorials
        .zipWith(Source(0 to 10))((num, idx) => s"$idx! = $num")
        .throttle(1, 1 second, 1, ThrottleMode.shaping)
        .runForeach(println)
    done
  }

  System.exit(0)
}
