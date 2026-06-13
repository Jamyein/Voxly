package com.voxly.presentation.screens.metadata

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.AudioMetadata
import com.voxly.presentation.viewmodel.MetadataField

/**
 * Generates a field label with modified indicator if the field is in the modified set.
 */
@Composable
private fun fieldLabel(field: MetadataField, baseLabelResId: Int, modifiedFields: Set<MetadataField>): String {
    val baseLabel = stringResource(baseLabelResId)
    return if (field in modifiedFields) {
        baseLabel + stringResource(R.string.field_modified)
    } else {
        baseLabel
    }
}

/**
 * Metadata fields section containing all editable metadata fields.
 */
@Composable
fun MetadataFieldsSection(
    metadata: AudioMetadata,
    modifiedFields: Set<MetadataField>,
    onTitleChange: (String) -> Unit,
    onArtistChange: (String) -> Unit,
    onAlbumChange: (String) -> Unit,
    onAlbumArtistChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onGenreChange: (String) -> Unit,
    onComposerChange: (String) -> Unit,
    onLyricistChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
    onLyricsChange: (String) -> Unit,
    onTrackNumberChange: (String, String) -> Unit,
    onDiscNumberChange: (String, String) -> Unit,
    audioFile: com.voxly.domain.model.AudioFile,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionTitle(stringResource(R.string.basic_information))

        BasicMetadataFields(
            metadata = metadata,
            modifiedFields = modifiedFields,
            onTitleChange = onTitleChange,
            onArtistChange = onArtistChange,
            onAlbumChange = onAlbumChange,
            onAlbumArtistChange = onAlbumArtistChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        SectionTitle(stringResource(R.string.track_information))

        TrackInfoFields(
            metadata = metadata,
            onTrackNumberChange = onTrackNumberChange,
            onDiscNumberChange = onDiscNumberChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        SectionTitle(stringResource(R.string.additional_information))

        AdvancedMetadataFields(
            metadata = metadata,
            modifiedFields = modifiedFields,
            onYearChange = onYearChange,
            onGenreChange = onGenreChange,
            onComposerChange = onComposerChange,
            onLyricistChange = onLyricistChange,
            onCommentChange = onCommentChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        SectionTitle(stringResource(R.string.lyrics_section_title))

        LyricsField(
            metadata = metadata,
            modifiedFields = modifiedFields,
            onLyricsChange = onLyricsChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        SectionTitle(stringResource(R.string.file_information))

        FileInfoSection(audioFile = audioFile)
    }
}

@Composable
private fun BasicMetadataFields(
    metadata: AudioMetadata,
    modifiedFields: Set<MetadataField>,
    onTitleChange: (String) -> Unit,
    onArtistChange: (String) -> Unit,
    onAlbumChange: (String) -> Unit,
    onAlbumArtistChange: (String) -> Unit
) {
    var titleText       by remember(metadata.title)       { mutableStateOf(metadata.title ?: "") }
    var artistText      by remember(metadata.artist)      { mutableStateOf(metadata.artist ?: "") }
    var albumText       by remember(metadata.album)       { mutableStateOf(metadata.album ?: "") }
    var albumArtistText by remember(metadata.albumArtist) { mutableStateOf(metadata.albumArtist ?: "") }

    OutlinedTextField(
        value = titleText,
        onValueChange = {
            titleText = it
            onTitleChange(it)
        },
        label = { Text(fieldLabel(MetadataField.TITLE, R.string.metadata_title, modifiedFields)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = artistText,
        onValueChange = {
            artistText = it
            onArtistChange(it)
        },
        label = { Text(fieldLabel(MetadataField.ARTIST, R.string.metadata_artist, modifiedFields)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = albumText,
        onValueChange = {
            albumText = it
            onAlbumChange(it)
        },
        label = { Text(fieldLabel(MetadataField.ALBUM, R.string.metadata_album, modifiedFields)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = albumArtistText,
        onValueChange = {
            albumArtistText = it
            onAlbumArtistChange(it)
        },
        label = { Text(fieldLabel(MetadataField.ALBUM_ARTIST, R.string.metadata_album_artist, modifiedFields)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge
    )
}

@Composable
private fun TrackInfoFields(
    metadata: AudioMetadata,
    onTrackNumberChange: (String, String) -> Unit,
    onDiscNumberChange: (String, String) -> Unit
) {
    var trackNumberText by remember(metadata.trackNumber) { mutableStateOf(metadata.trackNumber?.toString() ?: "") }
    var discNumberText  by remember(metadata.discNumber)  { mutableStateOf(metadata.discNumber?.toString() ?: "") }

    Row(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = trackNumberText,
            onValueChange = {
                trackNumberText = it
                onTrackNumberChange(it, metadata.totalTracks?.toString() ?: "")
            },
            label = { Text(stringResource(R.string.label_track)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = MaterialTheme.shapes.extraLarge
        )

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedTextField(
            value = discNumberText,
            onValueChange = {
                discNumberText = it
                onDiscNumberChange(it, metadata.totalDiscs?.toString() ?: "")
            },
            label = { Text(stringResource(R.string.label_disc)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

@Composable
private fun AdvancedMetadataFields(
    metadata: AudioMetadata,
    modifiedFields: Set<MetadataField>,
    onYearChange: (String) -> Unit,
    onGenreChange: (String) -> Unit,
    onComposerChange: (String) -> Unit,
    onLyricistChange: (String) -> Unit,
    onCommentChange: (String) -> Unit
) {
    var yearText        by remember(metadata.year)        { mutableStateOf(metadata.year ?: "") }
    var genreText       by remember(metadata.genre)       { mutableStateOf(metadata.genre ?: "") }
    var composerText    by remember(metadata.composer)    { mutableStateOf(metadata.composer ?: "") }
    var lyricistText    by remember(metadata.lyricist)    { mutableStateOf(metadata.lyricist ?: "") }
    var commentText     by remember(metadata.comment)     { mutableStateOf(metadata.comment ?: "") }

    Row(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = yearText,
            onValueChange = {
                yearText = it
                onYearChange(it)
            },
            label = { Text(fieldLabel(MetadataField.YEAR, R.string.metadata_year, modifiedFields)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = MaterialTheme.shapes.extraLarge
        )

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedTextField(
            value = genreText,
            onValueChange = {
                genreText = it
                onGenreChange(it)
            },
            label = { Text(fieldLabel(MetadataField.GENRE, R.string.metadata_genre, modifiedFields)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = composerText,
        onValueChange = {
            composerText = it
            onComposerChange(it)
        },
        label = { Text(fieldLabel(MetadataField.COMPOSER, R.string.metadata_composer, modifiedFields)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = lyricistText,
        onValueChange = {
            lyricistText = it
            onLyricistChange(it)
        },
        label = { Text(fieldLabel(MetadataField.LYRICIST, R.string.metadata_lyricist, modifiedFields)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = commentText,
        onValueChange = {
            commentText = it
            onCommentChange(it)
        },
        label = { Text(fieldLabel(MetadataField.COMMENT, R.string.metadata_comment, modifiedFields)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        shape = MaterialTheme.shapes.extraLarge
    )
}

@Composable
private fun LyricsField(
    metadata: AudioMetadata,
    modifiedFields: Set<MetadataField>,
    onLyricsChange: (String) -> Unit
) {
    var lyricsText by remember(metadata.lyrics) { mutableStateOf(metadata.lyrics ?: "") }

    OutlinedTextField(
        value = lyricsText,
        onValueChange = {
            lyricsText = it
            onLyricsChange(it)
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp),
        label = { Text(fieldLabel(MetadataField.LYRICS, R.string.edit_lyrics, modifiedFields)) },
        placeholder = { Text(stringResource(R.string.no_lyrics_added)) },
        minLines = 6,
        shape = MaterialTheme.shapes.extraLarge
    )
}

@Composable
private fun FileInfoSection(audioFile: com.voxly.domain.model.AudioFile) {
    FileInfoRow(stringResource(R.string.file_info_format), audioFile.format.displayName)
    FileInfoRow(stringResource(R.string.metadata_bitrate), "${audioFile.bitrate} kbps")
    FileInfoRow(stringResource(R.string.metadata_sample_rate), "${audioFile.sampleRate} Hz")
    FileInfoRow(stringResource(R.string.file_info_channels), audioFile.channels.toString())
    FileInfoRow(stringResource(R.string.metadata_duration), audioFile.getFormattedDuration())
    FileInfoRow(stringResource(R.string.file_info_size), audioFile.getFormattedSize())
}