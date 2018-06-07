package sample.cluster.factorial

import scala.annotation.tailrec
import scala.concurrent.Future
import com.typesafe.config.ConfigFactory
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.http.scaladsl.Http
import akka.pattern.pipe

//#backend
class FactorialBackend extends Actor with ActorLogging {

  import context.dispatcher

  def receive = {
    case (n: Int) =>
      Future(factorial(n)) map { result => (n, result) } pipeTo sender()
  }

  def factorial(n: Int): BigInt = {
    @tailrec def factorialAcc(acc: BigInt, n: Int): BigInt = {
      if (n <= 1) acc
      else factorialAcc(acc * n, n - 1)
    }
    factorialAcc(BigInt(1), n)
  }

}
//#backend

object FactorialBackend {

  def main(args: Array[String]): Unit = {

    // Java will cache dns lookups by default, turn off cacheing
    java.security.Security.setProperty("networkaddress.cache.ttl", "0")

    val port = if (args.isEmpty) 2551 else args(0).toInt

    val internalIp = NetworkConfig.hostLocalAddress

    println(s"internalIp:$internalIp")

    val appConfig = ConfigFactory.load("factorial")
    val clusterName = appConfig.getString("clustering.name")
    val internalSeedHostname = appConfig.getString("clustering.seed-host")
    val internalSeedPort = appConfig.getString("clustering.seed-port")


    val config = ConfigFactory.parseString("akka.cluster.roles = [backend]").
      withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.hostname=$internalIp")).
      withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")).
      withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.bind-hostname=0.0.0.0")).
      withFallback(NetworkConfig.seedsConfig(clusterName, internalSeedHostname, internalSeedPort)).
      withFallback(appConfig)

    val system = ActorSystem(clusterName, config)
    system.actorOf(Props[FactorialBackend], name = "factorialBackend")
  }
}
