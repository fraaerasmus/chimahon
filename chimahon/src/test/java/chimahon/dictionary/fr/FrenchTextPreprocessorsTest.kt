package chimahon.dictionary.fr

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrenchTextPreprocessorsTest {

    @Test
    fun `unicode decapitalize preprocessing`() {
        val preprocessed = FrenchTextPreprocessors.allVariants("École")

        assertTrue("École" in preprocessed)
        assertTrue("école" in preprocessed)
    }
}
