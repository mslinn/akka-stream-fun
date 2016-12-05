package quickStart

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, IOResult, UniformFanOutShape}
import scala.concurrent.Future

object ReactiveStreams extends App {
  implicit val system = ActorSystem("reactive-tweets")
  implicit val executionContext = system.dispatcher
  implicit val materializer = ActorMaterializer()

  final case class Author(handle: String)

  final case class HashTag(name: String)

  final case class Tweet(author: Author, timestamp: Long, body: String) {
    def hashTags: Set[HashTag] =
      body.split(" ").collect { case t if t.startsWith("#") => HashTag(t) }.toSet
  }

  val akkaTag = HashTag("#akka")

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
        .filter(_.hashTags.contains(akkaTag))
        .map(_.author)
    authors
      .runWith(sink("authors.txt"))
      //.runWith(Sink.foreach(println)) // Prints lines that start with "Author"
  }

  BlockOf.asycCode("HashTags") {
    val hashTags: Source[HashTag, _] = tweets.mapConcat(_.hashTags.toList)
    hashTags
      .runWith(sink("hashTags.txt"))
      //.runWith(Sink.foreach(println)) // Prints lines that start with "HashTag"
  }

  BlockOf.asycCode("Broadcasting a Stream") {
    // If the type parameter for sink is not supplied then the files will be empty
    val writeAuthors: Sink[Author, Future[IOResult]] = sink[Author]("authors2.txt") // Sink.ignore
    val writeHashTags: Sink[HashTag, Future[IOResult]] = sink[HashTag]("hashTags2.txt") // Sink.ignore

    val runnableGraph: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(GraphDSL.create() { implicit graphDSLBuilder =>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val broadcastShape: UniformFanOutShape[Tweet, Tweet] = graphDSLBuilder.add(Broadcast[Tweet](2))
      tweets ~> broadcastShape.in
      broadcastShape.out(0) ~> Flow[Tweet].map(_.author) ~> writeAuthors
      broadcastShape.out(1) ~> Flow[Tweet].mapConcat(_.hashTags.toList) ~> writeHashTags
      ClosedShape
    })
    runnableGraph.run()
    Future.successful("Done")
  }

  System.exit(0)
}
