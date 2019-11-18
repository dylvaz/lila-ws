package lila.ws

import javax.inject._
import kamon.Kamon
import kamon.tag.TagSet
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

@Singleton
final class Monitor @Inject() (config: play.api.Configuration)(implicit system: akka.actor.ActorSystem, ec: ExecutionContext) {

  import Monitor._

  def start: Unit = {

    val version = System.getProperty("java.version")
    val memory = Runtime.getRuntime().maxMemory() / 1024 / 1024
    play.api.Logger("Monitor").info(s"Java version: $version, memory: ${memory}MB")

    if (config.get[String]("kamon.influxdb.hostname").nonEmpty) {
      play.api.Logger("Monitor").info("Kamon is enabled")
      kamon.Kamon.loadModules()
    }

    system.scheduler.schedule(5.seconds, 1949.millis) { periodicMetrics }
  }

  private def periodicMetrics = {
    historyRoomSize.update(History.room.size)
    historyRoundSize.update(History.round.size)
    crowdRoomSize.update(RoomCrowd.size)
    crowdRoundSize.update(RoundCrowd.size)
    busSize.update(Bus.size)
    busAllSize.update(Bus.sizeOf(_.all))
  }
}

object Monitor {

  object connection {
    val current = Kamon.gauge("connection.current").withoutTags
    def open(endpoint: String) = Kamon.counter("connection.open").withTag("endpoint", endpoint).increment()
  }

  val clientOutUnexpected = Kamon.counter("client.out.unexpected").withoutTags

  val historyRoomSize = Kamon.gauge("history.room.size").withoutTags
  val historyRoundSize = Kamon.gauge("history.round.size").withoutTags

  val crowdRoomSize = Kamon.gauge("crowd.room.size").withoutTags
  val crowdRoundSize = Kamon.gauge("crowd.round.size").withoutTags

  val palantirChannels = Kamon.gauge("crowd.channels.size").withoutTags

  val busSize = Kamon.gauge("bus.size").withoutTags
  val busAllSize = Kamon.gauge("bus.all.size").withoutTags

  val chessMoveTime = Kamon.timer("chess.analysis.move.time").withoutTags
  val chessDestTime = Kamon.timer("chess.analysis.dest.time").withoutTags

  object redis {
    val publishTime = Kamon.timer("redis.publish.time").withoutTags
    def in(chan: String, path: String) = Kamon.counter(s"redis.in").withTags(
      TagSet.from(Map("channel" -> chan, "path" -> path))
    ).increment()
    def out(chan: String, path: String) = Kamon.counter(s"redis.out").withTags(
      TagSet.from(Map("channel" -> chan, "path" -> path))
    ).increment()
  }

  def time[A](metric: Monitor.type => kamon.metric.Timer)(f: => A): A = {
    val timer = metric(Monitor).start()
    val res = f
    timer.stop
    res
  }
}
