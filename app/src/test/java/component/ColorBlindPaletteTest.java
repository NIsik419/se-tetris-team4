package component;

import org.junit.Test;
import java.awt.Color;
import static org.junit.Assert.*;

public class ColorBlindPaletteTest {

    @Test
    public void testGetPalette_AllModes() {
        for (ColorBlindPalette.Mode mode : ColorBlindPalette.Mode.values()) {
            Color[] palette = ColorBlindPalette.getPalette(mode);
            assertNotNull(palette);
            assertEquals(7, palette.length);
        }
    }

    @Test
    public void testConvert_NormalMode_ReturnsSame() {
        Color original = ColorBlindPalette.I;
        Color converted = ColorBlindPalette.convert(original, ColorBlindPalette.Mode.NORMAL);
        assertEquals(original, converted);
    }

    @Test
    public void testConvert_Protan_ConvertCorrectIndex() {
        Color original = ColorBlindPalette.J; // index=1
        Color converted = ColorBlindPalette.convert(original, ColorBlindPalette.Mode.PROTAN);

        Color expected = ColorBlindPalette.getPalette(ColorBlindPalette.Mode.PROTAN)[1];

        assertEquals(expected, converted);
    }

    @Test
    public void testConvert_UnknownColor_ReturnsSame() {
        Color unknown = new Color(123, 45, 67);
        Color converted = ColorBlindPalette.convert(unknown, ColorBlindPalette.Mode.TRITAN);

        assertEquals(unknown, converted);
    }
}
