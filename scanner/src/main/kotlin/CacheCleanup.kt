package com.here.ort.scanner

import com.here.ort.model.ScanResult
import com.here.ort.model.ScanResultContainer
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.model.readValue
import com.here.ort.model.yamlMapper
import com.here.ort.scanner.scanners.ScanCode
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.safeMkdirs
import okhttp3.Request

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.SortedSet

fun main(args: Array<String>) {
    val tmpDir = File("/tmp/cache-cleanup")
    tmpDir.safeMkdirs()

    val url = args[0]
    val repository = args[1]
    val apiToken = args[2]

    val cache = ArtifactoryCache(url, repository, apiToken)
    val files = cache.listFiles()

    val processedFilesFile = File(tmpDir, "processed-files.yml")
    val processedFiles = try {
        processedFilesFile.readValue<SortedSet<String>>()
    } catch (e: FileNotFoundException) {
        sortedSetOf<String>()
    }

    println("Found ${files.size} files.")
    println("${processedFiles.size} files already processed.")

    val localResultsFile = File(tmpDir, "scan-results.yml")

    val filesToProcess = (files - processedFiles)
    filesToProcess.forEachIndexed { index, file ->
        println("Processing $file (${index + 1}/${filesToProcess.size})...")

        // Download cache file
        if (localResultsFile.exists()) localResultsFile.delete()

        println("\tDownloading $file...")
        URL("$url/$repository/scan-results/$file").openStream().use {
            it.copyTo(localResultsFile.outputStream())
        }
        val sizeBefore = localResultsFile.length()
        println("\tDownloaded scan-results.yml (${sizeBefore / 1048576}MB)")

        // Update locations in cache file
        println("\tUpdating license and copyright locations...")
        updateLocations(localResultsFile)

        // Upload cache file
        val sizeAfter = localResultsFile.length()
        println("\tUploading $file... (${sizeAfter / 1048576}MB, ${(sizeAfter - sizeBefore) / 1048576}MB larger than before)")
        uploadFile(file, localResultsFile, url, repository, apiToken)

        // Update processed packages file
        processedFiles += file
        yamlMapper.writeValue(processedFilesFile, processedFiles)
    }
}

fun updateLocations(file: File) {
    val scanCode = ScanCode(ScannerConfiguration())

    val container = file.readValue<ScanResultContainer>()
    println("\tFound ${container.results.size} results for ${container.id}")

    val newResults = mutableListOf<ScanResult>()

    container.results.forEach { scanResult ->
        scanResult.rawResult?.let { rawResult ->
            val findings = scanCode.associateFindings(rawResult)
            newResults += scanResult.copy(summary = scanResult.summary.copy(licenseFindings = findings))
        } ?: newResults.add(scanResult)
    }

    val newContainer = container.copy(results = newResults)

    yamlMapper.writerWithDefaultPrettyPrinter().writeValue(file, newContainer)
}

fun uploadFile(file: String, localResultsFile: File, url: String, repository: String, apiToken: String) {
    val request = Request.Builder()
            .header("X-JFrog-Art-Api", apiToken)
            .put(OkHttpClientHelper.createRequestBody(localResultsFile))
            .url("$url/$repository/scan-results/$file")
            .build()

    try {
        OkHttpClientHelper.execute(HTTP_CACHE_PATH, request).use { response ->
            if (response.code() == HttpURLConnection.HTTP_CREATED) {
                println("\tUploaded $file to Artifactory cache.")
            } else {
                println("\tCould not upload $file to Artifactory cache: ${response.code()} - ${response.message()}")
            }
        }
    } catch (e: IOException) {
        e.printStackTrace()

        println("\tCould not upload $file to Artifactory cache: ${e.message}")
    }
}
