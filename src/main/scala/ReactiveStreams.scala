import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.reactivestreams.Processor
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.Duration
import scala.util.Success

object ReactiveStreams extends App {
  implicit val system = ActorSystem("reactive-tweets")
  implicit val executionContext = system.dispatcher
  implicit val materializer = ActorMaterializer()

  final case class Author(handle: String)

  final case class Hashtag(name: String)

  final case class Tweet(author: Author, timestamp: Long, body: String) {
    def hashtags: Set[Hashtag] =
      body.split(" ").collect { case t if t.startsWith("#") => Hashtag(t) }.toSet
  }

  val akkaTag = Hashtag("#akka")

  val tweets: Source[Tweet, NotUsed] = Source(
    Tweet(Author("rolandkuhn"), System.currentTimeMillis, "#akka rocks!") ::
    Tweet(Author("patriknw"), System.currentTimeMillis, "#akka !") ::
    Tweet(Author("bantonsson"), System.currentTimeMillis, "#akka !") ::
    Tweet(Author("drewhk"), System.currentTimeMillis, "#akka !") ::
    Tweet(Author("ktosopl"), System.currentTimeMillis, "#akka on the rocks!") ::
    Tweet(Author("mmartynas"), System.currentTimeMillis, "wow #akka !") ::
    Tweet(Author("akkateam"), System.currentTimeMillis, "#akka rocks!") ::
    Tweet(Author("bananaman"), System.currentTimeMillis, "#bananas rock!") ::
    Tweet(Author("appleman"), System.currentTimeMillis, "#apples rock!") ::
    Tweet(Author("drama"), System.currentTimeMillis, "we compared #apples to #oranges!") ::
    Nil
  )

  BlockOf.asycCode("Authors", delimChar="") {
    val authors: Source[Author, NotUsed] =
      tweets
        .filter(_.hashtags.contains(akkaTag))
        .map(_.author)
    authors
      .runWith(Sink.foreach(println)) // Prints lines that start with "Author"
  }

  BlockOf.asycCode("Hashtags") {
    val hashtags: Source[Hashtag, NotUsed] = tweets.mapConcat(_.hashtags.toList)
    hashtags
      .runWith(Sink.foreach(println)) // Prints lines that start with "Hashtag"
  }


  ////////////////////////////
  trait Fixture {
    val authors: Flow[Tweet, Author, NotUsed] = Flow[Tweet]
      .filter(_.hashtags.contains(akkaTag))
      .map(_.author)

    def tweets: Publisher[Tweet]

    def storage: Subscriber[Author]

    def alert: Subscriber[Author]
  }

/*  val sub = storage.expectSubscription()
  sub.request(10)
  storage.expectNext(Author("rolandkuhn"))
  storage.expectNext(Author("patriknw"))
  storage.expectNext(Author("bantonsson"))
  storage.expectNext(Author("drewhk"))
  storage.expectNext(Author("ktosopl"))
  storage.expectNext(Author("mmartynas"))
  storage.expectNext(Author("akkateam"))
  storage.expectComplete()

  Source.fromPublisher(tweets).via(authors).to(Sink.fromSubscriber(storage)).run()*/

  System.exit(0)
}
