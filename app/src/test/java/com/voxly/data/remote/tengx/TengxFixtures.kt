package com.voxly.data.remote.tengx

import com.voxly.data.remote.tengx.model.*

/**
 * Test fixtures for Tengx (QQ Music) API responses.
 * Provides sample JSON payloads for testing without live API calls.
 */
object TengxFixtures {

    // ==================== Search Response Fixtures ====================

    /** Successful search response with songs */
    const val SEARCH_SUCCESS_JSON = """
    {
        "code": 0,
        "message": "",
        "data": {
            "song": {
                "totalnum": 2,
                "list": [
                    {
                        "id": 123456789,
                        "mid": "001XXp5G2v8f7c",
                        "name": "Test Song",
                        "title": "Test Song",
                        "subtitle": "",
                        "interval": 240000,
                        "version": 1,
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
                            "singer": null,
                            "publicTime": "2024-01-01",
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
                        "version": 2,
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
                            "singer": null,
                            "publicTime": "2023-06-15",
                            "pic": ""
                        }
                    }
                ]
            }
        }
    }
    """

    /** Search response with no results */
    const val SEARCH_EMPTY_JSON = """
    {
        "code": 0,
        "message": "",
        "data": {
            "song": {
                "totalnum": 0,
                "list": []
            }
        }
    }
    """

    /** Search response with API error */
    const val SEARCH_ERROR_JSON = """
    {
        "code": -1,
        "message": "Invalid parameter",
        "data": null
    }
    """

    // ==================== Lyrics Response Fixtures ====================

    /** Successful lyrics response with Base64 encoded content */
    const val LYRICS_SUCCESS_JSON = """
    {
        "code": 0,
        "lyric": {
            "lyric": "w6zCzsYAQ+egkeaIkOS7suWkt+eggeeggeS7suWkt+eggeeggeS7suWkqOS4quaMh+W6kyE=",
            "version": 3
        },
        "trans": {
            "lyric": "5LiL5Y2g5a2m55CG5ZOl5bey5biB5b+D6K+35Y2g5a2m55CG5ZOl5bey5biB5b+D6K+3",
            "version": 1
        },
        "message": ""
    }
    """

    /** Lyrics response with no lyrics available */
    const val LYRICS_EMPTY_JSON = """
    {
        "code": 0,
        "lyric": null,
        "trans": null,
        "message": ""
    }
    """

    /** Lyrics response with API error */
    const val LYRICS_ERROR_JSON = """
    {
        "code": -1,
        "lyric": null,
        "trans": null,
        "message": "Song not found"
    }
    """

    // ==================== Song Detail Response Fixtures ====================

    /** Successful song detail response */
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

    /** Successful album detail response */
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

    /** Sample TengxSong for testing */
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
        version = 1
    )

    /** Sample TengxAlbum for testing */
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

    /** Sample TengxSearchResponse for testing */
    val sampleSearchResponse = TengxSearchResponse(
        code = 0,
        data = TengxSearchData(
            song = TengxSongResult(
                list = listOf(sampleSong),
                totalnum = 1
            )
        ),
        message = ""
    )

    /** Sample TengxLyricsResponse for testing */
    val sampleLyricsResponse = TengxLyricsResponse(
        code = 0,
        lyric = TengxLyricContainer(
            lyric = "w6zCzsYAQ+egkeaIkOS7suWkt+eggeeggeS7suWkt+eggeeggeS7suWkqOS4quaMh+W6kyE=",
            version = 3
        ),
        trans = TengxLyricContainer(
            lyric = "5LiL5Y2g5a2m55CG5ZOl5bey5biB5b+D6K+35Y2g5a2m55CG5ZOl5bey5biB5b+D6K+3",
            version = 1
        ),
        message = ""
    )

    /** Sample DecodedLyricsResult for testing */
    fun sampleDecodedLyricsResult(): DecodedLyricsResult {
        return DecodedLyricsResult(
            response = sampleLyricsResponse,
            lyrics = "测试歌词内容",
            translatedLyrics = "测试翻译歌词内容"
        )
    }

    // ==================== Error Fixtures ====================

    /** Network error scenario */
    class NetworkError : Exception("Network error")

    /** API error scenario */
    class ApiError(val code: Int, message: String) : Exception("API error: $code - $message")
}
