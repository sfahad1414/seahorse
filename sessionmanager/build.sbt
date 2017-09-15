// Copyright (c) 2016, CodiLime Inc.

// scalastyle:off println

import com.typesafe.sbt.packager.docker._

name := "deepsense-sessionmanager"

libraryDependencies ++= Dependencies.sessionmanager
resolvers ++= Dependencies.resolvers

Revolver.settings

enablePlugins(JavaAppPackaging, GitVersioning, DeepsenseUniversalSettingsPlugin)

// If there are many `App` objects in project, docker image will crash with cryptic message
mainClass in Compile := Some("io.deepsense.sessionmanager.SessionManagerApp")

val weJar = taskKey[File]("Workflow executor runnable jar")

weJar := {
  val jar =
    new File("seahorse-workflow-executor/workflowexecutor/target/scala-2.11/workflowexecutor.jar")

  val assemblyCmd = "sbt workflowexecutor/assembly"

  if(jar.exists()) {
    println(
      s"""
         |Workflow executor jar in nested repo already exist. Assuming it's up to date.
         |If you need to rebuild we.jar run `$assemblyCmd` in embedded WE repo.
          """.stripMargin
    )
  } else {
    val shell = Seq("bash", "-c")
    shell :+ s"cd seahorse-workflow-executor; $assemblyCmd" !
  }

  jar
}

mappings in Universal += weJar.value -> "we.jar"

val preparePythonDeps = taskKey[File]("Generates we_deps.zip file with python dependencies")

preparePythonDeps := {
  "sessionmanager/prepare-deps.sh" !

  target.value / "we-deps.zip"
}

preparePythonDeps <<= preparePythonDeps dependsOn weJar

mappings in Universal += preparePythonDeps.value -> "we-deps.zip"

dockerBaseImage := {
  val pattern = "([0-9]+\\.[0-9]+\\.[0-9]+).*".r
  val pattern(versionNumber) = version.value
  s"docker-repo.deepsense.codilime.com/deepsense_io/deepsense-mesos-spark:$versionNumber"
}

dockerCommands ++= Seq(
  Cmd("USER", "root"),
  Cmd("RUN", "/opt/conda/bin/pip install pika==0.10.0"),
  ExecCmd("ENTRYPOINT", "bin/deepsense-sessionmanager")
)
dockerUpdateLatest := true

// scalastyle:on