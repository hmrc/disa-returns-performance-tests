import sbt.*

object Dependencies {

  val test = Seq(
    "uk.gov.hmrc"        %% "performance-test-runner" % "6.3.0"  % Test,
    "com.typesafe.play"  %% "play-json"               % "2.10.8" % Test,
    ("org.playframework" %% "play-ahc-ws-standalone"  % "3.0.13" % Test).cross(CrossVersion.for3Use2_13),
    ("org.apache.pekko"  %% "pekko-stream"            % "1.7.0"  % Test).cross(CrossVersion.for3Use2_13),
    "org.scalatest"      %% "scalatest"               % "3.2.20" % Test
  )
}
