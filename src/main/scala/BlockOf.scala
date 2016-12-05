import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

object BlockOf {
  def asycCode[T](msg: String="", delimChar: String = "=")(code: => Future[T])
                 (implicit ec: ExecutionContext): T = {
    if (delimChar.nonEmpty) println("\n" + delimChar*50)
    if (msg.nonEmpty) println(msg)
    if (delimChar.nonEmpty) println(delimChar*50)
    Await.result(code, Duration.Inf)
  }
}
