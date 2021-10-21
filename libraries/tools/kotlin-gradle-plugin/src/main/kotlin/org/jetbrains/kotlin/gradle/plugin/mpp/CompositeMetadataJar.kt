/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import org.jetbrains.kotlin.gradle.utils.copyZipFilePartially
import org.jetbrains.kotlin.gradle.utils.listChildren
import java.io.File
import java.util.zip.ZipFile

internal fun CompositeMetadataJar(
    moduleIdentifier: String,
    projectStructureMetadata: KotlinProjectStructureMetadata,
    primaryArtifactFile: File,
    hostSpecificArtifactsBySourceSet: Map<String, File>,
): CompositeMetadataJar = CompositeMetadataJarImpl(
    moduleIdentifier, projectStructureMetadata, primaryArtifactFile, hostSpecificArtifactsBySourceSet
)

internal interface CompositeMetadataJar {
    fun getSourceSetCompiledMetadata(
        sourceSetName: String, outputDirectory: File, materializeFile: Boolean
    ): File

    fun getSourceSetCInteropMetadata(
        sourceSetName: String, outputDirectory: File, materializeFiles: Boolean
    ): Set<File>
}

private class CompositeMetadataJarImpl(
    private val moduleIdentifier: String,
    private val projectStructureMetadata: KotlinProjectStructureMetadata,
    private val primaryArtifactFile: File,
    private val hostSpecificArtifactsBySourceSet: Map<String, File>,
) : CompositeMetadataJar {

    override fun getSourceSetCompiledMetadata(
        sourceSetName: String, outputDirectory: File, materializeFile: Boolean
    ): File {
        val artifactFile = getArtifactFile(sourceSetName)
        val moduleOutputDirectory = outputDirectory.resolve(moduleIdentifier).also { it.mkdirs() }

        val extension = projectStructureMetadata.sourceSetBinaryLayout[sourceSetName]?.archiveExtension
            ?: SourceSetMetadataLayout.METADATA.archiveExtension

        val metadataOutputFile = moduleOutputDirectory.resolve("$moduleIdentifier-$sourceSetName.$extension")

        /** In composite builds, we don't really need tro process the file in IDE import, so ignore it if it's missing */
        // refactor: allow only included builds to provide no artifacts, and allow this only in IDE import
        if (!artifactFile.isFile) {
            return metadataOutputFile
        }

        if (!materializeFile) {
            return metadataOutputFile
        }

        if (metadataOutputFile.exists()) {
            metadataOutputFile.delete()
        }

        copyZipFilePartially(artifactFile, metadataOutputFile, "$sourceSetName/")
        return metadataOutputFile
    }

    override fun getSourceSetCInteropMetadata(
        sourceSetName: String, outputDirectory: File, materializeFiles: Boolean
    ): Set<File> {
        val artifactFile = getArtifactFile(sourceSetName)
        val moduleOutputDirectory = outputDirectory.resolve(moduleIdentifier).also(File::mkdirs)

        ZipFile(getArtifactFile(sourceSetName)).use { compoundMetadataArtifactZipFile ->
            val cinteropRootDirectory = compoundMetadataArtifactZipFile.entries().asSequence()
                .firstOrNull { zipEntry -> zipEntry.name == "$sourceSetName-cinterop/" && zipEntry.isDirectory }
                ?: return emptySet()

            val cinterops = compoundMetadataArtifactZipFile.listChildren(cinteropRootDirectory)

            val cinteropsByOutputFile = cinterops.associateBy { cinteropZipEntry ->
                moduleOutputDirectory.resolve("${cinteropZipEntry.name.removePrefix(cinteropRootDirectory.name).removeSuffix("/")}.klib")
            }

            if (materializeFiles) {
                cinteropsByOutputFile.forEach { (cinteropOutputFile, cinteropZipEntry) ->
                    copyZipFilePartially(artifactFile, cinteropOutputFile, cinteropZipEntry.name)
                }
            }

            return cinteropsByOutputFile.keys
        }
    }

    private fun getArtifactFile(sourceSetName: String): File =
        hostSpecificArtifactsBySourceSet[sourceSetName] ?: primaryArtifactFile
}

