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

package org.ysb33r.gradle.ivypot.repositories

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.xml.MarkupBuilder
import org.gradle.api.Action
import org.gradle.api.artifacts.repositories.RepositoryLayout
import org.ysb33r.gradle.ivypot.internal.PatternBasedResolver
import org.ysb33r.grolifant.api.ClosureUtils

/**
 * @since 1.0
 */
@CompileStatic
class IvyArtifactRepository implements Repository, RepositoryTraits {

    String artifactPattern
    String ivyPattern
    String layoutName

    /**
     * Adds an independent pattern that will be used to locate artifact files in this repository. This pattern will be used to locate ivy files as well, unless a specific
     * ivy pattern is supplied via {@link #ivyPattern(String)}.
     *
     * If this pattern is not a fully-qualified URL, it will be interpreted as a file relative to the project directory.
     * It is not interpreted relative the the URL specified in {@link #setUrl(Object)}.
     *
     * Patterns added in this way will be in addition to any layout-based patterns added via {@link #setUrl}.
     *
     * @param pattern The artifact pattern.
     */
    void artifactPattern(String pattern) {
        this.artifactPattern = pattern
    }

    /**
     * Adds an independent pattern that will be used to locate ivy files in this repository.
     *
     * If this pattern is not a fully-qualified URL, it will be interpreted as a file relative to the project directory.
     * It is not interpreted relative the the URL specified in {@link #setUrl(Object)}.
     *
     * Patterns added in this way will be in addition to any layout-based patterns added via {@link #setUrl}.
     *
     * @param pattern The ivy pattern.
     */
    void ivyPattern(String pattern) {
        this.ivyPattern = pattern
    }

    /**
     * Specifies the layout to use with this repository, based on the root url.
     * See {@link #layout(String, Closure)}.
     *
     * @param layoutName The name of the layout to use.
     */
    void layout(final String layoutName) {
        final String namespace = 'org.gradle.api.internal.artifacts.repositories.layout.'
        String repositoryLayoutName
        Class layoutClass
        switch (layoutName) {
            case 'maven':
            case 'ivy':
            case 'gradle':
            case 'pattern':
                repositoryLayoutName = layoutName.capitalize()
                break
            default:
                throw new UnsupportedOperationException("'${layoutName}' is not a valid layout")
        }

        try {
            layoutClass = Class.forName "${namespace}${repositoryLayoutName}RepositoryLayout"
        } catch (ClassNotFoundException e) {
            // Change in class name prefix in Gradle 2.3 from 'Pattern' to 'DefaultIvyPattern'.
            if (layoutName == 'pattern') {
                layoutClass = Class.forName "${namespace}DefaultIvy${repositoryLayoutName}RepositoryLayout"
            } else {
                throw e
            }
        }

        setLayoutClass(layoutClass)
    }

    /**
     * Specifies how the items of the repository are organized.
     * <p>
     * The layout is configured with the supplied closure.
     * <p>
     * Recognised values are as follows:
     * </p>
     * <h4>'gradle'</h4>
     * <p>
     * A Repository Layout that applies the following patterns:
     * <ul>
     *     <li>Artifacts: <code>$baseUri/{@value #GRADLE_ARTIFACT_PATTERN}</code></li>
     *     <li>Ivy: <code>$baseUri/{@value #GRADLE_IVY_PATTERN}</code></li>
     * </ul>
     * </p>
     * <h4>'maven'</h4>
     * <p>
     * A Repository Layout that applies the following patterns:
     * <ul>
     *     <li>Artifacts: <code>$baseUri/{@value #MAVEN_ARTIFACT_PATTERN}</code></li>
     *     <li>Ivy: <code>$baseUri/{@value #MAVEN_IVY_PATTERN}</code></li>
     * </ul>
     * </p>
     * <p>
     * Following the Maven convention, the 'organisation' value is further processed by replacing '.' with '/'.
     * </p>
     * <h4>'ivy'</h4>
     * <p>
     * A Repository Layout that applies the following patterns:
     * <ul>
     *     <li>Artifacts: <code>$baseUri/{@value #IVY_ARTIFACT_PATTERN}</code></li>
     *     <li>Ivy: <code>$baseUri/{@value #IVY_ARTIFACT_PATTERN}</code></li>
     * </ul>
     * </p>
     * <p><b>Note:</b> this pattern is currently {@link org.gradle.api.Incubating incubating}.</p>
     * <h4>'pattern'</h4>
     * <p>
     * A repository layout that allows custom patterns to be defined. eg:
     * <pre autoTested="">
     * repositories {*     ivy {*         layout 'pattern' , {*             artifact '[module]/[revision]/[artifact](.[ext])'
     *             ivy '[module]/[revision]/ivy.xml'
     *}*}*}* </pre>
     * </p>
     *
     * @param layoutName The name of the layout to use.
     * @param config The closure used to configure the layout.
     */
    void layout(String layoutName, Closure config) {
        layout(layoutName)
        ClosureUtils.configureItem(repositoryLayout, config)
    }

    /** Specifies how the items of the repository are organized.
     *
     * @param layoutName The name of the layout to use.
     * @param action The action used to configure the layout. Takes a {@code RepositoryLayout} as parameter.
     *
     */
    void layout(String layoutName, Action<? extends RepositoryLayout> action) {
        layout(layoutName)
        action.execute(repositoryLayout)
    }

    @Override
    @CompileDynamic
    void writeTo(MarkupBuilder builder) {
        if (repositoryLayout == null) {
            throw new UnsupportedOperationException('layout has not seen set for Ivy repository')
        }

        PatternBasedResolver patterns = new PatternBasedResolver()
        applyPatterns(patterns)

        builder.url(name: name, m2compatible: patterns.m2compatible) {
            patterns.ivyPatterns.each { pat ->
                ivy(pattern: pat.pattern)
            }
            patterns.artifactPatterns.each { pat ->
                artifact(pattern: pat.pattern)
            }
        }
    }

    @CompileDynamic
    private void applyPatterns(final PatternBasedResolver patterns) {
        repositoryLayout.apply(url, patterns)
    }

    @CompileDynamic
    private void setLayoutClass(Class layoutClass) {
        repositoryLayout = layoutClass.newInstance()
    }

    private RepositoryLayout repositoryLayout

}
