import sbt.*

object Dependencies {

  val test = Seq(
    "uk.gov.hmrc"       %% "performance-test-runner" % "6.3.0"  % Test,
    "com.typesafe.play" %% "play-json"               % "2.10.6" % Test,
    "org.scalaj"        %% "scalaj-http"             % "2.4.2"  % Test,
    "com.lihaoyi"       %% "ujson"                   % "3.3.1"  % Test
  )

}
