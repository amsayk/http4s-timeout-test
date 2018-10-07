package com.example.timeouttest

import cats.effect.{ConcurrentEffect, Sync, ContextShift, ExitCode, IO, IOApp, Resource}
import cats.implicits._
import org.http4s.implicits._
import org.http4s.HttpRoutes
import org.http4s.server.{ Router, Server }
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.staticcontent.{ MemoryCache, WebjarService, webjarService }

import java.util.concurrent.{ ExecutorService, Executors, ThreadFactory }

import scala.concurrent.ExecutionContext

object HelloWorldServer extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    resource[IO].use(_ => IO.never)

  private def ioResource[F[_]: Sync]: Resource[F, ExecutorService] = {
    val executorService = Sync[F].delay {
      Executors.newCachedThreadPool(new ThreadFactory {
        def newThread(r: Runnable) = {
          val th = new Thread(r)
          th.setDaemon(true)
          th
        }
      })
    }

    Resource.make[F, ExecutorService](executorService)(es ⇒ Sync[F].delay(es.shutdownNow).void)
  }

  private def resource[F[_]: ConcurrentEffect : ContextShift]: Resource[F, Server[F]]= {

    for {
      ec <- ioResource[F]

      helloWorldService = new HelloWorldService[F].routes

      httpApp = Router(
        "/" -> helloWorldService,
        "/assets" → webjarService(
          WebjarService
            .Config(
              blockingExecutionContext = ExecutionContext.fromExecutorService(ec),
              cacheStrategy = MemoryCache[F]
            )
          ),
        ).orNotFound

      server <- BlazeServerBuilder[F]
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(httpApp)
        .resource
    } yield server
  }
}
