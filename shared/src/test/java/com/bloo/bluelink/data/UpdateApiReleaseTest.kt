package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which release the updater offers: finished, a build-N tag, and the highest N -- not whichever is listed first. */
class UpdateApiReleaseTest {
    private fun asset(name: String, state: String = "uploaded", size: Long = 10) =
        """{"name":"$name","browser_download_url":"https://x/$name","state":"$state","size":$size}"""

    private fun release(tag: String, draft: Boolean = false, pre: Boolean = false, assets: String = asset("Bloo.apk")) =
        """{"tag_name":"$tag","html_url":"https://x/$tag","draft":$draft,"prerelease":$pre,"assets":[$assets]}"""

    private fun list(vararg r: String) = "[${r.joinToString(",")}]"

    @Test fun `the highest build wins even when it is listed second`() {
        val run = UpdateApi.latestRunFrom(list(release("build-101"), release("build-102").let { it }, release("build-99")))
        assertEquals(102, run?.runNumber)
    }

    @Test fun `a draft, a prerelease and a non-build tag are skipped`() {
        val run = UpdateApi.latestRunFrom(list(release("build-200", draft = true), release("build-150", pre = true), release("v9"), release("build-5")))
        assertEquals(5, run?.runNumber)
    }

    @Test fun `a release still uploading is skipped for the last finished one`() {
        val unfinished = release("build-30", assets = asset("Bloo.apk", state = "starter", size = 0))
        assertEquals(29, UpdateApi.latestRunFrom(list(unfinished, release("build-29")))?.runNumber)
    }

    @Test fun `the watch apk comes along when it is finished`() {
        val both = release("build-7", assets = asset("Bloo.apk") + "," + asset("Bloo-watch.apk"))
        assertEquals("https://x/Bloo-watch.apk", UpdateApi.latestRunFrom(list(both))?.watchApkUrl)
    }

    @Test fun `nothing finished gives nothing`() {
        assertNull(UpdateApi.latestRunFrom(list(release("build-1", assets = asset("Other.zip")))))
        assertNull(UpdateApi.latestRunFrom("[]"))
    }
}
