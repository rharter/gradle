/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.play.tasks

import org.gradle.play.integtest.fixtures.PlayMultiVersionIntegrationTest
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.util.TextUtil

class TwirlCompileIntegrationTest extends PlayMultiVersionIntegrationTest {
    def destinationDirPath = "build/playBinary/src/twirlCompileTwirlTemplatesPlayBinary/views/html"
    def destinationDir = file(destinationDirPath)

    def setup() {
        settingsFile << """ rootProject.name = 'twirl-play-app' """
        buildFile << """
            plugins {
                id 'play-application'
            }

            repositories{
                jcenter()
                maven{
                    name = "typesafe-maven-release"
                    url = "https://repo.typesafe.com/typesafe/maven-releases"
                }
            }

            model {
                components {
                    play {
                        targetPlatform "play-${version}"
                    }
                }
            }
        """
    }

    def "can run TwirlCompile"() {
        given:
        withTwirlTemplate()
        when:
        succeeds("twirlCompileTwirlTemplatesPlayBinary")
        then:
        destinationDir.assertHasDescendants("index.template.scala")

        when:
        succeeds("twirlCompileTwirlTemplatesPlayBinary")
        then:
        skipped(":twirlCompileTwirlTemplatesPlayBinary");
    }

    def "runs compiler incrementally"() {
        when:
        withTwirlTemplate("input1.scala.html")
        then:
        succeeds("twirlCompileTwirlTemplatesPlayBinary")
        and:
        destinationDir.assertHasDescendants("input1.template.scala")
        def input1FirstCompileSnapshot = file("${destinationDirPath}/input1.template.scala").snapshot();

        when:
        withTwirlTemplate("input2.scala.html")
        and:
        succeeds("twirlCompileTwirlTemplatesPlayBinary")
        then:
        destinationDir.assertHasDescendants("input1.template.scala", "input2.template.scala")
        and:
        file("${destinationDirPath}/input1.template.scala").assertHasNotChangedSince(input1FirstCompileSnapshot)

        when:
        file("app/views/input2.scala.html").delete()
        then:
        succeeds("twirlCompileTwirlTemplatesPlayBinary")
        and:
        destinationDir.assertHasDescendants("input1.template.scala")
    }

    def "removes stale output files in incremental compile"(){
        given:
        withTwirlTemplate("input1.scala.html")
        withTwirlTemplate("input2.scala.html")
        succeeds("twirlCompileTwirlTemplatesPlayBinary")

        and:
        destinationDir.assertHasDescendants("input1.template.scala", "input2.template.scala")
        def input1FirstCompileSnapshot = file("${destinationDirPath}/input1.template.scala").snapshot();

        when:
        file("app/views/input2.scala.html").delete()

        then:
        succeeds("twirlCompileTwirlTemplatesPlayBinary")
        and:
        destinationDir.assertHasDescendants("input1.template.scala")
        file("${destinationDirPath}/input1.template.scala").assertHasNotChangedSince(input1FirstCompileSnapshot);
        file("${destinationDirPath}/input2.template.scala").assertDoesNotExist()
    }

    def "builds multiple twirl source sets as part of play build" () {
        withExtraSourceSets()
        withTemplateSource(file("app", "views", "index.scala.html"))
        withTemplateSource(file("otherSources", "templates", "other.scala.html"))
        withTemplateSource(file("extraSources", "extra.scala.html"))

        when:
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":twirlCompileTwirlTemplatesPlayBinary",
                ":twirlCompileExtraTwirlPlayBinary",
                ":twirlCompileOtherTwirlPlayBinary"
        )

        and:
        destinationDir.assertHasDescendants("index.template.scala")
        file("build/playBinary/src/twirlCompileOtherTwirlPlayBinary/templates/html").assertHasDescendants("other.template.scala")
        file("build/playBinary/src/twirlCompileExtraTwirlPlayBinary/html").assertHasDescendants("extra.template.scala")

        and:
        jar("build/playBinary/lib/twirl-play-app.jar").assertContainsFile("views/html/index.class")
        jar("build/playBinary/lib/twirl-play-app.jar").assertContainsFile("templates/html/other.class")
        jar("build/playBinary/lib/twirl-play-app.jar").assertContainsFile("html/extra.class")
    }

    def "extra sources appear in the component report"() {
        withExtraSourceSets()

        when:
        succeeds "components"

        then:
        output.contains(TextUtil.toPlatformLineSeparators("""
Play Application 'play'
-----------------------

Source sets
    Twirl template source 'play:extraTwirl'
        extraSources
    Java source 'play:java'
        app
        includes: **/*.java
    Twirl template source 'play:otherTwirl'
        otherSources
    JVM resources 'play:resources'
        conf
    Routes source 'play:routesSources'
        conf
        includes: routes, *.routes
    Scala source 'play:scala'
        app
        includes: **/*.scala
    Twirl template source 'play:twirlTemplates'
        app
        includes: **/*.html

Binaries
"""))

    }


    def withTemplateSource(File templateFile) {
        templateFile << """@(message: String)

    @play20.welcome(message)

"""
    }

    def withTwirlTemplate(String fileName = "index.scala.html") {
        def templateFile = file("app", "views", fileName)
        templateFile.createFile()
        withTemplateSource(templateFile)
    }

    def withExtraSourceSets() {
        buildFile << """
            model {
                components {
                    play {
                        sources {
                            extraTwirl(TwirlSourceSet) {
                                source.srcDir "extraSources"
                            }
                            otherTwirl(TwirlSourceSet) {
                                source.srcDir "otherSources"
                            }
                        }
                    }
                }
            }
        """
    }

    JarTestFixture jar(String fileName) {
        new JarTestFixture(file(fileName))
    }
}
