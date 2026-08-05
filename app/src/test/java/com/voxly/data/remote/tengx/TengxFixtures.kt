package com.voxly.data.remote.tengx

import com.voxly.data.remote.tengx.model.*

/**
 * Test fixtures for Tengx (QQ Music) API responses.
 *
 * Search responses use the musicu.fcg format (req_0 wrapper).
 * Lyrics responses use the GetPlayLyricInfo format (req_0.data.lyric/trans/roma).
 */
object TengxFixtures {

    // ==================== Search Response Fixtures (musicu.fcg) ====================

    /** Successful search response with songs (musicu.fcg format). */
    const val SEARCH_SUCCESS_JSON = """
    {
        "code": 0,
        "req_0": {
            "data": {
                "body": {
                    "item_song": [
                        {
                            "id": 123456789,
                            "mid": "001XXp5G2v8f7c",
                            "name": "Test Song",
                            "title": "Test Song",
                            "subtitle": "",
                            "interval": 240000,
                            "time_public": "2024-01-01",
                            "singer": [
                                {
                                    "id": 1001,
                                    "name": "Test Artist",
                                    "title": "Test Artist",
                                    "type": 1,
                                    "gender": 1,
                                    "pic": ""
                                }
                            ],
                            "album": {
                                "id": 2001,
                                "mid": "001T8N2x3y4z5A",
                                "name": "Test Album",
                                "title": "Test Album",
                                "pic": "https://y.gtimg.cn/music/photo_new/T002R500x500M000001T8N2x3y4z5A.jpg"
                            }
                        },
                        {
                            "id": 987654321,
                            "mid": "002YYp5H3g9d8f",
                            "name": "Another Song",
                            "title": "Another Song",
                            "subtitle": "feat. Someone",
                            "interval": 195000,
                            "time_public": "2023-06-15",
                            "singer": [
                                {
                                    "id": 1002,
                                    "name": "Another Artist",
                                    "title": "Another Artist",
                                    "type": 1,
                                    "gender": 2,
                                    "pic": ""
                                }
                            ],
                            "album": {
                                "id": 2002,
                                "mid": "002U9O4i5a6b7B",
                                "name": "Another Album",
                                "title": "Another Album",
                                "pic": ""
                            }
                        }
                    ],
                    "sum": 2
                },
                "meta": {
                    "sum": 2
                }
            }
        }
    }
    """

    /** Search response with no results. */
    const val SEARCH_EMPTY_JSON = """
    {
        "code": 0,
        "req_0": {
            "data": {
                "body": {},
                "meta": { "sum": 0 }
            }
        }
    }
    """

    // ==================== Lyrics Response Fixtures (GetPlayLyricInfo) ====================

    /**
     * Lyrics fixture with base64-encoded QRC text.
     *
     * The plain QRC text is:
     *   [0,3000]hello world[4000,2000]bye
     * Base64-encoded for the trans field.
     * The qrc field uses hex that won't decrypt (fallthrough to base64).
     *
     * After decodeLyricPayload → qrcToLrc, lyrics becomes:
     *   [00:00.00]hello world
     *   [00:04.00]bye
     */
    const val LYRICS_SUCCESS_JSON = """
    {
        "code": 0,
        "req_0": {
            "data": {
                "lyric": "WzAsMzAwMF1oZWxsbyB3b3JsZApbNDAwMCwyMDAwXWJ5ZQ==",
                "trans": "WzAsMzAwMF3kvaDlpb3kuJbnlYwKWzQwMDAsMjAwMF3mi5w=",
                "roma": ""
            }
        }
    }
    """

    /** Lyrics response with no lyrics available. */
    const val LYRICS_EMPTY_JSON = """
    {
        "code": 0,
        "req_0": {
            "data": {
                "lyric": null,
                "trans": null,
                "roma": null
            }
        }
    }
    """

    // ==================== Song Detail Response Fixtures ====================

    /** Successful song detail response. */
    const val SONG_DETAIL_SUCCESS_JSON = """
    {
        "code": 0,
        "data": {
            "song": [
                {
                    "id": 123456789,
                    "mid": "001XXp5G2v8f7c",
                    "name": "Test Song",
                    "title": "Test Song",
                    "singer": [
                        {
                            "id": 1001,
                            "name": "Test Artist",
                            "title": "Test Artist"
                        }
                    ],
                    "album": {
                        "id": 2001,
                        "mid": "001T8N2x3y4z5A",
                        "name": "Test Album",
                        "title": "Test Album"
                    },
                    "interval": 240000,
                    "size": 5000000,
                    "url": "http://example.com/song.mp3"
                }
            ]
        },
        "message": ""
    }
    """

    // ==================== Album Detail Response Fixtures ====================

    /** Successful album detail response. */
    const val ALBUM_DETAIL_SUCCESS_JSON = """
    {
        "code": 0,
        "data": {
            "info": {
                "album": {
                    "id": 2001,
                    "mid": "001T8N2x3y4z5A",
                    "name": "Test Album",
                    "title": "Test Album",
                    "subtitle": "Album Subtitle",
                    "singer": {
                        "id": 1001,
                        "name": "Test Artist"
                    },
                    "publicTime": "2024-01-01",
                    "company": "Test Label",
                    "genre": "Pop"
                }
            },
            "songlist": {
                "totalnum": 10,
                "list": [
                    {
                        "id": 123456789,
                        "mid": "001XXp5G2v8f7c",
                        "name": "Track 1",
                        "interval": 240000
                    }
                ]
            }
        },
        "message": ""
    }
    """

    // ==================== Domain Model Fixtures ====================

    /** Sample TengxSong for testing. */
    val sampleSong = TengxSong(
        id = 123456789L,
        mid = "001XXp5G2v8f7c",
        name = "Test Song",
        title = "Test Song",
        subtitle = "",
        singer = listOf(
            TengxSinger(
                id = 1001L,
                name = "Test Artist",
                title = "Test Artist",
                type = 1,
                gender = 1,
                pic = ""
            )
        ),
        album = TengxAlbum(
            id = 2001L,
            mid = "001T8N2x3y4z5A",
            name = "Test Album",
            title = "Test Album",
            singer = null,
            publicTime = "2024-01-01",
            pic = "https://y.gtimg.cn/music/photo_new/T002R500x500M000001T8N2x3y4z5A.jpg"
        ),
        interval = 240000,
        version = 0
    )

    /** Sample TengxAlbum for testing. */
    val sampleAlbum = TengxAlbum(
        id = 2001L,
        mid = "001T8N2x3y4z5A",
        name = "Test Album",
        title = "Test Album",
        singer = TengxSinger(
            id = 1001L,
            name = "Test Artist",
            title = "Test Artist",
            type = 1,
            gender = 1,
            pic = ""
        ),
        publicTime = "2024-01-01",
        pic = "https://y.gtimg.cn/music/photo_new/T002R500x500M000001T8N2x3y4z5A.jpg"
    )

    /** Sample TengxSearchResponse for testing. */
    val sampleSearchResponse = TengxSearchResponse(
        code = 0,
        data = TengxSearchData(
            song = TengxSongResult(
                list = listOf(sampleSong),
                totalnum = 1
            )
        ),
        message = null
    )

    /** Sample DecodedLyricsResult for testing. */
    val sampleDecodedLyricsResult = DecodedLyricsResult(
        lyrics = "[00:00.00]hello world\n[00:04.00]bye",
        translatedLyrics = "[00:00.00]你好世界\n[00:04.00]拜"
    )
}
