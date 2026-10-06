package chimahon.custom.player

/** What the fork changes about the stream URLs and headers sentence audio accepts. */
object SentenceAudioInput {
    /**
     * Auth headers used by self-hosted media servers (Jellyfin/Emby/Plex) and referer-locked CDNs.
     * Sentence audio passes these on to FFmpeg along with the headers upstream already allows.
     */
    val SELF_HOSTED_AUTH_HEADERS = setOf(
        "authorization",
        "x-emby-token",
        "x-emby-authorization",
        "x-mediabrowser-token",
        "x-plex-token",
        "x-api-key",
    )
}
