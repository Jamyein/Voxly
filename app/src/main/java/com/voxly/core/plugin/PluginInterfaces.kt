package com.voxly.core.plugin

import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata

/**
 * Interface for MP3 Tag Editor plugins.
 * Plugins can extend the app's functionality without modifying core code.
 */
interface Plugin {
    /**
     * Unique identifier for the plugin.
     */
    val id: String

    /**
     * Display name of the plugin.
     */
    val name: String

    /**
     * Plugin version.
     */
    val version: String

    /**
     * Plugin description.
     */
    val description: String

    /**
     * Plugin author.
     */
    val author: String

    /**
     * Called when the plugin is initialized.
     * @param context Plugin context providing access to app services
     */
    fun initialize(context: PluginContext)

    /**
     * Called when the plugin is being shut down.
     */
    fun shutdown()
}

/**
 * Interface for metadata processor plugins.
 * Allows plugins to provide custom metadata processing logic.
 */
interface MetadataProcessorPlugin : Plugin {
    /**
     * Processes metadata before it's saved to a file.
     * @param filePath Path to the audio file
     * @param metadata Current metadata
     * @return Modified metadata
     */
    fun processMetadata(filePath: String, metadata: AudioMetadata): AudioMetadata

    /**
     * Called after metadata has been saved.
     * @param filePath Path to the audio file
     * @param metadata Saved metadata
     */
    fun onMetadataSaved(filePath: String, metadata: AudioMetadata)
}

/**
 * Interface for export/import plugins.
 * Allows plugins to support additional file formats.
 */
interface ExportImportPlugin : Plugin {
    /**
     * Supported file extensions for import.
     */
    val supportedImportFormats: List<String>

    /**
     * Supported file extensions for export.
     */
    val supportedExportFormats: List<String>

    /**
     * Imports metadata from a file.
     * @param filePath Path to the import file
     * @return Imported metadata or null if import failed
     */
    fun importMetadata(filePath: String): AudioMetadata?

    /**
     * Exports metadata to a file.
     * @param filePath Target file path
     * @param metadata Metadata to export
     * @return True if export succeeded
     */
    fun exportMetadata(filePath: String, metadata: AudioMetadata): Boolean
}

/**
 * Interface for UI extension plugins.
 * Allows plugins to add custom UI components.
 */
interface UIExtensionPlugin : Plugin {
    /**
     * Returns a list of menu items to add to the main menu.
     */
    fun getMenuItems(): List<PluginMenuItem>

    /**
     * Returns a list of actions to add to the file context menu.
     */
    fun getFileContextActions(): List<PluginFileAction>
}

/**
 * Context provided to plugins for accessing app services.
 */
interface PluginContext {
    /**
     * Gets the app version.
     */
    val appVersion: String

    /**
     * Shows a toast message.
     */
    fun showToast(message: String)

    /**
     * Shows a notification.
     */
    fun showNotification(title: String, message: String)

    /**
     * Logs a message.
     */
    fun log(message: String, level: LogLevel = LogLevel.INFO)

    /**
     * Gets an audio file by path.
     */
    suspend fun getAudioFile(filePath: String): AudioFile?

    /**
     * Updates metadata for an audio file.
     */
    suspend fun updateMetadata(filePath: String, metadata: AudioMetadata): Boolean
}

/**
 * Data class representing a plugin menu item.
 */
data class PluginMenuItem(
    val id: String,
    val title: String,
    val icon: String? = null,
    val action: () -> Unit
)

/**
 * Data class representing a plugin file action.
 */
data class PluginFileAction(
    val id: String,
    val title: String,
    val icon: String? = null,
    val action: (List<String>) -> Unit
)

/**
 * Enum representing log levels.
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

/**
 * Manager for handling plugins.
 * Note: This is a placeholder for future plugin system implementation.
 */
class PluginManager {
    private val plugins = mutableListOf<Plugin>()
    private val metadataProcessors = mutableListOf<MetadataProcessorPlugin>()
    private val exportImportPlugins = mutableListOf<ExportImportPlugin>()
    private val uiExtensions = mutableListOf<UIExtensionPlugin>()

    /**
     * Registers a plugin.
     */
    fun registerPlugin(plugin: Plugin) {
        plugins.add(plugin)

        when (plugin) {
            is MetadataProcessorPlugin -> metadataProcessors.add(plugin)
            is ExportImportPlugin -> exportImportPlugins.add(plugin)
            is UIExtensionPlugin -> uiExtensions.add(plugin)
        }
    }

    /**
     * Unregisters a plugin.
     */
    fun unregisterPlugin(pluginId: String) {
        plugins.removeAll { it.id == pluginId }
        metadataProcessors.removeAll { it.id == pluginId }
        exportImportPlugins.removeAll { it.id == pluginId }
        uiExtensions.removeAll { it.id == pluginId }
    }

    /**
     * Gets all registered plugins.
     */
    fun getPlugins(): List<Plugin> = plugins.toList()

    /**
     * Gets all metadata processor plugins.
     */
    fun getMetadataProcessors(): List<MetadataProcessorPlugin> = metadataProcessors.toList()

    /**
     * Gets all export/import plugins.
     */
    fun getExportImportPlugins(): List<ExportImportPlugin> = exportImportPlugins.toList()

    /**
     * Gets all UI extension plugins.
     */
    fun getUIExtensions(): List<UIExtensionPlugin> = uiExtensions.toList()

    /**
     * Processes metadata through all registered metadata processor plugins.
     */
    fun processMetadata(filePath: String, metadata: AudioMetadata): AudioMetadata {
        var processedMetadata = metadata
        metadataProcessors.forEach { plugin ->
            processedMetadata = plugin.processMetadata(filePath, processedMetadata)
        }
        return processedMetadata
    }
}
