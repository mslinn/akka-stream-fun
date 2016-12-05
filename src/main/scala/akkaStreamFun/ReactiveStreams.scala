package akkaStreamFun

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, IOResult, OverflowStrategy, UniformFanOutShape}
import scala.concurrent.Future
import scala.util.Success

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


  WaitForFuture("Authors") {
    val authors: Source[Author, NotUsed] =
      tweets
        .filter(_.hashTags.contains(akkaTag))
        .map(_.author)
    authors
      .runWith(fileSink("authors.txt"))
      //.runWith(Sink.foreach(println)) // Prints lines that start with "Author"
  }.showFile("authors.txt")


  WaitForFuture.apply("HashTags") {
    val hashTags: Source[HashTag, _] = tweets.mapConcat(_.hashTags.toList)
    hashTags
      .runWith(fileSink("hashTags.txt"))
      //.runWith(Sink.foreach(println)) // Prints lines that start with "HashTag"
  }.showFile("hashTags.txt")


  WaitForFuture("Broadcasting a Stream") {
    // If the type parameter for sink() is not supplied then the files it creates will be empty
    val writeAuthors: Sink[Author, Future[IOResult]] = fileSink[Author]("authors2.txt") // Sink.ignore
    val writeHashTags: Sink[HashTag, Future[IOResult]] = fileSink[HashTag]("hashTags2.txt") // Sink.ignore

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
  }.showFile("authors2.txt")
   .showFile("hashTags2.txt")


  WaitForFuture("Back-pressure in action") {
    def slowComputation(tweet: Tweet): Long = {
      Thread.sleep(500) // act as if performing some heavy computation
      tweet.body.length.toLong
    }

    tweets
      .buffer(10, OverflowStrategy.dropHead)
      .map(slowComputation)
      .runWith(Sink.ignore)
  }


  WaitForFuture("Materialized Values") {
    val count: Flow[Tweet, Int, NotUsed] = Flow[Tweet].map(_ => 1)

    val sumSink: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)(_ + _)

    val counterGraph: RunnableGraph[Future[Int]] =
      tweets
        .via(count)
        .toMat(sumSink)(Keep.right)

    val futureSum: Future[Int] = counterGraph.run()
    futureSum.andThen { case Success(c) => println(s"Total tweets processed: $c") }
  }


  WaitForFuture("More Materialized Values") {
    val sumSink = Sink.fold[Int, Int](0)(_ + _)

    val counterRunnableGraph: RunnableGraph[Future[Int]] =
      tweets
        .filter(_.hashTags contains akkaTag)
        .map(_ => 1)
        .toMat(sumSink)(Keep.right)

    // materialize the stream once in the morning
    val futureMorningTweets: Future[Int] = counterRunnableGraph.run()
    futureMorningTweets.andThen { case Success(c) => println(s"Morning tweets containing ${ akkaTag.name }: $c") }

    // and once in the evening, reusing the flow
    val futureEveningTweets: Future[Int] = counterRunnableGraph.run()
    futureEveningTweets.andThen { case Success(c) => println(s"Evening tweets containing ${ akkaTag.name }: $c") }

    val futureSumTweets: Future[Int] =
      tweets
        .filter(_.hashTags contains akkaTag)
        .map(_ => 1)
        .runWith(sumSink)
    futureSumTweets.andThen { case Success(c) => println(s"Tweets containing ${ akkaTag.name }: $c") }
  }

  System.exit(0)
}
