package akkaStreamFun

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

object WaitForFuture {
  def apply(msg: String="", delimChar: String = "=")
           (code: => Future[_])
           (implicit ec: ExecutionContext): WaitForFuture =
    new WaitForFuture().apply(msg, delimChar)(code)(ec)

  def deleteIfPresent(fileName: String): WaitForFuture = {
    new java.io.File(fileName).delete()
    new WaitForFuture()
  }
}

class WaitForFuture {
  def apply(msg: String="", delimChar: String = "=")(code: => Future[_])
                 (implicit ec: ExecutionContext): WaitForFuture = {
    if (msg.nonEmpty && delimChar.nonEmpty) println("\n" + delimChar*50)
    if (msg.nonEmpty) println(msg)
    if (msg.nonEmpty && delimChar.nonEmpty) println(delimChar*50)
    Await.ready(code, Duration.Inf)
    this
  }

  def showFile(fileName: String): WaitForFuture = {
    println(s"\n--- $fileName ---")
    println(io.Source.fromFile(fileName).mkString)
    this
  }
}
