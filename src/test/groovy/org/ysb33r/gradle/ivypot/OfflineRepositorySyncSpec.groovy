//
// ============================================================================
// (C) Copyright Schalk W. Cronje 2013-2019
//
// This software is licensed under the Apache License 2.0
// See http://www.apache.org/licenses/LICENSE-2.0 for license details
//
// Unless required by applicable law or agreed to in writing, software distributed under the License is
// distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and limitations under the License.
//
// ============================================================================
//

package org.ysb33r.gradle.ivypot


import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * @author Schalk W. Cronjé
 */
class OfflineRepositorySyncSpec extends Specification {

    Project project
    OfflineRepositorySync syncTask

    void setup() {
        project = ProjectBuilder.builder().build()
        syncTask = project.tasks.create('syncTask', OfflineRepositorySync)
    }

    void "Setting up repositories"() {

        given:
        project.allprojects {

            // tag::usage[]
            syncTask {
                repoRoot '/path/to/folder'

                configurations 'compile', 'testCompile'

                repositories {
                    mavenCentral()
                    mavenLocal()
                    jcenter()
                    google()

                    maven {
                        url 'http://foo.com/bar'
                    }

                    maven {
                        url 'http://hog.com/whole'
                        artifactUrls 'http://hog.com/one'
                        artifactUrls 'http://hog.com/two'

                        credentials {
                            username 'the'
                            password 'pig'
                        }
                    }

                    ivy {
                        url 'http://ivy/climber'
                        layout 'maven'
                    }

                    ivy {
                        url 'http://gradle/grover'
                        layout 'gradle'
                    }

                    ivy {
                        url 'http://hog/roast'
                        layout 'ivy'

                        credentials {
                            username 'the'
                            password 'pig'
                            realm 'sty'
                        }
                    }

                    ivy {
                        url 'http://pat/tern'
                        layout 'pattern', {
                            artifact '[artifact].[ext]'
                            ivy 'foo/ivy.xml'
                        }
                    }
                }
            }
            // end::usage[]
        }

        // Need to extract repositories in order collected using these gradle-assigned names
        def mavenC = syncTask.repositories.getByName('MavenRepo')
        def mavenL = syncTask.repositories.getByName('MavenLocal')
        def maven2 = syncTask.repositories.getByName('maven_1')
        def maven3 = syncTask.repositories.getByName('maven_2')
        def bintray = syncTask.repositories.getByName('BintrayJCenter')
        def ivyMaven = syncTask.repositories.getByName('ivy_3')
        def ivyGradle = syncTask.repositories.getByName('ivy_4')
        def ivyIvy = syncTask.repositories.getByName('ivy_5')
        def ivyPattern = syncTask.repositories.getByName('ivy_6')
        def google = syncTask.repositories.getByName('Google')

        expect: 'Local repo has been set'
        syncTask.repoRoot == project.file('/path/to/folder')

        and: 'mavenCentral is loaded'
        mavenC.resolverXml() == $/<ibiblio name='MavenRepo' m2compatible='true' />/$

        and: 'a maven repo can be added'
        maven2.url == 'http://foo.com/bar'.toURI()
        maven2.resolverXml() == $/<ibiblio name='maven_1' m2compatible='true' root='http://foo.com/bar' />/$

        and: 'a maven repo with artifact urls and credentials can be added'
        maven3.url == 'http://hog.com/whole'.toURI()
        maven3.artifactUrls.contains('http://hog.com/one'.toURI())
        maven3.artifactUrls.contains('http://hog.com/two'.toURI())
        maven3.credentials.username == 'the'
        maven3.credentials.password == 'pig'
        maven3.resolverXml() == '''<chain name='maven_2'>
  <ibiblio name='maven_2_root}' m2compatible='true' root='http://hog.com/whole' usepoms='true' />
  <ibiblio name='maven_2_0' m2compatible='true' root='http://hog.com/one' usepoms='false' />
  <ibiblio name='maven_2_1' m2compatible='true' root='http://hog.com/two' usepoms='false' />
</chain>'''

        and: 'JCenter is loaded'
        bintray.resolverXml() == $/<ibiblio name='BintrayJCenter' root='https://jcenter.bintray.com/' m2compatible='true' />/$

        and: 'mavenLocal is loaded'
        mavenL.resolverXml() == $/<ibiblio name='MavenLocal' /$ +
                "root='${new File(System.getProperty('user.home')).absoluteFile.toURI()}.m2/repository/' " +
                $/m2compatible='true' checkmodified='true' changingPattern='.*' changingMatcher='regexp' />/$

        and: 'ivy with maven layout loaded'
        ivyMaven.resolverXml() == """<url name='ivy_3' m2compatible='true'>
  <ivy pattern='http://ivy/climber/[organisation]/[module]/[revision]/ivy-[revision].xml' />
  <artifact pattern='http://ivy/climber/${IvyArtifactRepository.MAVEN_ARTIFACT_PATTERN}' />
</url>"""

        and: 'ivy with gradle layout loaded'
        ivyGradle.resolverXml() == """<url name='ivy_4' m2compatible='false'>
  <ivy pattern='http://gradle/grover/[organisation]/[module]/[revision]/ivy-[revision].xml' />
  <artifact pattern='http://gradle/grover/${IvyArtifactRepository.GRADLE_ARTIFACT_PATTERN}' />
</url>"""

        and: 'ivy with ivy layout loaded + credentials'
        ivyIvy.resolverXml() == """<url name='ivy_5' m2compatible='false'>
  <ivy pattern='http://hog/roast/${IvyArtifactRepository.IVY_ARTIFACT_PATTERN}' />
  <artifact pattern='http://hog/roast/${IvyArtifactRepository.IVY_ARTIFACT_PATTERN}' />
</url>"""
        ivyIvy.credentials.username == 'the'
        ivyIvy.credentials.password == 'pig'
        ivyIvy.credentials.realm == 'sty'

        and: 'ivy with pattern layout loaded'
        ivyPattern.resolverXml() == '''<url name='ivy_6' m2compatible='false'>
  <ivy pattern='http://pat/tern/foo/ivy.xml' />
  <artifact pattern='http://pat/tern/[artifact].[ext]' />
</url>'''

        and: 'google was loaded'
        google.resolverXml() == $/<ibiblio name='Google' root='https://dl.google.com/dl/android/maven2/' m2compatible='true' />/$
    }

    void "Not specifying a configuration, means all configurations are loaded"() {
        given:
        project.allprojects {
            syncTask {

            }

            configurations {
                config1
                config2
            }
        }
        Iterable<Configuration> configs = syncTask.configurations

        expect:
        getConfiguration(configs, 'config1') != null
        getConfiguration(configs, 'config2') != null
        configs.size() == 2
    }

    void "Specifying configurations means only those are added"() {
        given:
        project.allprojects {
            syncTask {
                configurations 'config1'
            }

            configurations {
                config1
                config2
            }
        }

        when:
        Iterable<Configuration> configs = syncTask.configurations

        then:
        getConfiguration(configs, 'config2') == null

        and:
        getConfiguration(configs, 'config1') != null
        configs.size() == 1
    }

    void "Configurations can be added by instance"() {
        project.allprojects {
            configurations {
                config1
                config2
            }
            syncTask {
                configurations project.configurations.getByName('config1')
            }

        }

        when:
        Iterable<Configuration> configs = syncTask.configurations

        then:
        getConfiguration(configs, 'config2') == null

        and:
        getConfiguration(configs, 'config1') != null
        configs.size() == 1

    }

    void "Cannot use existing project as parameter to addProject"() {
        when:
        project.allprojects {
            syncTask {
                addProject project
            }
        }

        then:
        thrown(CannotUseCurrentProjectException)
    }

    void 'Adding a binary repository'() {
        when:
        project.allprojects {
            syncTask {
                binaryRepositories {
                    gradleDist {
                        rootUri = 'https://services.gradle.org/distributions/'
                        artifactPattern = 'gradle-[revision]-[type].[ext]'
                    }
                }
            }
        }

        def binaryRepo = syncTask.binaryRepositories.getByName('gradleDist')

        then:
        verifyAll {
            binaryRepo.rootUri == 'https://services.gradle.org/distributions/'.toURI()
            binaryRepo.artifactPattern == 'gradle-[revision]-[type].[ext]'
        }
    }

    private getConfiguration(final Iterable<Configuration> configs, final String name) {
        configs.find {
            it.name == name
        }
    }
}