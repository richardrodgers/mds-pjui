name := "mds-pjui"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  javaJdbc,
  javaEbean,
  cache,
  "org.dspace" % "dsm-kernel" % "1.0-SNAPSHOT"
)     

resolvers += (
    "Local Maven Repo" at "file:///"+Path.userHome+"/.m2/repository"
)

play.Project.playJavaSettings
