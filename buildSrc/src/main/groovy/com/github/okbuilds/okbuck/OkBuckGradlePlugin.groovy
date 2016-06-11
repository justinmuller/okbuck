package com.github.okbuilds.okbuck

import com.github.okbuilds.core.dependency.DependencyCache
import com.github.okbuilds.core.system.BuildSystem
import com.github.okbuilds.core.util.InstallUtil
import com.github.okbuilds.okbuck.config.BUCKFile
import com.github.okbuilds.okbuck.generator.BuckFileGenerator
import com.github.okbuilds.okbuck.generator.DotBuckConfigLocalGenerator
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger

class OkBuckGradlePlugin implements Plugin<Project> {

    static final String OKBUCK = "okbuck"
    static final String OKBUCK_CLEAN = 'okbuckClean'
    static final String BUCK = "BUCK"
    static final String EXPERIMENTAL = "experimental"
    static final String INSTALL = "install"
    static final String INSTALL_BUCK = "installBuck"

    static final String GROUP = "okbuck"

    DependencyCache dependencyCache

    static Logger LOGGER

    void apply(Project project) {
        LOGGER = project.logger
        OkBuckExtension okbuck = project.extensions.create(OKBUCK, OkBuckExtension, project)
        InstallExtension install = okbuck.extensions.create(INSTALL, InstallExtension, project)
        okbuck.extensions.create(EXPERIMENTAL, ExperimentalExtension)

        dependencyCache = new DependencyCache(project)

        Task okBuckClean = project.task(OKBUCK_CLEAN)
        okBuckClean.setGroup(GROUP)
        okBuckClean.setDescription("Delete all files generated by OkBuck and buck")

        okBuckClean << {
            [".okbuck", ".buckd", "buck-out", ".buckconfig.local"]
                    .plus(okbuck.buckProjects.collect { it.file(BUCK).absolutePath })
                    .minus(okbuck.keep).each { String file ->
                FileUtils.deleteQuietly(project.file(file))
            }
        }

        Task okBuck = project.task(OKBUCK)
        okBuck.setGroup(GROUP)
        okBuck.setDescription("Generate BUCK files")

        okBuck.outputs.upToDateWhen { false }
        okBuck.dependsOn(okBuckClean)
        okBuck << {
            generate(project)
        }

        Task installBuck = project.task(INSTALL_BUCK)
        installBuck.outputs.upToDateWhen { false }
        installBuck.setGroup(GROUP)
        installBuck.setDescription("Install buck")

        installBuck << {
            InstallUtil.install(project, BuildSystem.BUCK, install.gitUrl, install.sha, new File(install.dir))
        }
    }

    private static generate(Project project) {
        OkBuckExtension okbuck = project.okbuck

        // generate empty .buckconfig if it does not exist
        File dotBuckConfig = project.file(".buckconfig")
        if (!dotBuckConfig.exists()) {
            dotBuckConfig.createNewFile()
        }

        // generate .buckconfig.local
        File dotBuckConfigLocal = project.file(".buckconfig.local")
        PrintStream configPrinter = new PrintStream(dotBuckConfigLocal)
        DotBuckConfigLocalGenerator.generate(okbuck).print(configPrinter)
        IOUtils.closeQuietly(configPrinter)

        // generate BUCK file for each project
        Map<Project, BUCKFile> buckFiles = new BuckFileGenerator(project).generate()

        buckFiles.each { Project subProject, BUCKFile buckFile ->
            PrintStream buckPrinter = new PrintStream(subProject.file(BUCK))
            buckFile.print(buckPrinter)
            IOUtils.closeQuietly(buckPrinter)
        }
    }
}
